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

import com.okta.directauth.cli.model.AuthMethod;
import com.okta.directauth.cli.model.CliPreferences;
import com.okta.directauth.cli.model.CliScreen;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.viewmodel.AuthViewModel;
import com.okta.directauth.cli.viewmodel.AuthViewModelListener;
import com.okta.directauth.jvm.DirectAuthenticationState;
import com.okta.directauth.jvm.OobPendingContinuation;
import com.okta.directauth.jvm.TransferContinuation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CLI view that implements the interactive console loop.
 *
 * <p>Reads user input via {@link ConsoleInput}, delegates actions to {@link AuthViewModel}, and
 * renders output via {@link ConsoleOutput} using {@link StateRenderer}.
 */
public final class ConsoleView implements AuthViewModelListener {
  private final AuthViewModel viewModel;
  private final ConsoleInput input;
  private final ConsoleOutput output;
  private final boolean decodedFormat;
  private final CliPreferences preferences;
  private volatile boolean running = true;

  /**
   * Creates a new ConsoleView.
   *
   * @param viewModel the authentication view model
   * @param input the console input source
   * @param output the console output target
   * @param decodedFormat true to display decoded JWT claims, false for raw JWT
   * @param preferences CLI preferences for remembering the username (may be null)
   */
  public ConsoleView(
      AuthViewModel viewModel,
      ConsoleInput input,
      ConsoleOutput output,
      boolean decodedFormat,
      CliPreferences preferences) {
    this.viewModel = viewModel;
    this.input = input;
    this.output = output;
    this.decodedFormat = decodedFormat;
    this.preferences = preferences;
    viewModel.addListener(this);
  }

  /** Runs the main interactive loop until the user exits. */
  public void run() {
    while (running) {
      CliScreen screen = viewModel.getCurrentScreen();
      if (screen == CliScreen.MAIN_MENU) {
        showMainMenu();
      } else if (screen == CliScreen.USERNAME_INPUT) {
        promptUsername();
      } else if (screen == CliScreen.SELECT_AUTHENTICATOR) {
        promptAuthenticator();
      } else if (screen == CliScreen.PASSWORD_ENTRY) {
        promptPassword();
      } else if (screen == CliScreen.CODE_ENTRY) {
        promptCode();
      } else if (screen == CliScreen.MFA_REQUIRED) {
        promptMfa();
      } else if (screen == CliScreen.OOB_POLLING) {
        waitForOob();
      } else if (screen == CliScreen.AUTHENTICATED) {
        showSuccess();
      } else if (screen == CliScreen.PASSWORD_CHANGE) {
        promptPasswordChange();
      } else if (screen == CliScreen.ERROR) {
        showError();
      }
    }
  }

  /** Stops the run loop. */
  public void stop() {
    running = false;
  }

  @Override
  public void onScreenChanged(CliScreen screen) {
    // Screen changes are handled by the run loop polling getCurrentScreen()
  }

  @Override
  public void onAuthStateChanged(DirectAuthenticationState state) {
    // State changes are handled by the run loop
  }

  @Override
  public void onError(String message) {
    output.println("Error: " + message);
  }

  private void showMainMenu() {
    List<String> options = Arrays.asList("Sign In", "Forgot Password", "Exit");
    output.print(StateRenderer.renderMenu("=== Okta Direct Auth CLI ===", options));

    String choice = input.readKey("Select option: ");
    if (choice == null) {
      stop();
      return;
    }
    if ("1".equals(choice)) {
      viewModel.navigateTo(CliScreen.USERNAME_INPUT);
    } else if ("2".equals(choice)) {
      viewModel.switchToRecovery();
    } else if ("3".equals(choice)) {
      stop();
    } else {
      output.println("Invalid option. Please try again.");
    }
  }

