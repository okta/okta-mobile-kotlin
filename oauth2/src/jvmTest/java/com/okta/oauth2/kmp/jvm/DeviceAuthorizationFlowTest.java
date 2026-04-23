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
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

/**
 * Java-language tests for {@link DeviceAuthorizationFlow} CompletableFuture wrapper. Validates that
 * the API is ergonomic from actual Java code.
 */
public class DeviceAuthorizationFlowTest {

  @Test
  public void start_ReturnsCompletableFutureWithContext()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (DeviceAuthorizationFlow flow = TestFlowFactory.createSuccessDeviceAuthorizationFlow()) {
      CompletableFuture<DeviceAuthorizationFlowContext> future =
          flow.start("openid profile email offline_access");
      assertNotNull("Future should not be null", future);

      DeviceAuthorizationFlowContext ctx = future.get(5, TimeUnit.SECONDS);
      assertNotNull("Context should not be null", ctx);
      assertEquals("https://example.okta.com/activate", ctx.getVerificationUri());
      assertEquals("ABCD-1234", ctx.getUserCode());
      assertEquals(600, ctx.getExpiresIn());
    }
  }

  @Test
  public void resume_ReturnsCompletableFutureWithTokenInfo()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (DeviceAuthorizationFlow flow = TestFlowFactory.createSuccessDeviceAuthorizationFlow()) {
      DeviceAuthorizationFlowContext ctx = flow.start("openid").get(5, TimeUnit.SECONDS);
      CompletableFuture<TokenInfo> future = flow.resume(ctx);
      assertNotNull("Future should not be null", future);

      TokenInfo tokenInfo = future.get(5, TimeUnit.SECONDS);
      assertNotNull("TokenInfo should not be null", tokenInfo);
      assertEquals("Bearer", tokenInfo.getTokenType());
      assertEquals("test-access-token", tokenInfo.getAccessToken());
    }
  }

  @Test
  public void start_WithError_CompletesExceptionally()
      throws TimeoutException, InterruptedException {
    try (DeviceAuthorizationFlow flow = TestFlowFactory.createFailingDeviceAuthorizationFlow()) {
      CompletableFuture<DeviceAuthorizationFlowContext> future = flow.start("openid");
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
  public void resume_WithError_CompletesExceptionally()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (DeviceAuthorizationFlow successStart =
            TestFlowFactory.createSuccessDeviceAuthorizationFlow();
        DeviceAuthorizationFlow failingResume =
            TestFlowFactory.createFailingDeviceAuthorizationFlow()) {
      // Get a valid context from the success flow
      DeviceAuthorizationFlowContext ctx = successStart.start("openid").get(5, TimeUnit.SECONDS);

      // Resume with the failing flow
      CompletableFuture<TokenInfo> future = failingResume.resume(ctx);
      try {
        future.get(5, TimeUnit.SECONDS);
        fail("Should have thrown ExecutionException");
      } catch (ExecutionException e) {
        assertNotNull("Cause should not be null", e.getCause());
        assertTrue(
            "Should contain error message", e.getCause().getMessage().contains("access_denied"));
      }
    }
  }

  @Test
  public void constructor_WithOAuth2Client_CreatesFlow() {
    com.okta.authfoundation.client.kmp.OAuth2Client kmpClient = TestOAuth2Client.create();
    DeviceAuthorizationFlow flow = new DeviceAuthorizationFlow(kmpClient);
    assertNotNull(flow);
    flow.close();
  }

  @Test
  public void close_IsIdempotent() {
    DeviceAuthorizationFlow flow = TestFlowFactory.createSuccessDeviceAuthorizationFlow();
    flow.close();
    flow.close(); // Should not throw
  }
}
