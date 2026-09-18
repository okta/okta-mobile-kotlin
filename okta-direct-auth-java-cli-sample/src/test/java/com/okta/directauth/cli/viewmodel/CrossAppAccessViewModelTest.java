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
package com.okta.directauth.cli.viewmodel;

import static com.google.common.truth.Truth.assertThat;

import com.okta.authfoundation.client.TokenInfo;
import com.okta.authfoundation.client.dto.IntrospectInfo;
import com.okta.directauth.cli.model.CrossAppAccessConfig;
import com.okta.directauth.cli.model.IdJagDisplay;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.SubjectKind;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.oauth2.FakeCrossAppAccessFlows;
import com.okta.directauth.cli.oauth2.FakeCrossAppAccessIdpFlow;
import com.okta.oauth2.kmp.CrossAppAccessException;
import com.okta.oauth2.kmp.IdJagAssertion;
import com.okta.oauth2.kmp.SubjectAssertion;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies {@link CrossAppAccessViewModel} covering both modes, the reusable-ID-JAG renewal path,
 * and the dedicated sign-in, mirroring {@link OAuth2ViewModelTest}'s shape so the two are easy to
 * compare side by side.
 */
public class CrossAppAccessViewModelTest {
  private FakeCrossAppAccessFlows fakeFlows;
  private FakeCrossAppAccessIdpFlow fakeIdpFlow;
  private CrossAppAccessConfig config;
  private TokenInfo session;
  private CrossAppAccessViewModel viewModel;
  private CapturingListener listener;

  @Before
  public void setUp() {
    fakeFlows = new FakeCrossAppAccessFlows();
    fakeIdpFlow = new FakeCrossAppAccessIdpFlow();
    config =
        new CrossAppAccessConfig.Builder()
            .idpIssuer("https://idp.example.com")
            .idpClientId("idp-client-id")
            .authorizationServerId("ausOther")
            .clientId("target-client-id")
            .build();
    session = makeToken("access-value", "id-value", "refresh-value");
    viewModel = new CrossAppAccessViewModel(fakeFlows, fakeIdpFlow, config, true, () -> 0L);
    viewModel.selectScope(Collections.singletonList("chat.read"));
    listener = new CapturingListener();
    viewModel.addListener(listener);
  }

  @After
  public void tearDown() {
    viewModel.close();
  }

  /** Signs in with {@link #session} and waits for it to settle — the common test setup step. */
  private void signInWithSession() throws InterruptedException {
    fakeIdpFlow.signInSucceedWith(session);
    viewModel.signIn();
    listener.awaitTerminal();
  }

  // ── Dedicated sign-in ────────────────────────────────────────────────────────────

