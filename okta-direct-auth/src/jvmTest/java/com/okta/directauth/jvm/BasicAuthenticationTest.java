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
package com.okta.directauth.jvm;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.okta.directauth.model.PrimaryFactor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/** Pure Java tests verifying that async authentication methods are callable from Java. */
public class BasicAuthenticationTest {

  private static final String ISSUER_URL = "https://example.okta.com";
  private static final String CLIENT_ID = "test_client_id";
  private static final List<String> SCOPE = Arrays.asList("openid", "profile", "offline_access");

  @Test
  public void startAsync_ReturnsCompletableFuture() {
    DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow();

    // Verify startAsync returns a CompletableFuture (compile-time check)
    CompletableFuture<DirectAuthenticationState> future =
        flow.startAsync("user@example.com", new PrimaryFactor.Password("pass123"));

    assertNotNull("startAsync should return a non-null CompletableFuture", future);

    // Cancel to avoid actual network call
    future.cancel(true);
    flow.close();
  }

  @Test
  public void reset_ReturnsDirectAuthenticationState() {
    DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow();

    DirectAuthenticationState result = flow.reset();

    assertNotNull("reset() should return a non-null state", result);
    assertTrue(
        "reset() should return Idle state", result instanceof DirectAuthenticationState.Idle);

    flow.close();
  }

  @Test
  public void getAuthenticationState_ReturnsCurrentState() {
    DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow();

    DirectAuthenticationState result = flow.getAuthenticationState();

    assertNotNull("getAuthenticationState() should return a non-null state", result);
    assertTrue("Initial state should be Idle", result instanceof DirectAuthenticationState.Idle);

    flow.close();
  }
}