  private void promptUsername() {
    output.println("Enter username (or [0] to go back):");
    String lastUsername = (preferences != null) ? preferences.getLastUsername() : "";
    String prompt = !lastUsername.isEmpty() ? "Username [" + lastUsername + "]: " : "Username: ";
    String username = input.readLine(prompt);
    if (username == null) {
      stop();
      return;
    }
    if ("0".equals(username.trim())) {
      viewModel.reset();
      return;
    }
    if (username.trim().isEmpty() && !lastUsername.isEmpty()) {
      username = lastUsername;
    }
    viewModel.setUsername(username);
  }

  private void promptAuthenticator() {
    AuthMethod[] methods;
    if (viewModel.isRecoveryMode()) {
      methods = new AuthMethod[] {AuthMethod.PASSWORD, AuthMethod.OTP};
    } else {
      methods = AuthMethod.values();
    }

    List<String> labels =
        Arrays.stream(methods).map(AuthMethod::getLabel).collect(Collectors.toList());
    output.print(StateRenderer.renderMenu("Select authentication method:", labels));
    output.println("[0] Back");

    String choice = input.readKey("Select option: ");
    if (choice == null) {
      stop();
      return;
    }
    if ("0".equals(choice)) {
      viewModel.navigateTo(CliScreen.USERNAME_INPUT);
      return;
    }
    try {
      int index = Integer.parseInt(choice) - 1;
      if (index >= 0 && index < methods.length) {
        AuthMethod selected = methods[index];
        viewModel.selectAuthMethod(selected);
        if (selected != AuthMethod.PASSWORD && selected != AuthMethod.OTP) {
          viewModel.waitForResult();
        }
      } else {
        output.println("Invalid option. Please try again.");
      }
    } catch (NumberFormatException e) {
      output.println("Invalid option. Please try again.");
    }
  }

  private void promptPassword() {
    output.println("Enter password (or [0] to go back):");
    String password = input.readPassword("Password: ");
    if (password == null) {
      stop();
      return;
    }
    if ("0".equals(password)) {
      viewModel.navigateTo(CliScreen.SELECT_AUTHENTICATOR);
      return;
    }
    viewModel.authenticate(password);
    viewModel.waitForResult();
  }

  private void promptCode() {
    output.println("Enter verification code (or [0] to go back):");
    String code = input.readLine("Code: ");
    if (code == null) {
      stop();
      return;
    }
    if ("0".equals(code.trim())) {
      viewModel.navigateTo(CliScreen.SELECT_AUTHENTICATOR);
      return;
    }
    viewModel.submitMfaOtp(code);
    viewModel.waitForResult();
  }

  private void promptMfa() {
    AuthMethod[] methods = {
      AuthMethod.OTP, AuthMethod.SMS, AuthMethod.VOICE, AuthMethod.OKTA_VERIFY
    };
    List<String> labels =
        Arrays.stream(methods).map(AuthMethod::getLabel).collect(Collectors.toList());
    output.print(StateRenderer.renderMenu("MFA Required. Select method:", labels));
    output.println("[0] Back to menu");

    String choice = input.readKey("Select option: ");
    if (choice == null) {
      stop();
      return;
    }
    if ("0".equals(choice)) {
      viewModel.reset();
      return;
    }
    try {
      int index = Integer.parseInt(choice) - 1;
      if (index >= 0 && index < methods.length) {
        viewModel.resumeMfa(methods[index]);
        if (methods[index] != AuthMethod.OTP) {
          viewModel.waitForResult();
        }
      } else {
        output.println("Invalid option. Please try again.");
      }
    } catch (NumberFormatException e) {
      output.println("Invalid option. Please try again.");
    }
  }

