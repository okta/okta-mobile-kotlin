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
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over the OAuth2 module's JVM flow wrappers.
 *
 * <p>Each method returns a {@link CompletableFuture} that completes with the result on success, or
 * completes exceptionally on failure. No Kotlin coroutine types are exposed to callers.
 *
 * <p>Production implementation: {@link WrapperOAuth2Flows}. Test implementation: use a fake that
 * returns pre-baked futures.
 */
public interface OAuth2Flows extends Closeable {

  /**
   * Starts the Resource Owner Password flow.
   *
   * @param username the user's username
   * @param password the user's password
   * @return future completing with {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> resourceOwner(String username, String password);

  /**
   * Starts the Device Authorization flow and returns the device context (user code + verification
   * URI).
   *
   * @return future completing with {@link DeviceAuthorizationFlowContext}
   */
  CompletableFuture<DeviceAuthorizationFlowContext> deviceStart();

  /**
   * Polls for device authorization completion.
   *
   * @param context the context returned by {@link #deviceStart()}
   * @return future completing with {@link TokenInfo} on approval
   */
  CompletableFuture<TokenInfo> deviceResume(DeviceAuthorizationFlowContext context);

  /**
   * Starts the Browser Sign-In (Authorization Code + PKCE) flow.
   *
   * <p>Opens the system browser and captures the loopback redirect.
   *
   * @return future completing with {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> browserSignIn();

  /**
   * Starts the Token Exchange flow.
   *
   * @param idToken an existing ID token to exchange
   * @param deviceSecret an existing device secret
   * @return future completing with {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> tokenExchange(String idToken, String deviceSecret);

  /**
   * Starts the Session Token flow.
   *
   * @param sessionToken a session token from the Okta Authn API
   * @return future completing with {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> sessionToken(String sessionToken);

  /** Releases all underlying flow resources and cancels any background coroutine scopes. */
  @Override
  void close();
}
