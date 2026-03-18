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

import com.okta.authfoundation.ChallengeGrantType;
import com.okta.authfoundation.GrantType;
import com.okta.authfoundation.api.log.JvmAuthFoundationLogger;
import com.okta.authfoundation.client.TokenInfo;
import com.okta.directauth.http.model.AuthenticatorEnrollment;
import com.okta.directauth.http.model.PublicKeyCredentialDescriptor;
import com.okta.directauth.http.model.PublicKeyCredentialRequestOptions;
import com.okta.directauth.http.model.PublicKeyCredentialType;
import com.okta.directauth.http.model.UserVerificationRequirement;
import com.okta.directauth.http.model.WebAuthnChallengeResponse;
import com.okta.directauth.model.BindingContext;
import com.okta.directauth.model.BindingMethod;
import com.okta.directauth.model.DirectAuthContinuation;
import com.okta.directauth.model.DirectAuthenticationContext;
import com.okta.directauth.model.DirectAuthenticationIntent;
import com.okta.directauth.model.DirectAuthenticationState;
import com.okta.directauth.model.MfaContext;
import com.okta.directauth.model.OobChannel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Test factory for creating real instances of Kotlin {@code internal} types from Java.
 *
 * <p>Kotlin {@code internal} classes compile to public JVM bytecode, so Java in another module can
 * construct them directly. This avoids the need for Mockito mocks in tests.
 */
final class TestStateFactory {

  private TestStateFactory() {}

  /**
   * Creates a {@link DirectAuthenticationContext} with test defaults.
   *
   * @return a test context backed by a mock HTTP executor
   */
  static DirectAuthenticationContext createContext() {
    return new DirectAuthenticationContext(
        /* issuerUrl= */ "https://example.okta.com",
        /* clientId= */ "test_client_id",
        /* scope= */ List.of("openid", "email", "profile", "offline_access"),
        /* authorizationServerId= */ "",
        /* clientSecret= */ "test_client_secret",
        /* grantTypes= */ List.of(GrantType.Password.INSTANCE, GrantType.WebAuthn.INSTANCE),
        /* acrValues= */ Collections.emptyList(),
        /* directAuthenticationIntent= */ DirectAuthenticationIntent.SIGN_IN,
        /* apiExecutor= */ TestHelpers.createMockApiExecutor(),
        /* logger= */ new JvmAuthFoundationLogger(),
        /* clock= */ () -> 1654041600L,
        /* additionalParameters= */ Map.of());
  }

  /**
   * Creates a real {@link DirectAuthenticationState.Authenticated} instance with a test token.
   *
   * @return a real Authenticated state backed by a stub {@link TokenInfo}
   */
  static DirectAuthenticationState.Authenticated createAuthenticated() {
    TokenInfo token =
        new TokenInfo() {
          @Override
          public @NotNull String getClientId() {
            return "test_client_id";
          }

          @Override
          public @NotNull String getIssuerUrl() {
            return "https://example.okta.com";
          }

          @Override
          public @NotNull String getTokenType() {
            return "Bearer";
          }

          @Override
          public int getExpiresIn() {
            return 3600;
          }

          @Override
          public @NotNull String getAccessToken() {
            return "test_access_token";
          }

          @Override
          public String getScope() {
            return "openid profile";
          }

          @Override
          public String getRefreshToken() {
            return null;
          }

          @Override
          public String getIdToken() {
            return null;
          }

          @Override
          public String getDeviceSecret() {
            return null;
          }

          @Override
          public String getIssuedTokenType() {
            return null;
          }
        };
    return new DirectAuthenticationState.Authenticated(token);
  }

