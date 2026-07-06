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
import com.okta.authfoundation.client.kmp.OAuth2Client;
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;
import com.okta.oauth2.kmp.LocalhostBrowserRedirectHandler;
import com.okta.oauth2.kmp.jvm.AuthorizationCodeFlow;
import com.okta.oauth2.kmp.jvm.DeviceAuthorizationFlow;
import com.okta.oauth2.kmp.jvm.ResourceOwnerFlow;
import com.okta.oauth2.kmp.jvm.SessionTokenFlow;
import com.okta.oauth2.kmp.jvm.TokenExchangeFlow;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production implementation of {@link OAuth2Flows} backed by the OAuth2 module's JVM wrappers.
 *
 * <p>Each flow wrapper wraps Kotlin coroutines and exposes {@link CompletableFuture}-based methods
 * so this class (and all callers) remain pure Java with no coroutine API surface.
 *
 * <p>Each flow instance is closed automatically when its future completes (success or failure). The
 * Device Authorization flow is kept alive between {@link #deviceStart()} and {@link #deviceResume}
 * and closed after resumption.
 */
public final class WrapperOAuth2Flows implements OAuth2Flows {
  private final OAuth2Client client;
  private final String scope;
  private final String redirectUrl;
  private final int redirectPort;
  private final String redirectPath;

  // Maps each device-start context to the live flow that owns it so resume uses the right instance.
  // IdentityHashMap is appropriate: context equality is object identity (data class with device
  // code
  // field that is internal/not accessible from Java and may differ across instances).
  private final Map<DeviceAuthorizationFlowContext, DeviceAuthorizationFlow> activeDeviceFlows =
      Collections.synchronizedMap(new IdentityHashMap<>());

  /**
   * Creates a {@link WrapperOAuth2Flows}.
   *
   * @param client the shared OAuth2 client
   * @param scopes the OAuth2 scopes to request
   * @param signInRedirectUri the loopback redirect URI (e.g. {@code
   *     http://localhost:8080/callback})
   * @throws IllegalArgumentException if {@code signInRedirectUri} is not a valid http loopback URI
   */
  public WrapperOAuth2Flows(OAuth2Client client, List<String> scopes, String signInRedirectUri) {
    this.client = client;
    this.scope = String.join(" ", scopes);
    this.redirectUrl = signInRedirectUri;

    try {
      URI uri = new URI(signInRedirectUri);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      if (!"http".equalsIgnoreCase(scheme)) {
        throw new IllegalArgumentException(
            "signInRedirectUri must use the http scheme; got: " + signInRedirectUri);
      }
      if (host == null || !isLoopbackHost(host)) {
        throw new IllegalArgumentException(
            "signInRedirectUri must be a loopback address (localhost / 127.0.0.1 / ::1); got: "
                + signInRedirectUri);
      }
      int port = uri.getPort();
      this.redirectPort = (port == -1) ? 80 : port;
      String path = uri.getPath();
      this.redirectPath = (path == null || path.isEmpty()) ? "/" : path;
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid signInRedirectUri: " + signInRedirectUri, e);
    }
  }

  @Override
  public CompletableFuture<TokenInfo> resourceOwner(String username, String password) {
    ResourceOwnerFlow flow = new ResourceOwnerFlow(client);
    return flow.start(username, password, scope).whenComplete((r, t) -> closeQuietly(flow));
  }

  @Override
  public CompletableFuture<DeviceAuthorizationFlowContext> deviceStart() {
    DeviceAuthorizationFlow flow = new DeviceAuthorizationFlow(client);
    return flow.start(scope)
        .thenApply(
            ctx -> {
              activeDeviceFlows.put(ctx, flow);
              return ctx;
            })
        .exceptionally(
            t -> {
              closeQuietly(flow);
              sneakyThrow(t);
              return null; // unreachable
            });
  }

  @Override
  public CompletableFuture<TokenInfo> deviceResume(DeviceAuthorizationFlowContext context) {
    // Remove before resuming so a second call with the same context creates a fresh flow.
    DeviceAuthorizationFlow flow = activeDeviceFlows.remove(context);
    if (flow == null) {
      // Fallback: resume via a new wrapper (context already carries the device code internally).
      flow = new DeviceAuthorizationFlow(client);
    }
    DeviceAuthorizationFlow finalFlow = flow;
    return finalFlow.resume(context).whenComplete((r, t) -> closeQuietly(finalFlow));
  }

  @Override
  public CompletableFuture<TokenInfo> browserSignIn() {
    LocalhostBrowserRedirectHandler handler =
        new LocalhostBrowserRedirectHandler(redirectPort, redirectPath);
    AuthorizationCodeFlow flow = new AuthorizationCodeFlow(client);
    return flow.start(redirectUrl, handler, Collections.emptyMap(), scope)
        .whenComplete((r, t) -> closeQuietly(flow));
  }

  @Override
  public CompletableFuture<TokenInfo> tokenExchange(String idToken, String deviceSecret) {
    TokenExchangeFlow flow = new TokenExchangeFlow(client);
    return flow.start(idToken, deviceSecret, null, scope)
        .whenComplete((r, t) -> closeQuietly(flow));
  }

  @Override
  public CompletableFuture<TokenInfo> sessionToken(String sessionToken) {
    SessionTokenFlow flow = new SessionTokenFlow(client);
    return flow.start(sessionToken, redirectUrl, Collections.emptyMap(), scope)
        .whenComplete((r, t) -> closeQuietly(flow));
  }

  @Override
  public void close() {
    // Close any device flows that were started but never resumed (e.g. on app exit).
    activeDeviceFlows.values().forEach(WrapperOAuth2Flows::closeQuietly);
    activeDeviceFlows.clear();
  }

  private static boolean isLoopbackHost(String host) {
    // URI.getHost() strips brackets from IPv6 addresses (returns "::1" not "[::1]").
    return host.equals("localhost")
        || host.equals("127.0.0.1")
        || host.equals("::1")
        || host.equals("[::1]"); // guard against JDK implementations that retain brackets
  }

  private static void closeQuietly(AutoCloseable c) {
    try {
      c.close();
    } catch (Exception ignored) {
      // Best-effort: cancel any remaining coroutine scope.
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
    throw (T) t;
  }
}
