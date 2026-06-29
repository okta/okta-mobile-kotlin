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
import com.okta.directauth.cli.model.DeviceCodeDisplay;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.oauth2.FakeOAuth2Flows;
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class OAuth2ViewModelTest {
  private FakeOAuth2Flows fakeFlows;
  private OAuth2ViewModel viewModel;
  private CapturingListener listener;

  @Before
  public void setUp() {
    fakeFlows = new FakeOAuth2Flows();
    viewModel = new OAuth2ViewModel(fakeFlows, false);
    listener = new CapturingListener();
    viewModel.addListener(listener);
  }

  @After
  public void tearDown() {
    viewModel.close();
  }

  // ── US1: Resource Owner Password ────────────────────────────────────────────

  @Test
  public void startResourceOwner_Success() throws Exception {
    fakeFlows.succeedWith(makeToken("at-123", "it-456"));
    viewModel.startResourceOwner("user@example.com", "password");
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.AUTHENTICATED);
    assertThat(listener.lastResult).isNotNull();
    assertThat(listener.lastResult.getAccessToken()).isEqualTo("at-123");
    assertThat(listener.lastResult.getIdToken()).isEqualTo("it-456");
    assertThat(fakeFlows.getLastMethod()).isEqualTo("resourceOwner");
    assertThat(fakeFlows.getLastUsername()).isEqualTo("user@example.com");
    assertThat(listener.screenHistory).containsExactly(OAuth2Screen.AUTHENTICATED).inOrder();
  }

  @Test
  public void startResourceOwner_Error() throws Exception {
    fakeFlows.failWith(new RuntimeException("invalid_grant: Bad credentials"));
    viewModel.startResourceOwner("user@example.com", "wrong");
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).contains("invalid_grant");
    assertThat(listener.screenHistory).containsExactly(OAuth2Screen.ERROR).inOrder();
  }

  @Test
  public void startResourceOwner_EmptyUsername_ValidationError() {
    viewModel.startResourceOwner("", "password");

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).isNotNull();
  }

  @Test
  public void startResourceOwner_EmptyPassword_ValidationError() {
    viewModel.startResourceOwner("user@example.com", "");

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).isNotNull();
  }

  // ── US2: Device Authorization ────────────────────────────────────────────────

  @Test
  public void startDeviceAuthorization_EmitsDeviceCodeThenToken() throws Exception {
    DeviceAuthorizationFlowContext ctx =
        makeDeviceContext("ABC-123", "https://example.okta.com/activate", 300);
    fakeFlows.deviceStartSucceedWith(ctx);
    fakeFlows.succeedWith(makeToken("at-device", null));

    viewModel.startDeviceAuthorization();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.AUTHENTICATED);
    assertThat(listener.lastDeviceCode).isNotNull();
    assertThat(listener.lastDeviceCode.getUserCode()).isEqualTo("ABC-123");
    assertThat(listener.lastDeviceCode.getVerificationUri())
        .isEqualTo("https://example.okta.com/activate");
    assertThat(listener.lastResult).isNotNull();
    assertThat(listener.lastResult.getAccessToken()).isEqualTo("at-device");
    // State machine: DEVICE_POLLING set synchronously, then AUTHENTICATED when resume completes.
    assertThat(listener.screenHistory)
        .containsAtLeast(OAuth2Screen.DEVICE_POLLING, OAuth2Screen.AUTHENTICATED)
        .inOrder();
  }

  @Test
  public void startDeviceAuthorization_Timeout() throws Exception {
    DeviceAuthorizationFlowContext ctx =
        makeDeviceContext("XYZ-789", "https://example.okta.com/activate", 30);
    fakeFlows.deviceStartSucceedWith(ctx);
    fakeFlows.failWith(new RuntimeException("authorization_pending: timed out"));

    viewModel.startDeviceAuthorization();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).contains("authorization_pending");
    assertThat(listener.screenHistory)
        .containsAtLeast(OAuth2Screen.DEVICE_POLLING, OAuth2Screen.ERROR)
        .inOrder();
  }

  @Test
  public void startDeviceAuthorization_StartFails() throws Exception {
    fakeFlows.deviceStartFailWith(new RuntimeException("Device code request failed"));

    viewModel.startDeviceAuthorization();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).contains("Device code request failed");
  }

  // ── US3: Browser Sign-In ─────────────────────────────────────────────────────

  @Test
  public void startBrowserSignIn_Success() throws Exception {
    fakeFlows.succeedWith(makeToken("at-browser", "it-browser"));
    viewModel.startBrowserSignIn();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.AUTHENTICATED);
    assertThat(listener.lastResult.getAccessToken()).isEqualTo("at-browser");
    assertThat(fakeFlows.getLastMethod()).isEqualTo("browserSignIn");
    assertThat(listener.screenHistory)
        .containsAtLeast(OAuth2Screen.BROWSER_WAITING, OAuth2Screen.AUTHENTICATED)
        .inOrder();
  }

  @Test
  public void startBrowserSignIn_Canceled() throws Exception {
    fakeFlows.failWith(new RuntimeException("Browser sign-in was cancelled or timed out"));
    viewModel.startBrowserSignIn();
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).contains("cancelled or timed out");
  }

  // ── US4: Token Exchange ───────────────────────────────────────────────────────

  @Test
  public void startTokenExchange_Success() throws Exception {
    fakeFlows.succeedWith(makeToken("at-exchange", null));
    viewModel.startTokenExchange("id-token-value", "device-secret-value");
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.AUTHENTICATED);
    assertThat(fakeFlows.getLastMethod()).isEqualTo("tokenExchange");
    assertThat(fakeFlows.getLastIdToken()).isEqualTo("id-token-value");
    assertThat(fakeFlows.getLastDeviceSecret()).isEqualTo("device-secret-value");
  }

  @Test
  public void startTokenExchange_EmptyIdToken_ValidationError() {
    viewModel.startTokenExchange("", "device-secret");

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).isNotNull();
  }

  @Test
  public void startTokenExchange_EmptyDeviceSecret_ValidationError() {
    viewModel.startTokenExchange("id-token", "");

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).isNotNull();
  }

  // ── US4: Session Token ───────────────────────────────────────────────────────

  @Test
  public void startSessionToken_Success() throws Exception {
    fakeFlows.succeedWith(makeToken("at-session", "it-session"));
    viewModel.startSessionToken("session-token-value");
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.AUTHENTICATED);
    assertThat(fakeFlows.getLastMethod()).isEqualTo("sessionToken");
    assertThat(fakeFlows.getLastSessionToken()).isEqualTo("session-token-value");
  }

  @Test
  public void startSessionToken_EmptyToken_ValidationError() {
    viewModel.startSessionToken("");

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).isNotNull();
  }

  @Test
  public void startSessionToken_Error() throws Exception {
    fakeFlows.failWith(new RuntimeException("invalid_token"));
    viewModel.startSessionToken("bad-session-token");
    listener.awaitTerminal();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.ERROR);
    assertThat(listener.lastError).contains("invalid_token");
  }

  // ── Lifecycle ────────────────────────────────────────────────────────────────

  @Test
  public void reset_ReturnToMenu() throws Exception {
    fakeFlows.succeedWith(makeToken("at-x", null));
    viewModel.startResourceOwner("u", "p");
    listener.awaitTerminal();

    viewModel.reset();
    assertThat(viewModel.getCurrentScreen()).isEqualTo(OAuth2Screen.MENU);
    assertThat(viewModel.getLastTokenDisplay()).isNull();
  }

  @Test
  public void close_ClosesFlows() {
    viewModel.close();
    assertThat(fakeFlows.isClosed()).isTrue();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private static TokenInfo makeToken(String accessToken, String idToken) {
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
        return "https://example.okta.com";
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
        return "openid profile email";
      }

      @Nullable
      @Override
      public String getRefreshToken() {
        return null;
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

  private static DeviceAuthorizationFlowContext makeDeviceContext(
      String userCode, String verificationUri, int expiresIn) {
    return new DeviceAuthorizationFlowContext(
        verificationUri, null, userCode, expiresIn, "fake-device-code", 5);
  }

  private static class CapturingListener implements OAuth2ViewModelListener {
    // Released when the screen reaches a terminal state (AUTHENTICATED or ERROR).
    private final CountDownLatch terminalLatch = new CountDownLatch(1);
    OAuth2Screen lastScreen;
    DeviceCodeDisplay lastDeviceCode;
    TokenDisplay lastResult;
    String lastError;
    final List<OAuth2Screen> screenHistory = new ArrayList<>();

    /** Blocks until the ViewModel reaches AUTHENTICATED or ERROR, with a 5-second timeout. */
    void awaitTerminal() throws InterruptedException {
      boolean reached = terminalLatch.await(5, TimeUnit.SECONDS);
      assertThat(reached).isTrue();
    }

    @Override
    public void onScreenChanged(OAuth2Screen screen) {
      lastScreen = screen;
      screenHistory.add(screen);
      if (screen == OAuth2Screen.AUTHENTICATED || screen == OAuth2Screen.ERROR) {
        terminalLatch.countDown();
      }
    }

    @Override
    public void onDeviceCode(DeviceCodeDisplay deviceCode) {
      lastDeviceCode = deviceCode;
    }

    @Override
    public void onResult(TokenDisplay token) {
      lastResult = token;
    }

    @Override
    public void onError(String message) {
      lastError = message;
    }
  }
}
