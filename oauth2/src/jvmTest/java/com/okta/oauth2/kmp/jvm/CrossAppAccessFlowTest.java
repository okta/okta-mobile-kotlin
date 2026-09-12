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
import com.okta.authfoundation.client.jvm.AuthFoundationResult;
import com.okta.authfoundation.client.kmp.OAuth2Client;
import com.okta.authfoundation.credential.kmp.Credential;
import com.okta.oauth2.kmp.CrossAppAccessTarget;
import com.okta.oauth2.kmp.IdJagAssertion;
import com.okta.oauth2.kmp.SubjectAssertion;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

/**
 * Java-language tests for {@link CrossAppAccessFlow} and its supporting types. Validates that the
 * API is ergonomic — and genuinely constructible without a nine-argument constructor — from actual
 * Java code.
 */
public class CrossAppAccessFlowTest {

  private static OAuth2Client idpClient() {
    return TestOAuth2Client.create();
  }

  @Test
  public void create_WithTargetNamedByIssuer_AndNoCredential_ReturnsFailedResult() {
    CrossAppAccessTarget target =
        CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com").build();

    AuthFoundationResult<CrossAppAccessFlow> result =
        CrossAppAccessFlow.create(idpClient(), target);

    assertTrue(result.isFailure());
  }

  @Test
  public void create_WithTargetNamedByAuthorizationServerId_AndNoCredential_ReturnsFailedResult() {
    CrossAppAccessTarget target =
        CrossAppAccessTargetBuilder.forAuthorizationServerId("default").build();

    AuthFoundationResult<CrossAppAccessFlow> result =
        CrossAppAccessFlow.create(idpClient(), target);

    assertTrue(result.isFailure());
  }

  @Test
  public void create_WithWrappedPublicClient_ReturnsFailedResult() {
    OAuth2Client publicTargetClient = TestOAuth2Client.create();
    CrossAppAccessTarget target = CrossAppAccessTarget.wrapping(publicTargetClient, null, null);

    AuthFoundationResult<CrossAppAccessFlow> result =
        CrossAppAccessFlow.create(idpClient(), target);

    assertTrue(result.isFailure());
  }

  @Test
  public void builder_ForIssuer_ChainsEverySettingAndBuildsATarget() {
    CrossAppAccessTarget target =
        CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
            .setClientId("target-client-id")
            .setScope(List.of("chat.read"))
            .setClientSecret("target-secret")
            .setResource("https://resource.example.com/api")
            .setClientBuildAction(
                builder -> {
                  builder.setClientSecret("target-secret-overridden");
                })
            .build();

    assertNotNull(target);
  }

  @Test
  public void create_WithBuilderTarget_Succeeds() {
    CrossAppAccessTarget target =
        CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
            .setClientSecret("target-secret")
            .build();

    AuthFoundationResult<CrossAppAccessFlow> result =
        CrossAppAccessFlow.create(idpClient(), target);

    assertTrue(result.isSuccess());
    result.getOrThrow().close();
  }

