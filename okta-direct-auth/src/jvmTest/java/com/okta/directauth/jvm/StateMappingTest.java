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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests verifying that JVM wrapper classes are usable from Java via {@code instanceof} checks,
 * without needing to reference the Kotlin model package directly.
 */
public class StateMappingTest {

  private static final String ISSUER_URL = "https://example.okta.com";
  private static final String CLIENT_ID = "test_client_id";
  private static final List<String> SCOPE = Arrays.asList("openid", "profile", "offline_access");

  private DirectAuthenticationFlow flow;

  @Before
  public void setUp() {
    flow = new DirectAuthenticationFlowBuilder(ISSUER_URL, CLIENT_ID, SCOPE).build().getOrThrow();
  }

  @After
  public void tearDown() {
    flow.close();
  }

  // --- Passive state wrappers via flow boundaries ---

  @Test
  public void getAuthenticationState_ReturnsIdleWrapper() {
    DirectAuthenticationState result = flow.getAuthenticationState();

    assertTrue("Should be Idle wrapper", result instanceof DirectAuthenticationState.Idle);
  }

  @Test
  public void reset_ReturnsIdleWrapper() {
    DirectAuthenticationState result = flow.reset();

    assertTrue("Should be Idle wrapper", result instanceof DirectAuthenticationState.Idle);
  }

  @Test
  public void authenticated_WrapperExposesToken() {
    com.okta.directauth.model.DirectAuthenticationState.Authenticated kotlinState =
        TestStateFactory.createAuthenticated();

    // Construct wrapper directly (same as toJvm would produce)
    DirectAuthenticationState.Authenticated wrapper =
        new DirectAuthenticationState.Authenticated(kotlinState);

    assertNotNull("token should be accessible", wrapper.getToken());
    assertSame(kotlinState.getToken(), wrapper.getToken());
  }
}
