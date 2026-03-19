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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.okta.authfoundation.client.TokenInfo;
import com.okta.directauth.cli.model.AuthMethod;
import com.okta.directauth.cli.model.CliScreen;
import com.okta.directauth.jvm.DirectAuthenticationFlow;
import com.okta.directauth.jvm.DirectAuthenticationState;
import com.okta.directauth.jvm.MfaRequired;
import com.okta.directauth.jvm.OobPendingContinuation;
import com.okta.directauth.jvm.PromptContinuation;
import com.okta.directauth.jvm.TransferContinuation;
import com.okta.directauth.model.PrimaryFactor;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuthViewModelTest {
  private DirectAuthenticationFlow signInFlow;
  private DirectAuthenticationFlow recoveryFlow;
  private AuthViewModel viewModel;
  private AuthViewModelListener listener;

  @Before
  public void setUp() {
    signInFlow = mock(DirectAuthenticationFlow.class);
    recoveryFlow = mock(DirectAuthenticationFlow.class);
    listener = mock(AuthViewModelListener.class);
    viewModel = new AuthViewModel(signInFlow, recoveryFlow, "https://example.okta.com");
    viewModel.addListener(listener);
  }

  @After
  public void tearDown() {
    viewModel.close();
  }

  @Test
  public void initialScreen_IsMainMenu() {
    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.MAIN_MENU);
  }

  @Test
  public void setUsername_AdvancesToSelectAuthenticator() {
    viewModel.setUsername("user@example.com");

    assertThat(viewModel.getUsername()).isEqualTo("user@example.com");
    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.SELECT_AUTHENTICATOR);
    verify(listener).onScreenChanged(CliScreen.SELECT_AUTHENTICATOR);
  }

  @Test
  public void setUsername_EmptyInput_NotifiesError() {
    viewModel.setUsername("");

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.MAIN_MENU);
    verify(listener).onError("Username cannot be empty");
  }

  @Test
  public void setUsername_WhitespaceOnly_NotifiesError() {
    viewModel.setUsername("   ");

    verify(listener).onError("Username cannot be empty");
  }

  @Test
  public void selectAuthMethod_Password_AdvancesToPasswordEntry() {
    viewModel.selectAuthMethod(AuthMethod.PASSWORD);

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.PASSWORD_ENTRY);
    assertThat(viewModel.getSelectedMethod()).isEqualTo(AuthMethod.PASSWORD);
  }

  @Test
  public void selectAuthMethod_Otp_AdvancesToCodeEntry() {
    viewModel.selectAuthMethod(AuthMethod.OTP);

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.CODE_ENTRY);
  }

  @Test
  public void authenticate_WithPassword_TransitionsToAuthenticated() {
    DirectAuthenticationState.Authenticated authState =
        mock(DirectAuthenticationState.Authenticated.class);
    TokenInfo tokenInfo = createMockTokenInfo();
    when(authState.getToken()).thenReturn(tokenInfo);
    when(signInFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(authState));

    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.PASSWORD);
    viewModel.authenticate("password123");
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.AUTHENTICATED);
    assertThat(viewModel.getLastTokenDisplay()).isNotNull();
    assertThat(viewModel.getLastTokenDisplay().getAccessToken()).isEqualTo("access_token");
  }

  @Test
  public void authenticate_EmptyPassword_NotifiesError() {
    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.PASSWORD);
    viewModel.authenticate("");

    verify(listener).onError("Password cannot be empty");
  }

  @Test
  public void authenticate_WithError_TransitionsToError() {
    DirectAuthenticationState.Error.InternalError errorState =
        mock(DirectAuthenticationState.Error.InternalError.class);
    when(signInFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(errorState));

    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.PASSWORD);
    viewModel.authenticate("password");
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.ERROR);
  }

  @Test
  public void authenticate_WithMfaRequired_TransitionsToMfaRequired() {
    MfaRequired mfaState = mock(MfaRequired.class);
    when(signInFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(mfaState));

    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.PASSWORD);
    viewModel.authenticate("password");
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.MFA_REQUIRED);
  }

  @Test
  public void authenticate_WithPromptContinuation_TransitionsToCodeEntry() {
    PromptContinuation promptState = mock(PromptContinuation.class);
    when(signInFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(promptState));

    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.OTP);
    viewModel.authenticate("123456");
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.CODE_ENTRY);
  }

  @Test
  public void authenticate_WithOobPending_TransitionsToOobPolling() {
    OobPendingContinuation oobState = mock(OobPendingContinuation.class);
    when(oobState.getExpirationInSeconds()).thenReturn(300);
    when(signInFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(oobState));

    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.SMS);
    viewModel.authenticate(null);
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.OOB_POLLING);
  }

  @Test
  public void authenticate_WithTransferContinuation_StoresBindingCode() {
    TransferContinuation transferState = mock(TransferContinuation.class);
    when(transferState.getBindingCode()).thenReturn("AB");
    when(transferState.getExpirationInSeconds()).thenReturn(300);
    when(signInFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(transferState));

    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.OKTA_VERIFY);
    viewModel.authenticate(null);
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.OOB_POLLING);
    assertThat(viewModel.getBindingCode()).isEqualTo("AB");
  }

  @Test
  public void reset_ReturnsToMainMenu() {
    viewModel.setUsername("user@example.com");
    viewModel.reset();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.MAIN_MENU);
    assertThat(viewModel.getUsername()).isEmpty();
    assertThat(viewModel.getSelectedMethod()).isNull();
  }

  @Test
  public void close_DelegatesToFlows() {
    viewModel.close();

    verify(signInFlow).close();
    verify(recoveryFlow).close();
  }

  @Test
  public void switchToRecovery_SetsRecoveryMode() {
    viewModel.switchToRecovery();

    assertThat(viewModel.isRecoveryMode()).isTrue();
    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.USERNAME_INPUT);
  }

  @Test
  public void authenticate_RecoveryMode_UsesRecoveryFlow() {
    DirectAuthenticationState.Authenticated authState =
        mock(DirectAuthenticationState.Authenticated.class);
    TokenInfo tokenInfo = createMockTokenInfo();
    when(authState.getToken()).thenReturn(tokenInfo);
    when(recoveryFlow.startAsync(any(), any(PrimaryFactor.class)))
        .thenReturn(CompletableFuture.completedFuture(authState));

    viewModel.switchToRecovery();
    viewModel.setUsername("user@example.com");
    viewModel.selectAuthMethod(AuthMethod.PASSWORD);
    viewModel.authenticate("password");
    viewModel.waitForResult();

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.PASSWORD_CHANGE);
    verify(recoveryFlow).startAsync(any(), any(PrimaryFactor.class));
  }

  @Test
  public void navigateTo_ChangesScreen() {
    viewModel.navigateTo(CliScreen.USERNAME_INPUT);

    assertThat(viewModel.getCurrentScreen()).isEqualTo(CliScreen.USERNAME_INPUT);
    verify(listener).onScreenChanged(CliScreen.USERNAME_INPUT);
  }

  private TokenInfo createMockTokenInfo() {
    TokenInfo tokenInfo = mock(TokenInfo.class);
    when(tokenInfo.getAccessToken()).thenReturn("access_token");
    when(tokenInfo.getTokenType()).thenReturn("Bearer");
    when(tokenInfo.getExpiresIn()).thenReturn(3600);
    when(tokenInfo.getIdToken()).thenReturn("id_token");
    return tokenInfo;
  }
}
