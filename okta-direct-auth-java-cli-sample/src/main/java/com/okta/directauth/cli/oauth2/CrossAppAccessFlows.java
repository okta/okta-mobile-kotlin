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
import com.okta.oauth2.kmp.IdJagAssertion;
import com.okta.oauth2.kmp.SubjectAssertion;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over the OAuth2 module's JVM Cross App Access wrapper.
 *
 * <p>Each method returns a {@link CompletableFuture} that completes with the result on success, or
 * completes exceptionally on failure. No Kotlin coroutine or {@code kotlin.Result} types are
 * exposed to callers — this interface, and everything that implements or calls it, is a valid
 * reference for a Java-only build with no Kotlin on the classpath.
 *
 * <p>Production implementation: {@link WrapperCrossAppAccessFlows}. Test implementation: use a fake
 * that returns pre-baked futures.
 */
public interface CrossAppAccessFlows extends Closeable {

  /**
   * Exchanges {@code subject} for an ID-JAG at the IdP authorization server.
   *
   * @param subject the signed-in session's assertion to present
   * @param scope requested scopes at the target, taking precedence over any configured on the
   *     target itself. The IdP authorization server rejects a scope-less request for this grant, so
   *     this (or a target-configured default) must be non-empty.
   * @return future completing with the {@link IdJagAssertion} on success
   */
  CompletableFuture<IdJagAssertion> start(SubjectAssertion subject, List<String> scope);

  /**
   * Redeems {@code idJag} for a resource access token at the resource authorization server.
   *
   * <p>Repeatable while {@code idJag} remains unexpired, without a second identity-provider round
   * trip.
   *
   * @param idJag a previously obtained ID-JAG
   * @return future completing with the resource access {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> redeem(IdJagAssertion idJag);

  /**
   * Runs {@link #start} then {@link #redeem} and returns the resulting resource access token.
   *
   * @param subject the signed-in session's assertion to present
   * @param scope requested scopes at the target; see {@link #start}.
   * @return future completing with the resource access {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> exchange(SubjectAssertion subject, List<String> scope);

  /**
   * Checks {@code token} with the resource authorization server per <a
   * href="https://datatracker.ietf.org/doc/html/rfc7662">RFC 7662</a> — proves the token is
   * actually accepted server-side, which a successful {@link #redeem}/{@link #exchange} alone does
   * not (that only proves the resource authorization server <em>issued</em> it).
   *
   * @param token the resource access token to introspect
   * @return future completing with the {@link IntrospectInfo} on success
   */
  CompletableFuture<IntrospectInfo> introspectResourceToken(String token);

  /** Releases all underlying flow resources and cancels any background coroutine scopes. */
  @Override
  void close();
}
