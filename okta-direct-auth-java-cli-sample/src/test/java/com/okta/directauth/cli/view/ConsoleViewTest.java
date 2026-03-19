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
package com.okta.directauth.cli.view;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.okta.directauth.cli.model.CliScreen;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.viewmodel.AuthViewModel;
import com.okta.directauth.jvm.PromptContinuation;
import org.junit.Before;
import org.junit.Test;

public class ConsoleViewTest {
  private AuthViewModel viewModel;
  private ConsoleInput input;
  private ConsoleOutput output;

  @Before
  public void setUp() {
    viewModel = mock(AuthViewModel.class);
    input = mock(ConsoleInput.class);
    output = mock(ConsoleOutput.class);
  }

  @Test
  public void mainMenu_Exit_StopsLoop() {
    when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
    when(input.readKey(anyString())).thenReturn("3");

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(output, atLeastOnce()).print(contains("=== Okta Direct Auth CLI ==="));
  }

  @Test
  public void mainMenu_SignIn_NavigatesToUsernameInput() {
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.MAIN_MENU)
        .thenReturn(CliScreen.MAIN_MENU);
    when(input.readKey(anyString())).thenReturn("1", "3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .navigateTo(CliScreen.USERNAME_INPUT);

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(viewModel).navigateTo(CliScreen.USERNAME_INPUT);
  }

  @Test
  public void usernameInput_CallsSetUsername() {
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.USERNAME_INPUT)
        .thenReturn(CliScreen.MAIN_MENU);
    when(input.readLine(anyString())).thenReturn("user@example.com");
    when(input.readKey(anyString())).thenReturn("3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .setUsername("user@example.com");

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(viewModel).setUsername("user@example.com");
  }

  @Test
  public void authenticatedScreen_ShowsTokenAndResets() {
    TokenDisplay token =
        new TokenDisplay.Builder("access123", "Bearer", 3600).idToken("id456").build();
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.AUTHENTICATED)
        .thenReturn(CliScreen.MAIN_MENU);
    when(viewModel.getLastTokenDisplay()).thenReturn(token);
    when(input.readLine(anyString())).thenReturn("");
    when(input.readKey(anyString())).thenReturn("3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .reset();

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(output, atLeastOnce()).print(contains("=== Authentication Successful ==="));
    verify(viewModel).reset();
  }

  @Test
  public void errorScreen_ShowsErrorAndReturnsToMenu() {
    when(viewModel.getCurrentScreen()).thenReturn(CliScreen.ERROR).thenReturn(CliScreen.MAIN_MENU);
    when(viewModel.getCurrentAuthState()).thenReturn(null);
    when(input.readLine(anyString())).thenReturn("");
    when(input.readKey(anyString())).thenReturn("3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .reset();

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(viewModel).reset();
  }

  @Test
  public void nullInput_StopsLoop() {
    when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
    when(input.readKey(anyString())).thenReturn(null);

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(output, atLeastOnce()).print(contains("=== Okta Direct Auth CLI ==="));
  }

  @Test
  public void recoveryMode_DoesNotShowPassword() {
    when(viewModel.isRecoveryMode()).thenReturn(true);
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.SELECT_AUTHENTICATOR)
        .thenReturn(CliScreen.MAIN_MENU);
    when(input.readKey(anyString())).thenReturn("0", "3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .navigateTo(CliScreen.USERNAME_INPUT);

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    // "] Password" would appear in numbered menu if Password were an option
    verify(output, never()).print(contains("] Password"));
    verify(output, atLeastOnce()).print(contains("OTP"));
    verify(output, atLeastOnce()).print(contains("SMS"));
    verify(output, atLeastOnce()).print(contains("Voice"));
    verify(output, atLeastOnce()).print(contains("Push (Okta Verify)"));
  }

  @Test
  public void signInMode_ShowsAllAuthMethods() {
    when(viewModel.isRecoveryMode()).thenReturn(false);
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.SELECT_AUTHENTICATOR)
        .thenReturn(CliScreen.MAIN_MENU);
    when(input.readKey(anyString())).thenReturn("0", "3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .navigateTo(CliScreen.USERNAME_INPUT);

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(output, atLeastOnce()).print(contains("Password"));
    verify(output, atLeastOnce()).print(contains("OTP"));
    verify(output, atLeastOnce()).print(contains("SMS"));
    verify(output, atLeastOnce()).print(contains("Voice"));
    verify(output, atLeastOnce()).print(contains("Push (Okta Verify)"));
  }

  @Test
  public void codeEntry_NoAuthState_CallsAuthenticate() {
    when(viewModel.getCurrentAuthState()).thenReturn(null);
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.CODE_ENTRY)
        .thenReturn(CliScreen.MAIN_MENU);
    when(input.readLine(anyString())).thenReturn("123456");
    when(input.readKey(anyString())).thenReturn("3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .authenticate("123456");

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(viewModel).authenticate("123456");
    verify(viewModel, never()).submitMfaOtp(anyString());
  }

  @Test
  public void codeEntry_WithAuthState_CallsSubmitMfaOtp() {
    PromptContinuation promptState = mock(PromptContinuation.class);
    when(viewModel.getCurrentAuthState()).thenReturn(promptState);
    when(viewModel.getCurrentScreen())
        .thenReturn(CliScreen.CODE_ENTRY)
        .thenReturn(CliScreen.MAIN_MENU);
    when(input.readLine(anyString())).thenReturn("654321");
    when(input.readKey(anyString())).thenReturn("3");

    doAnswer(
            invocation -> {
              when(viewModel.getCurrentScreen()).thenReturn(CliScreen.MAIN_MENU);
              return null;
            })
        .when(viewModel)
        .submitMfaOtp("654321");

    ConsoleView view = new ConsoleView(viewModel, input, output, false, null);
    view.run();

    verify(viewModel).submitMfaOtp("654321");
    verify(viewModel, never()).authenticate(anyString());
  }
}
