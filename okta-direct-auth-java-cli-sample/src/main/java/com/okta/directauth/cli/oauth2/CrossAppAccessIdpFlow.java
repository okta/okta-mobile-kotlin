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
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over Cross App Access's own dedicated Browser Sign-In — deliberately separate from
 * {@link OAuth2Flows}, since Cross App Access needs only this one flow against its own IdP client,
 * never the other four this sample's primary app demonstrates.
 *
 * <p>Production implementation: {@link WrapperCrossAppAccessIdpFlow}. Test implementation: use a
 * fake that returns pre-baked futures.
 */
public interface CrossAppAccessIdpFlow extends Closeable {

  /**
   * Performs the dedicated Cross App Access sign-in (Authorization Code + PKCE).
   *
   * @return future completing with the signed-in session's {@link TokenInfo} on success
   */
  CompletableFuture<TokenInfo> browserSignIn();

  /** Releases all underlying flow resources and cancels any background coroutine scopes. */
  @Override
  void close();
}
