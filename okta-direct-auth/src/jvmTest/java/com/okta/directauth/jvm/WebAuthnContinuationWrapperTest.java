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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.okta.directauth.http.model.AuthenticatorEnrollment;
import com.okta.directauth.model.DirectAuthContinuation;
import com.okta.directauth.model.WebAuthnAssertionResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

/** Tests verifying WebAuthnContinuation wrapper API is usable from Java. */
public class WebAuthnContinuationWrapperTest {

  @Test
  public void authenticatorEnrollment_ReturnsEnrollments() {
    AuthenticatorEnrollment enrollment = new AuthenticatorEnrollment("credId", null, null);
    DirectAuthContinuation.WebAuthn delegate = TestStateFactory.createWebAuthn(List.of(enrollment));

    WebAuthnContinuation continuation = new WebAuthnContinuation(delegate);

    assertNotNull(continuation.getAuthenticatorEnrollment());
    assertEquals(1, continuation.getAuthenticatorEnrollment().size());
  }

  @Test
  public void authenticatorEnrollment_ReturnsNull_WhenNotPresent() {
    DirectAuthContinuation.WebAuthn delegate = TestStateFactory.createWebAuthn(null);

    WebAuthnContinuation continuation = new WebAuthnContinuation(delegate);

    assertNull(continuation.getAuthenticatorEnrollment());
  }

  @Test
  public void proceedAsync_WithHandler_ReturnsCompletableFuture() {
    DirectAuthContinuation.WebAuthn delegate = TestStateFactory.createWebAuthn(null);
    WebAuthnContinuation continuation = new WebAuthnContinuation(delegate);

    CompletableFuture<?> future = continuation.proceedAsync(TestHelpers.createSuccessHandler());

    assertNotNull(future);
  }

  @Test
  public void proceedAsync_WithAssertionResponse_ReturnsCompletableFuture() {
    DirectAuthContinuation.WebAuthn delegate = TestStateFactory.createWebAuthn(null);
    WebAuthnContinuation continuation = new WebAuthnContinuation(delegate);
    WebAuthnAssertionResponse response =
        new WebAuthnAssertionResponse("clientData", "authData", "sig", "user");

    CompletableFuture<?> future = continuation.proceedAsync(response);

    assertNotNull(future);
  }

  @Test
  public void webAuthnAssertionResponse_IsConstructable() {
    WebAuthnAssertionResponse response =
        new WebAuthnAssertionResponse("clientData", "authData", "sig", "user");
    assertNotNull(response);
    assertEquals("clientData", response.getClientDataJSON());
    assertEquals("authData", response.getAuthenticatorData());
    assertEquals("sig", response.getSignature());
    assertEquals("user", response.getUserHandle());
  }
}
