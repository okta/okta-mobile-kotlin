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
import java.util.concurrent.CompletableFuture;

/**
 * Test double for {@link CrossAppAccessIdpFlow}.
 *
 * <p>Returns a pre-configured future so tests can exercise the dedicated sign-in's success and
 * failure paths without a network or a real browser.
 */
public final class FakeCrossAppAccessIdpFlow implements CrossAppAccessIdpFlow {
  private CompletableFuture<TokenInfo> signInResult = new CompletableFuture<>();
  private boolean closed = false;

  /** Configures {@link #browserSignIn} to succeed with the given token. */
  public void signInSucceedWith(TokenInfo tokenInfo) {
    signInResult = CompletableFuture.completedFuture(tokenInfo);
  }

  /** Configures {@link #browserSignIn} to fail with the given exception. */
  public void signInFailWith(Throwable error) {
    signInResult = CompletableFuture.failedFuture(error);
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public CompletableFuture<TokenInfo> browserSignIn() {
    return signInResult;
  }

  @Override
  public void close() {
    closed = true;
  }
}
