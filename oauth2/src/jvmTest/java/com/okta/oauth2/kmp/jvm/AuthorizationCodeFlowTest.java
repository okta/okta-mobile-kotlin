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
package com.okta.oauth2.kmp.jvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.okta.authfoundation.client.TokenInfo;
import com.okta.oauth2.kmp.BrowserRedirectHandler;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

/**
 * Java-language tests for {@link AuthorizationCodeFlow} CompletableFuture wrapper. Validates that
 * the API is ergonomic from actual Java code.
 */
public class AuthorizationCodeFlowTest {

  /** A {@link BrowserRedirectHandler} stub that immediately returns a fake redirect URI. */
  private static final BrowserRedirectHandler FAKE_REDIRECT_HANDLER =
      TestFlowFactory.createFakeBrowserRedirectHandler(
          "com.example.app:/callback?code=auth-code&state=test-state");

  @Test
  public void start_ReturnsCompletableFutureWithTokenInfo()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (AuthorizationCodeFlow flow = TestFlowFactory.createSuccessAuthorizationCodeFlow()) {
      CompletableFuture<TokenInfo> future =
          flow.start("com.example.app:/callback", FAKE_REDIRECT_HANDLER);
      assertNotNull("Future should not be null", future);

      TokenInfo tokenInfo = future.get(5, TimeUnit.SECONDS);
      assertNotNull("TokenInfo should not be null", tokenInfo);
      assertEquals("Bearer", tokenInfo.getTokenType());
      assertEquals("test-access-token", tokenInfo.getAccessToken());
    }
  }

  @Test
  public void start_WithAllParams_UsesProvidedScope()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (AuthorizationCodeFlow flow = TestFlowFactory.createSuccessAuthorizationCodeFlow()) {
      CompletableFuture<TokenInfo> future =
          flow.start(
              "com.example.app:/callback",
              FAKE_REDIRECT_HANDLER,
              Collections.emptyMap(),
              "openid profile email");
      TokenInfo tokenInfo = future.get(5, TimeUnit.SECONDS);
      assertNotNull("TokenInfo should not be null", tokenInfo);
    }
  }

  @Test
  public void start_WithDefaults_UsesDefaultScopeAndNoExtraParams()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (AuthorizationCodeFlow flow = TestFlowFactory.createSuccessAuthorizationCodeFlow()) {
      // Uses @JvmOverloads defaults — only redirectUrl and browserRedirectHandler required
      CompletableFuture<TokenInfo> future =
          flow.start("com.example.app:/callback", FAKE_REDIRECT_HANDLER);
      TokenInfo tokenInfo = future.get(5, TimeUnit.SECONDS);
      assertNotNull("TokenInfo should not be null", tokenInfo);
    }
  }

  @Test
  public void start_WithError_CompletesExceptionally()
      throws TimeoutException, InterruptedException {
    try (AuthorizationCodeFlow flow = TestFlowFactory.createFailingAuthorizationCodeFlow()) {
      CompletableFuture<TokenInfo> future =
          flow.start("com.example.app:/callback", FAKE_REDIRECT_HANDLER);
      try {
        future.get(5, TimeUnit.SECONDS);
        fail("Should have thrown ExecutionException");
      } catch (ExecutionException e) {
        assertNotNull("Cause should not be null", e.getCause());
        assertTrue(
            "Should contain error message",
            e.getCause().getMessage().contains("OIDC Endpoints not available."));
      }
    }
  }

  @Test
  public void constructor_WithOAuth2Client_CreatesFlow() {
    com.okta.authfoundation.client.kmp.OAuth2Client kmpClient = TestOAuth2Client.create();
    AuthorizationCodeFlow flow = new AuthorizationCodeFlow(kmpClient);
    assertNotNull(flow);
    flow.close();
  }

  @Test
  public void close_IsIdempotent() {
    AuthorizationCodeFlow flow = TestFlowFactory.createSuccessAuthorizationCodeFlow();
    flow.close();
    flow.close(); // Should not throw
  }
}