  private void waitForOob() {
    DirectAuthenticationState state = viewModel.getCurrentAuthState();

    if (state instanceof TransferContinuation) {
      TransferContinuation transfer = (TransferContinuation) state;
      if (transfer.getBindingCode() != null) {
        output.print(StateRenderer.renderBindingCode(transfer.getBindingCode()));
      }
      output.println(
          "Waiting for device approval... (expires in " + transfer.getExpirationInSeconds() + "s)");
    } else if (state instanceof OobPendingContinuation) {
      OobPendingContinuation oob = (OobPendingContinuation) state;
      output.println("Waiting for approval... (expires in " + oob.getExpirationInSeconds() + "s)");
    }

    int maxAttempts = getOobExpirationSeconds() / 5;
    for (int i = 0; i < maxAttempts; i++) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }

      viewModel.pollOob();
      DirectAuthenticationState result = viewModel.waitForResult();
      if (result != null) {
        CliScreen screen = viewModel.getCurrentScreen();
        if (screen != CliScreen.OOB_POLLING) {
          return;
        }
        if (result instanceof DirectAuthenticationState.AuthorizationPending) {
          output.print(".");
        }
      }
    }

    output.println("\nPolling timed out.");
    String choice = input.readKey("Would you like to [1] Retry or [2] Return to menu? ");
    if (choice == null || "2".equals(choice)) {
      viewModel.reset();
    }
  }

  private void showSuccess() {
    if (preferences != null && !viewModel.getUsername().isEmpty()) {
      preferences.setLastUsername(viewModel.getUsername());
    }
    TokenDisplay token = viewModel.getLastTokenDisplay();
    if (token != null) {
      output.print(StateRenderer.renderToken(token, decodedFormat));
    }
    output.println("");
    String line = input.readLine("Press Enter to sign out...");
    if (line == null) {
      stop();
      return;
    }
    viewModel.reset();
  }

  private void promptPasswordChange() {
    String newPassword = input.readPassword("Enter new password: ");
    if (newPassword == null) {
      stop();
      return;
    }
    String confirmPassword = input.readPassword("Confirm new password: ");
    if (confirmPassword == null) {
      stop();
      return;
    }

    if (!newPassword.equals(confirmPassword)) {
      output.println("Error: Passwords do not match. Please try again.");
      return;
    }

    if (newPassword.isEmpty()) {
      output.println("Error: Password cannot be empty.");
      return;
    }

    TokenDisplay token = viewModel.getLastTokenDisplay();
    if (token != null) {
      boolean success = viewModel.changePassword(token.getAccessToken(), newPassword);
      if (success) {
        output.print(StateRenderer.renderPasswordChangeSuccess());
      }
    }

    viewModel.reset();
  }

  private void showError() {
    DirectAuthenticationState state = viewModel.getCurrentAuthState();
    if (state instanceof DirectAuthenticationState.Error) {
      output.print(StateRenderer.renderError((DirectAuthenticationState.Error) state));
    }

    boolean isRetryable = isRetryableError(state);
    if (isRetryable) {
      String choice = input.readKey("Would you like to [1] Retry or [2] Return to menu? ");
      if (choice == null) {
        stop();
        return;
      }
      if ("1".equals(choice)) {
        viewModel.authenticate(null);
        viewModel.waitForResult();
        return;
      }
    } else {
      String line = input.readLine("Press Enter to return to menu...");
      if (line == null) {
        stop();
        return;
      }
    }
    viewModel.reset();
  }

  private boolean isRetryableError(DirectAuthenticationState state) {
    if (state instanceof DirectAuthenticationState.Error.InternalError) {
      return true;
    }
    if (state instanceof DirectAuthenticationState.Error.HttpError) {
      int code = ((DirectAuthenticationState.Error.HttpError) state).getHttpStatusCode().getValue();
      return code >= 500;
    }
    return false;
  }

  private int getOobExpirationSeconds() {
    DirectAuthenticationState state = viewModel.getCurrentAuthState();
    if (state instanceof OobPendingContinuation) {
      return ((OobPendingContinuation) state).getExpirationInSeconds();
    }
    if (state instanceof TransferContinuation) {
      return ((TransferContinuation) state).getExpirationInSeconds();
    }
    return 300;
  }
}