  @Test
  public void start_ReturnsCompletableFutureWithIdJagAssertion()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow()) {
      CompletableFuture<IdJagAssertion> future =
          flow.start(SubjectAssertion.idToken("id-token"), List.of("chat.read"));
      assertNotNull("Future should not be null", future);

      IdJagAssertion idJag = future.get(5, TimeUnit.SECONDS);
      assertNotNull("IdJagAssertion should not be null", idJag);
    }
  }

  @Test
  public void start_WithJvmOverloadsShortForm_OmitsScope()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow()) {
      IdJagAssertion idJag =
          flow.start(SubjectAssertion.idToken("id-token")).get(5, TimeUnit.SECONDS);
      assertNotNull(idJag);
    }
  }

  @Test
  public void redeem_CalledTwice_BothCallsSucceed()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow()) {
      IdJagAssertion idJag =
          flow.start(SubjectAssertion.idToken("id-token")).get(5, TimeUnit.SECONDS);

      TokenInfo first = flow.redeem(idJag).get(5, TimeUnit.SECONDS);
      TokenInfo second = flow.redeem(idJag).get(5, TimeUnit.SECONDS);

      assertNotNull(first);
      assertNotNull(second);
    }
  }

  @Test
  public void exchange_ReturnsCompletableFutureWithTokenInfo()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow()) {
      TokenInfo tokenInfo =
          flow.exchange(SubjectAssertion.idToken("id-token"), null).get(5, TimeUnit.SECONDS);
      assertNotNull(tokenInfo);
      assertEquals("Bearer", tokenInfo.getTokenType());
    }
  }

  @Test
  public void exchange_WithJvmOverloadsShortForm_OmitsScope()
      throws ExecutionException, InterruptedException, TimeoutException {
    try (CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow()) {
      TokenInfo tokenInfo =
          flow.exchange(SubjectAssertion.idToken("id-token")).get(5, TimeUnit.SECONDS);
      assertNotNull(tokenInfo);
    }
  }

  @Test
  public void exchange_WithError_CompletesExceptionally()
      throws TimeoutException, InterruptedException {
    try (CrossAppAccessFlow flow = TestFlowFactory.createFailingCrossAppAccessFlow()) {
      CompletableFuture<TokenInfo> future =
          flow.exchange(SubjectAssertion.idToken("id-token"), null);
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
  public void getTargetClient_ReturnsConfiguredClient() {
    try (CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow()) {
      assertNotNull(flow.getTargetClient());
      assertNotNull(flow.getIdpClient());
    }
  }

  @Test
  public void subjectAssertionFactories_AreCallableWithoutACompanionQualifier() {
    assertNotNull(SubjectAssertion.idToken("id-token"));
    assertNotNull(SubjectAssertion.accessToken("access-token"));
    assertNotNull(SubjectAssertion.refreshToken("refresh-token"));
  }

  @Test
  public void crossAppAccessSubject_StaticHelper_ReturnsSubjectAssertion()
      throws ExecutionException, InterruptedException, TimeoutException {
    Credential credential = FakeSuspendFlows.fakeCredentialWithIdToken("the-id-token");

    SubjectAssertion subject =
        CrossAppAccessFlow.crossAppAccessSubject(credential).get(5, TimeUnit.SECONDS);

    assertNotNull(subject);
  }

  @Test
  public void crossAppAccessSubject_StaticHelper_WithAccessTokenType_ReturnsSubjectAssertion()
      throws ExecutionException, InterruptedException, TimeoutException {
    // Credential has no ID token, so this only succeeds if the explicit type argument actually
    // reaches the underlying call rather than the default ID_TOKEN type being used instead.
    Credential credential = FakeSuspendFlows.fakeCredentialWithNoIdToken();

    SubjectAssertion subject =
        CrossAppAccessFlow.crossAppAccessSubject(credential, SubjectAssertion.Type.ACCESS_TOKEN)
            .get(5, TimeUnit.SECONDS);

    assertNotNull(subject);
  }

  @Test
  public void crossAppAccessToken_StaticHelper_WhenCredentialLacksIdToken_CompletesExceptionally()
      throws InterruptedException, TimeoutException {
    // No stub executor is reachable from Java (ApiExecutor has a suspend member), so this
    // exercises the pre-network failure path deterministically rather than performing real I/O.
    Credential credential = FakeSuspendFlows.fakeCredentialWithNoIdToken();
    CrossAppAccessTarget target =
        CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
            .setClientSecret("target-secret")
            .build();

    CompletableFuture<TokenInfo> future =
        CrossAppAccessFlow.crossAppAccessToken(credential, idpClient(), target);
    try {
      future.get(5, TimeUnit.SECONDS);
      fail("Should have thrown ExecutionException");
    } catch (ExecutionException e) {
      assertNotNull(e.getCause());
    }
  }

  @Test
  public void crossAppAccessToken_StaticHelper_WithRefreshTokenSubjectType_DerivesFromRefreshToken()
      throws InterruptedException, TimeoutException {
    // idpClient must match the credential's issuer/clientId, so the failure below comes from
    // deriving the refresh-token subject assertion, not from the pre-subject client-match check.
    OAuth2Client matchingIdpClient = TestOAuth2Client.create("fake-client-id");
    Credential credential = FakeSuspendFlows.fakeCredentialWithNoIdToken();
    CrossAppAccessTarget target =
        CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
            .setClientSecret("target-secret")
            .build();

    CompletableFuture<TokenInfo> future =
        CrossAppAccessFlow.crossAppAccessToken(
            credential, matchingIdpClient, target, SubjectAssertion.Type.REFRESH_TOKEN, null);

    try {
      future.get(5, TimeUnit.SECONDS);
      fail("Should have thrown ExecutionException");
    } catch (ExecutionException e) {
      assertNotNull(e.getCause());
      assertTrue(
          "Should fail deriving the refresh token subject, proving subjectType reached the"
              + " underlying call",
          e.getCause().getMessage().contains("refresh token"));
    }
  }

  @Test
  public void close_IsIdempotent() {
    CrossAppAccessFlow flow = TestFlowFactory.createSuccessCrossAppAccessFlow();
    flow.close();
    flow.close(); // Should not throw
  }
}
