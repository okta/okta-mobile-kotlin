/*
 * Copyright 2022-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.directauth.cli.oauth2;

import com.okta.authfoundation.client.TokenInfo;
import com.okta.authfoundation.client.dto.IntrospectInfo;
import com.okta.authfoundation.client.jvm.AuthFoundationResult;
import com.okta.authfoundation.client.kmp.OAuth2Client;
import com.okta.oauth2.kmp.CrossAppAccessTarget;
import com.okta.oauth2.kmp.IdJagAssertion;
import com.okta.oauth2.kmp.SubjectAssertion;
import com.okta.oauth2.kmp.jvm.CrossAppAccessFlow;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production implementation of {@link CrossAppAccessFlows} backed by the OAuth2 module's JVM Cross
 * App Access wrapper.
 *
 * <p>Unlike {@link WrapperOAuth2Flows}'s single-shot flows (which close as soon as their future
 * completes), the flow built for {@link #start} is deliberately <em>retained</em> until a fresh
 * {@link #start} call or {@link #close} — mirroring {@link WrapperOAuth2Flows}'s own {@code
 * activeDeviceFlows} precedent for its two-step Device Authorization flow. Closing after {@link
 * #start} would make {@link #redeem} — and therefore repeat redemption — impossible, since the
 * whole point of the ID-JAG is that it can be redeemed again without revisiting the identity
 * provider. {@link #exchange} closes its flow once it completes (nothing left to redeem again), but
 * — unlike {@link WrapperOAuth2Flows}'s single-shot flows — still keeps the reference, so {@link
 * #introspectResourceToken} remains callable afterward; this is safe because {@code close} only
 * cancels the flow's own future-bridging coroutine scope, never the target client that {@link
 * #introspectResourceToken} runs against on its own separate scope.
 */
public final class WrapperCrossAppAccessFlows implements CrossAppAccessFlows {
  private final OAuth2Client idpClient;
  private final CrossAppAccessTarget target;

  // Retained between start() and redeem() so redemption can repeat with no second
  // identity-provider round trip. The wrapped flow owns two coroutine scopes (see
  // CrossAppAccessFlow.introspectionScope); every path that replaces or clears this field tears
  // down the previous value's scopes first via closeAndClearActiveFlow().
  private final AtomicReference<CrossAppAccessFlow> activeFlow = new AtomicReference<>();

  /**
   * Creates a {@link WrapperCrossAppAccessFlows}.
   *
   * @param idpClient Cross App Access's own, dedicated requesting-app client, already configured
   *     against the IdP authorization server, or {@code null} if {@code xaaIdpIssuer}/{@code
   *     xaaIdpClientId} are not configured — every method then completes exceptionally instead of
   *     throwing, describing the missing configuration
   * @param target the resource authorization server to redeem ID-JAGs at
   */
  public WrapperCrossAppAccessFlows(OAuth2Client idpClient, CrossAppAccessTarget target) {
    this.idpClient = idpClient;
    this.target = target;
  }

  @Override
  public CompletableFuture<IdJagAssertion> start(SubjectAssertion subject, List<String> scope) {
    AuthFoundationResult<CrossAppAccessFlow> result = createAndRetainFlow();
    if (result.isFailure()) {
      return failedFuture(result.exceptionOrNull());
    }
    return result.getOrThrow().start(subject, normalizeScope(scope));
  }

  @Override
  public CompletableFuture<TokenInfo> redeem(IdJagAssertion idJag) {
    CrossAppAccessFlow flow = activeFlow.get();
    if (flow == null) {
      return failedFuture(new IllegalStateException("redeem() called before start() succeeded."));
    }
    return flow.redeem(idJag);
  }

  @Override
  public CompletableFuture<TokenInfo> exchange(SubjectAssertion subject, List<String> scope) {
    AuthFoundationResult<CrossAppAccessFlow> result = createAndRetainFlow();
    if (result.isFailure()) {
      return failedFuture(result.exceptionOrNull());
    }
    CrossAppAccessFlow flow = result.getOrThrow();
    // Single-shot: nothing to redeem again after a one-action exchange, so close as soon as it
    // completes — same lifetime as WrapperOAuth2Flows's other single-shot flows. The reference
    // itself is kept (not nulled out) so introspectResourceToken() can still find it afterward —
    // safe, since close() only cancels this flow's own future-bridging coroutine scope, never the
    // underlying target client introspectResourceToken() runs on its own separate scope.
    return flow.exchange(subject, normalizeScope(scope))
        .whenComplete((tokenInfo, throwable) -> closeQuietly(flow));
  }

  @Override
  public CompletableFuture<IntrospectInfo> introspectResourceToken(String token) {
    CrossAppAccessFlow flow = activeFlow.get();
    if (flow == null) {
      return failedFuture(
          new IllegalStateException(
              "introspectResourceToken() called before start()/exchange() succeeded."));
    }
    return flow.introspectResourceToken(token);
  }

  @Override
  public void close() {
    closeAndClearActiveFlow();
  }

  private AuthFoundationResult<CrossAppAccessFlow> createAndRetainFlow() {
    closeAndClearActiveFlow();
    if (idpClient == null) {
      return AuthFoundationResult.failure(
          new IllegalStateException(
              "Cross App Access IdP client is not configured (xaaIdpIssuer/xaaIdpClientId)."));
    }
    AuthFoundationResult<CrossAppAccessFlow> result = CrossAppAccessFlow.create(idpClient, target);
    if (result.isSuccess()) {
      activeFlow.set(result.getOrThrow());
    }
    return result;
  }

  private void closeAndClearActiveFlow() {
    CrossAppAccessFlow flow = activeFlow.getAndSet(null);
    if (flow != null) {
      // Fully torn down: nothing will call introspectResourceToken() on a flow that has been
      // replaced or discarded — unlike exchange()'s own closeQuietly() call above, which
      // deliberately keeps introspection alive on the flow it just completed.
      closeCompletelyQuietly(flow);
    }
  }

  // The underlying JVM CrossAppAccessFlow.start/exchange document null as "use the target's
  // configured default", so an omitted (null) scope must reach them as null, not NPE here. An
  // empty list is normalized the same way since it carries no scope either.
  static List<String> normalizeScope(List<String> scope) {
    return (scope == null || scope.isEmpty()) ? null : scope;
  }

  private static <T> CompletableFuture<T> failedFuture(Throwable error) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(error);
    return future;
  }

  private static void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Best-effort: cancel any remaining coroutine scope.
    }
  }

  private static void closeCompletelyQuietly(CrossAppAccessFlow flow) {
    try {
      flow.closeCompletely();
    } catch (Exception ignored) {
      // Best-effort: cancel any remaining coroutine scopes.
    }
  }
}