  @Test
  public void signIn_Success_MakesSessionAvailable() throws Exception {
    signInWithSession();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_SIGNED_IN);
    assertThat(viewModel.hasSession()).isTrue();
    assertThat(viewModel.isKindAvailable(SubjectKind.IDENTITY)).isTrue();
  }

  @Test
  public void signIn_Failure_AttributesToIdentityProvider() throws Exception {
    fakeIdpFlow.signInFailWith(new IllegalStateException("browser closed"));

    viewModel.signIn();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(listener.lastError).contains("identity provider");
    assertThat(listener.lastError).contains("browser closed");
    assertThat(viewModel.hasSession()).isFalse();
  }

  @Test
  public void signIn_FailsAfterPriorSuccess_ClearsStaleSession() throws Exception {
    // A first sign-in succeeds, establishing a session — then a second sign-in (e.g. the session
    // expired and the user re-authenticates) fails. The stale session from the first sign-in must
    // not remain available to exchange()/start() as if reauthentication had succeeded.
    signInWithSession();
    assertThat(viewModel.hasSession()).isTrue();
    listener.reset();

    fakeIdpFlow.signInFailWith(new IllegalStateException("reauthentication failed"));
    viewModel.signIn();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(viewModel.hasSession()).isFalse();
  }

  // ── One-action exchange ───────────────────────────────────────────────────────

  @Test
  public void exchange_Success_ReturnsResourceToken() throws Exception {
    signInWithSession();
    listener.reset();
    fakeFlows.exchangeSucceedWith(makeToken("resource-access-token", null, null));

    viewModel.exchange();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_RESULT);
    assertThat(listener.lastResourceToken).isNotNull();
    assertThat(listener.lastResourceToken.getAccessToken()).isEqualTo("resource-access-token");
    assertThat(fakeFlows.getLastSubject().getType()).isEqualTo(SubjectAssertion.Type.ID_TOKEN);
    // Scope is entered per-call now (via selectScope() in setUp()), not configured statically.
    assertThat(fakeFlows.getLastScope()).containsExactly("chat.read");
  }

  @Test
  public void introspect_Success_UpdatesIntrospectionActiveAndUsesLastAccessToken()
      throws Exception {
    signInWithSession();
    listener.reset();
    fakeFlows.exchangeSucceedWith(makeToken("resource-access-token", null, null));
    viewModel.exchange();
    listener.awaitTerminal();

    listener.reset();
    fakeFlows.introspectSucceedWith(IntrospectInfo.Inactive.INSTANCE);

    viewModel.introspect();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_RESULT);
    assertThat(viewModel.getIntrospectionActive()).isFalse();
    assertThat(fakeFlows.getLastIntrospectedToken()).isEqualTo("resource-access-token");
  }

  @Test
  public void introspect_Failure_AttributesToConfigurationAndPreservesLastToken() throws Exception {
    signInWithSession();
    listener.reset();
    fakeFlows.exchangeSucceedWith(makeToken("resource-access-token", null, null));
    viewModel.exchange();
    listener.awaitTerminal();

    listener.reset();
    fakeFlows.introspectFailWith(new RuntimeException("introspection endpoint unreachable"));

    viewModel.introspect();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(listener.lastError).contains("introspection endpoint unreachable");
    // The failed introspection must not discard the token it was trying to verify.
    assertThat(viewModel.getLastTokenDisplay().getAccessToken()).isEqualTo("resource-access-token");
  }

  @Test
  public void redeemAgainAfterIntrospect_ClearsStaleIntrospectionResult() throws Exception {
    signInWithSession();
    listener.reset();
    IdJagAssertion idJag =
        IdJagAssertion.restore(
            "id-jag-value", "https://resource.example.com", 300, 0, "chat.read", null);
    fakeFlows.startSucceedWith(idJag);
    fakeFlows.enqueueRedeemSuccess(makeToken("resource-token-1", null, null));
    fakeFlows.enqueueRedeemSuccess(makeToken("resource-token-2", null, null));
    viewModel.start();
    listener.awaitTerminal();

    listener.reset();
    viewModel.redeem();
    listener.awaitTerminal();
    listener.reset();
    fakeFlows.introspectSucceedWith(IntrospectInfo.Inactive.INSTANCE);
    viewModel.introspect();
    listener.awaitTerminal();
    assertThat(viewModel.getIntrospectionActive()).isFalse();

    // Redeeming again must not carry the previous token's "Active: true" verdict forward onto a
    // brand-new, never-introspected token.
    listener.reset();
    viewModel.redeem();
    listener.awaitTerminal();

    assertThat(listener.lastResourceToken.getAccessToken()).isEqualTo("resource-token-2");
    assertThat(viewModel.getIntrospectionActive()).isNull();
  }

  @Test
  public void introspect_NoResourceToken_ReportsErrorWithoutCallingFlows() {
    viewModel.introspect();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(fakeFlows.getLastIntrospectedToken()).isNull();
  }

  @Test
  public void exchange_IdpExchangeFailed_AttributesToIdentityProvider() throws Exception {
    signInWithSession();
    listener.reset();
    fakeFlows.exchangeFailWith(
        new CrossAppAccessException.IdpExchangeFailed(
            "wrapper", new RuntimeException("No trusted connection configured")));

    viewModel.exchange();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(listener.lastError).contains("identity provider");
    assertThat(listener.lastError).contains("No trusted connection configured");
  }

  @Test
  public void exchange_NoSession_ReportsErrorWithoutCallingFlows() {
    viewModel.exchange();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(fakeFlows.getLastSubject()).isNull();
  }

  // ── Step-by-step mode and redemption reuse ────────────────────────────────────

  @Test
  public void start_Success_ReturnsIdJag() throws Exception {
    signInWithSession();
    listener.reset();
    IdJagAssertion idJag =
        IdJagAssertion.restore(
            "id-jag-value", "https://resource.example.com", 300, 0, "chat.read", null);
    fakeFlows.startSucceedWith(idJag);

    viewModel.start();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ID_JAG);
    assertThat(listener.lastIdJag).isNotNull();
    assertThat(listener.lastIdJag.getAudience()).isEqualTo("https://resource.example.com");
    assertThat(listener.lastIdJag.getRedemptionCount()).isEqualTo(0);
  }

  @Test
  public void redeem_CalledTwice_IncrementsRedemptionCountWithOnlyOneStartCall() throws Exception {
    signInWithSession();
    listener.reset();
    IdJagAssertion idJag =
        IdJagAssertion.restore(
            "id-jag-value", "https://resource.example.com", 300, 0, "chat.read", null);
    fakeFlows.startSucceedWith(idJag);
    fakeFlows.enqueueRedeemSuccess(makeToken("resource-token-1", null, null));
    fakeFlows.enqueueRedeemSuccess(makeToken("resource-token-2", null, null));

    viewModel.start();
    listener.awaitTerminal();

    listener.reset();
    viewModel.redeem();
    listener.awaitTerminal();
    assertThat(listener.lastResourceToken.getAccessToken()).isEqualTo("resource-token-1");
    // redeem()'s success path fires onResourceToken, not onIdJag again — the redemption count is
    // read from the ViewModel's own live display, not the listener's stale onIdJag() cache.
    assertThat(viewModel.getLastIdJagDisplay().getRedemptionCount()).isEqualTo(1);

    listener.reset();
    viewModel.redeem();
    listener.awaitTerminal();
    assertThat(listener.lastResourceToken.getAccessToken()).isEqualTo("resource-token-2");
    assertThat(viewModel.getLastIdJagDisplay().getRedemptionCount()).isEqualTo(2);

    // The whole point of the ID-JAG being reusable: two redemptions, one call to start().
    assertThat(fakeFlows.getStartCount()).isEqualTo(1);
    assertThat(fakeFlows.getRedeemCount()).isEqualTo(2);
  }

  @Test
  public void redeem_BeforeStart_ReportsErrorWithoutCallingFlows() throws Exception {
    signInWithSession();
    listener.reset();

    viewModel.redeem();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(fakeFlows.getRedeemCount()).isEqualTo(0);
  }

  @Test
  public void redeem_ExpiredIdJag_FailsWithoutCallingFlows() throws Exception {
    IdJagAssertion idJag =
        IdJagAssertion.restore(
            "id-jag-value", "https://resource.example.com", 300, 0, "chat.read", null);
    fakeFlows.startSucceedWith(idJag);
    CrossAppAccessViewModel expiringViewModel =
        new CrossAppAccessViewModel(fakeFlows, fakeIdpFlow, config, true, () -> 10_000L);
    expiringViewModel.selectScope(Collections.singletonList("chat.read"));
    CapturingListener expiringListener = new CapturingListener();
    expiringViewModel.addListener(expiringListener);
    fakeIdpFlow.signInSucceedWith(session);
    expiringViewModel.signIn();
    expiringListener.awaitTerminal();
    expiringListener.reset();

    expiringViewModel.start();
    expiringListener.awaitTerminal();
    expiringListener.reset();

    expiringViewModel.redeem();

    assertThat(expiringViewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(expiringListener.lastError).contains("expired");
    assertThat(fakeFlows.getRedeemCount()).isEqualTo(0);
    expiringViewModel.close();
  }

  @Test
  public void redeem_TargetRedemptionFailed_AttributesToResourceServer() throws Exception {
    signInWithSession();
    listener.reset();
    IdJagAssertion idJag =
        IdJagAssertion.restore(
            "id-jag-value", "https://resource.example.com", 300, 0, "chat.read", null);
    fakeFlows.startSucceedWith(idJag);
    fakeFlows.enqueueRedeemFailure(
        new CrossAppAccessException.TargetRedemptionFailed(
            "wrapper", new RuntimeException("ID-JAG expired")));

    viewModel.start();
    listener.awaitTerminal();
    listener.reset();

    viewModel.redeem();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
    assertThat(listener.lastError).contains("resource authorization server");
    assertThat(listener.lastError).contains("ID-JAG expired");
  }

  // ── Subject kind selection ─────────────────────────────────────────────────────

  @Test
  public void isKindAvailable_ReflectsSessionContents() throws Exception {
    signInWithSession();

    assertThat(viewModel.isKindAvailable(SubjectKind.IDENTITY)).isTrue();
    assertThat(viewModel.isKindAvailable(SubjectKind.ACCESS)).isTrue();
    assertThat(viewModel.isKindAvailable(SubjectKind.REFRESH)).isTrue();

    TokenInfo sessionNoRefresh = makeToken("access-value", "id-value", null);
    CrossAppAccessViewModel vm =
        new CrossAppAccessViewModel(fakeFlows, fakeIdpFlow, config, true, () -> 0L);
    CapturingListener vmListener = new CapturingListener();
    vm.addListener(vmListener);
    fakeIdpFlow.signInSucceedWith(sessionNoRefresh);
    vm.signIn();
    vmListener.awaitTerminal();

    assertThat(vm.isKindAvailable(SubjectKind.REFRESH)).isFalse();
    vm.close();
  }

  @Test
  public void selectSubjectKind_NonDefaultKindRefused_NamesKind() throws Exception {
    signInWithSession();
    listener.reset();
    fakeFlows.exchangeFailWith(
        new CrossAppAccessException.IdpExchangeFailed(
            "wrapper", new RuntimeException("subject_token_type not accepted")));

    viewModel.selectSubjectKind(SubjectKind.ACCESS);
    viewModel.exchange();
    listener.awaitTerminal();

    assertThat(listener.lastError).contains("access");
    assertThat(listener.lastError).contains("identity token");
  }

  // ── Configuration validation ──────────────────────────────────────────────────

  @Test
  public void validateConfig_CompleteConfigAndCredentials_ReturnsComplete() {
    assertThat(viewModel.validateConfig())
        .isInstanceOf(CrossAppAccessConfig.Validation.Complete.class);
  }

  @Test
  public void validateConfig_NoTargetCredential_ReturnsIncomplete() {
    CrossAppAccessViewModel vm =
        new CrossAppAccessViewModel(fakeFlows, fakeIdpFlow, config, false, () -> 0L);
    assertThat(vm.validateConfig()).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    vm.close();
  }

  @Test
  public void validateConfig_NoIdpCredentialButTargetCredentialPresent_ReturnsComplete() {
    // Unlike the target, the IdP client's credential is never required — there is no
    // hasIdpCredential parameter on this constructor at all.
    CrossAppAccessViewModel vm =
        new CrossAppAccessViewModel(fakeFlows, fakeIdpFlow, config, true, () -> 0L);
    assertThat(vm.validateConfig()).isInstanceOf(CrossAppAccessConfig.Validation.Complete.class);
    vm.close();
  }

  // ── Lifecycle ──────────────────────────────────────────────────────────────────

  @Test
  public void reset_ReturnsToMenuAndClearsResultButPreservesSession() throws Exception {
    signInWithSession();
    listener.reset();
    fakeFlows.exchangeSucceedWith(makeToken("resource-access-token", null, null));
    viewModel.exchange();
    listener.awaitTerminal();

    viewModel.reset();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.CROSS_APP_ACCESS_MENU);
    assertThat(viewModel.getLastTokenDisplay()).isNull();
    assertThat(viewModel.getLastIdJagDisplay()).isNull();
    // reset() must never discard the dedicated sign-in session — only a fresh signIn() would.
    assertThat(viewModel.hasSession()).isTrue();
  }

  @Test
  public void close_ClosesFlowsAndIdpFlow() {
    viewModel.close();
    assertThat(fakeFlows.isClosed()).isTrue();
    assertThat(fakeIdpFlow.isClosed()).isTrue();
  }

  // ── Helpers ────────────────────────────────────────────────────────────────────

  private static TokenInfo makeToken(String accessToken, String idToken, String refreshToken) {
    return new TokenInfo() {
      @NotNull
      @Override
      public String getId() {
        return "test-id";
      }

      @NotNull
      @Override
      public String getClientId() {
        return "test-client";
      }

      @NotNull
      @Override
      public String getIssuerUrl() {
        return "https://idp.example.com";
      }

      @NotNull
      @Override
      public String getTokenType() {
        return "Bearer";
      }

      @Override
      public int getExpiresIn() {
        return 3600;
      }

      @NotNull
      @Override
      public String getAccessToken() {
        return accessToken;
      }

      @Nullable
      @Override
      public String getScope() {
        return "openid profile";
      }

      @Nullable
      @Override
      public String getRefreshToken() {
        return refreshToken;
      }

      @Nullable
      @Override
      public String getIdToken() {
        return idToken;
      }

      @Nullable
      @Override
      public String getDeviceSecret() {
        return null;
      }

      @Nullable
      @Override
      public String getIssuedTokenType() {
        return null;
      }
    };
  }

  private static class CapturingListener implements CrossAppAccessViewModelListener {
    private volatile CountDownLatch terminalLatch = new CountDownLatch(1);
    volatile IdJagDisplay lastIdJag;
    volatile TokenDisplay lastResourceToken;
    volatile Boolean lastIntrospectionActive;
    volatile String lastError;

    /** Resets the latch so a second action within the same test can be awaited independently. */
    void reset() {
      terminalLatch = new CountDownLatch(1);
    }

    /** Blocks until a settled screen is reached, with a 5-second timeout. */
    void awaitTerminal() throws InterruptedException {
      boolean reached = terminalLatch.await(5, TimeUnit.SECONDS);
      assertThat(reached).isTrue();
    }

    @Override
    public void onScreenChanged(OAuth2Screen screen) {
      if (screen == OAuth2Screen.CROSS_APP_ACCESS_SIGNED_IN
          || screen == OAuth2Screen.CROSS_APP_ACCESS_ID_JAG
          || screen == OAuth2Screen.CROSS_APP_ACCESS_RESULT
          || screen == OAuth2Screen.CROSS_APP_ACCESS_ERROR) {
        terminalLatch.countDown();
      }
    }

    @Override
    public void onIdJag(IdJagDisplay idJag) {
      lastIdJag = idJag;
    }

    @Override
    public void onResourceToken(TokenDisplay token) {
      lastResourceToken = token;
    }

    @Override
    public void onIntrospection(Boolean active) {
      lastIntrospectionActive = active;
    }

    @Override
    public void onError(String message) {
      lastError = message;
    }
  }
}