  /**
   * Creates a real {@link DirectAuthContinuation.WebAuthn} instance with the given enrollments.
   *
   * @param enrollments the authenticator enrollments to include, or {@code null} for none
   * @return a real WebAuthn continuation backed by test defaults
   */
  static DirectAuthContinuation.WebAuthn createWebAuthn(List<AuthenticatorEnrollment> enrollments) {
    PublicKeyCredentialRequestOptions publicKey =
        new PublicKeyCredentialRequestOptions(
            /* challenge= */ "dGVzdC1jaGFsbGVuZ2U",
            /* rpId= */ "example.okta.com",
            /* allowCredentials= */ List.of(
                new PublicKeyCredentialDescriptor(
                    "Y3JlZC0x", PublicKeyCredentialType.PUBLIC_KEY, null)),
            /* timeout= */ 60000L,
            /* userVerification= */ UserVerificationRequirement.PREFERRED,
            /* hints= */ null,
            /* extensions= */ null);

    WebAuthnChallengeResponse challengeResponse =
        new WebAuthnChallengeResponse(null, publicKey, enrollments);

    return new DirectAuthContinuation.WebAuthn(challengeResponse, createContext(), null);
  }

  /**
   * Creates a real {@link DirectAuthenticationState.MfaRequired} instance.
   *
   * @return a real MfaRequired state backed by test defaults
   */
  static DirectAuthenticationState.MfaRequired createMfaRequired() {
    MfaContext mfaContext =
        new MfaContext(
            List.of(ChallengeGrantType.WebAuthnMfa.INSTANCE), /* mfaToken= */ "test_mfa_token");
    return new DirectAuthenticationState.MfaRequired(createContext(), mfaContext);
  }

  /**
   * Creates a real {@link DirectAuthContinuation.Prompt} instance.
   *
   * @param expiresIn the expiration time in seconds
   * @return a real Prompt continuation backed by test defaults
   */
  static DirectAuthContinuation.Prompt createPrompt(int expiresIn) {
    BindingContext bindingContext =
        new BindingContext(
            /* oobCode= */ "test_oob_code",
            /* expiresIn= */ expiresIn,
            /* interval= */ 5,
            /* channel= */ OobChannel.SMS,
            /* bindingMethod= */ BindingMethod.PROMPT,
            /* bindingCode= */ null,
            /* challengeType= */ null);
    return new DirectAuthContinuation.Prompt(bindingContext, createContext(), null);
  }

  /** Creates a real {@link DirectAuthContinuation.Prompt} with default expiration (120s). */
  static DirectAuthContinuation.Prompt createPrompt() {
    return createPrompt(120);
  }

  /**
   * Creates a real {@link DirectAuthContinuation.Transfer} instance.
   *
   * @param expiresIn the expiration time in seconds
   * @param bindingCode the binding code to display, or {@code null}
   * @return a real Transfer continuation backed by test defaults
   */
  static DirectAuthContinuation.Transfer createTransfer(int expiresIn, String bindingCode) {
    BindingContext bindingContext =
        new BindingContext(
            /* oobCode= */ "test_oob_code",
            /* expiresIn= */ expiresIn,
            /* interval= */ 5,
            /* channel= */ OobChannel.PUSH,
            /* bindingMethod= */ BindingMethod.TRANSFER,
            /* bindingCode= */ bindingCode,
            /* challengeType= */ null);
    return new DirectAuthContinuation.Transfer(bindingContext, createContext(), null);
  }

  /** Creates a real {@link DirectAuthContinuation.Transfer} with default values. */
  static DirectAuthContinuation.Transfer createTransfer() {
    return createTransfer(120, "95");
  }

  /**
   * Creates a real {@link DirectAuthContinuation.OobPending} instance.
   *
   * @param expiresIn the expiration time in seconds
   * @return a real OobPending continuation backed by test defaults
   */
  static DirectAuthContinuation.OobPending createOobPending(int expiresIn) {
    BindingContext bindingContext =
        new BindingContext(
            /* oobCode= */ "test_oob_code",
            /* expiresIn= */ expiresIn,
            /* interval= */ 5,
            /* channel= */ OobChannel.PUSH,
            /* bindingMethod= */ BindingMethod.NONE,
            /* bindingCode= */ null,
            /* challengeType= */ null);
    return new DirectAuthContinuation.OobPending(bindingContext, createContext(), null);
  }

  /** Creates a real {@link DirectAuthContinuation.OobPending} with default expiration (120s). */
  static DirectAuthContinuation.OobPending createOobPending() {
    return createOobPending(120);
  }
}
