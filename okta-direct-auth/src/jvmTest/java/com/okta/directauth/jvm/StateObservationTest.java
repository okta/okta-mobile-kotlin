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

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

/** Pure Java tests verifying state observation works from Java. Validates FR-012. */
public class StateObservationTest {

  private static final String ISSUER_URL = "https://example.okta.com";
  private static final String CLIENT_ID = "test_client_id";
  private static final List<String> SCOPE = Arrays.asList("openid", "profile", "offline_access");

  @Test
  public void addStateListener_ReturnsCloseable() {
    DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow();

    Consumer<DirectAuthenticationState> listener = state -> {};
    Closeable subscription = flow.addStateListener(listener);

    assertNotNull("addStateListener should return a non-null Closeable", subscription);

    flow.close();
  }

  @Test
  public void addStateListener_CloseableDoesNotThrow() throws Exception {
    try (DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow()) {
      Consumer<DirectAuthenticationState> listener = state -> {};
      Closeable subscription = flow.addStateListener(listener);

      subscription.close();
    }
  }

  @Test
  public void removeStateListener_DoesNotThrow() {
    try (DirectAuthenticationFlow flow =
        new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow()) {
      Consumer<DirectAuthenticationState> listener = state -> {};
      flow.addStateListener(listener);

      flow.removeStateListener(listener);
    }
  }
}
