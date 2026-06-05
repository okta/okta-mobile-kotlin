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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.okta.oauth2.kmp.BrowserRedirectHandler;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kotlin.Unit;
import org.junit.Test;

/**
 * Java-language tests for {@link RedirectEndSessionFlow} CompletableFuture wrapper. Validates that
 * the API is ergonomic from actual Java code.
 */
public class RedirectEndSessionFlowTest {

  @Test
  public void start_ReturnsCompletableFutureOnSuccess()
      throws ExecutionException, InterruptedException, TimeoutException {
    BrowserRedirectHandler handler =
        TestFlowFactory.createFakeBrowserRedirectHandler(
            "com.example.app:/logout?state=test-state");
    RedirectEndSessionFlow flow =
        TestFlowFactory.createSuccessRedirectEndSessionFlow("com.example.app:/logout");
    try {
      CompletableFuture<Unit> future =
          flow.start("example-id-token", "com.example.app:/logout", handler);
      assertNotNull("Future should not be null", future);
      future.get(5, TimeUnit.SECONDS);
    } finally {
      flow.close();
    }
  }

  @Test
  public void start_WithAllParams_ReturnsUnit()
      throws ExecutionException, InterruptedException, TimeoutException {
    BrowserRedirectHandler handler =
        TestFlowFactory.createFakeBrowserRedirectHandler(
            "com.example.app:/logout?state=test-state");
    RedirectEndSessionFlow flow =
        TestFlowFactory.createSuccessRedirectEndSessionFlow("com.example.app:/logout");
    try {
      CompletableFuture<Unit> future =
          flow.start(
              "example-id-token", "com.example.app:/logout", handler, Collections.emptyMap());
      future.get(5, TimeUnit.SECONDS);
    } finally {
      flow.close();
    }
  }

  @Test
  public void start_WithError_CompletesExceptionally()
      throws TimeoutException, InterruptedException {
    BrowserRedirectHandler handler =
        TestFlowFactory.createFakeBrowserRedirectHandler("com.example.app:/logout");
    RedirectEndSessionFlow flow = TestFlowFactory.createFailingRedirectEndSessionFlow();
    try {
      CompletableFuture<Unit> future =
          flow.start("example-id-token", "com.example.app:/logout", handler);
      try {
        future.get(5, TimeUnit.SECONDS);
        fail("Should have thrown ExecutionException");
      } catch (ExecutionException e) {
        assertNotNull("Cause should not be null", e.getCause());
        assertTrue(
            "Should contain error message",
            e.getCause().getMessage().contains("OIDC Endpoints not available."));
      }
    } finally {
      flow.close();
    }
  }

  @Test
  public void close_IsIdempotent() {
    RedirectEndSessionFlow flow =
        TestFlowFactory.createSuccessRedirectEndSessionFlow("com.example.app:/logout");
    flow.close();
    flow.close(); // Should not throw
  }
}
