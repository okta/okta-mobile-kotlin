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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

/**
 * Java-language tests for {@link TokenExchangeFlow} CompletableFuture wrapper. Validates that the
 * API is ergonomic from actual Java code.
 */
public class TokenExchangeFlowTest {

  @Test
  public void start_ReturnsCompletableFutureWithTokenInfo()
      throws ExecutionException, InterruptedException, TimeoutException {
    TokenExchangeFlow flow = TestFlowFactory.createSuccessTokenExchangeFlow();
    try {
      CompletableFuture<TokenInfo> future =
          flow.start("id-token", "device-secret", null, "openid profile email offline_access");
      assertNotNull("Future should not be null", future);

      TokenInfo tokenInfo = future.get(5, TimeUnit.SECONDS);
      assertNotNull("TokenInfo should not be null", tokenInfo);
      assertEquals("Bearer", tokenInfo.getTokenType());
      assertEquals("test-access-token", tokenInfo.getAccessToken());
    } finally {
      flow.close();
    }
  }

  @Test
  public void start_WithDefaults_UsesDefaultScope()
      throws ExecutionException, InterruptedException, TimeoutException {
    TokenExchangeFlow flow = TestFlowFactory.createSuccessTokenExchangeFlow();
    try {
      // Uses @JvmOverloads defaults — only required params
      CompletableFuture<TokenInfo> future = flow.start("id-token", "device-secret");
      TokenInfo tokenInfo = future.get(5, TimeUnit.SECONDS);
      assertNotNull("TokenInfo should not be null", tokenInfo);
    } finally {
      flow.close();
    }
  }

  @Test
  public void start_WithError_CompletesExceptionally()
      throws TimeoutException, InterruptedException {
    TokenExchangeFlow flow = TestFlowFactory.createFailingTokenExchangeFlow();
    try {
      CompletableFuture<TokenInfo> future = flow.start("id-token", "device-secret");
      try {
        future.get(5, TimeUnit.SECONDS);
        fail("Should have thrown ExecutionException");
      } catch (ExecutionException e) {
        assertNotNull("Cause should not be null", e.getCause());
        assertTrue(
            "Should contain error message", e.getCause().getMessage().contains("invalid_grant"));
      }
    } finally {
      flow.close();
    }
  }

  @Test
  public void close_IsIdempotent() {
    TokenExchangeFlow flow = TestFlowFactory.createSuccessTokenExchangeFlow();
    flow.close();
    flow.close(); // Should not throw
  }
}
