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

import com.okta.authfoundation.ChallengeGrantType;
import com.okta.directauth.cli.CliLogger;
import com.okta.directauth.cli.model.AuthMethod;
import com.okta.directauth.cli.model.CliScreen;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.jvm.DirectAuthenticationFlow;
import com.okta.directauth.jvm.DirectAuthenticationState;
import com.okta.directauth.jvm.MfaRequired;
import com.okta.directauth.jvm.OobPendingContinuation;
import com.okta.directauth.jvm.PromptContinuation;
import com.okta.directauth.jvm.TransferContinuation;
import com.okta.directauth.jvm.WebAuthnContinuation;
import com.okta.directauth.model.OobChannel;
import com.okta.directauth.model.PrimaryFactor;
import com.okta.directauth.model.SecondaryFactor;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Core orchestration ViewModel that manages authentication state and delegates to the SDK.
 *
 * <p>Manages two flow instances: one for sign-in and one for password recovery. Uses {@link
 * AuthViewModelListener} callbacks to notify the view of state changes.
 */
public final class AuthViewModel implements Closeable {
  private static final String TAG = "ViewModel";
  private static final long TIMEOUT_SECONDS = 30;

  private final DirectAuthenticationFlow signInFlow;
  private final DirectAuthenticationFlow recoveryFlow;
  private final String issuer;
  private final List<AuthViewModelListener> listeners = new CopyOnWriteArrayList<>();

  private volatile CliScreen currentScreen = CliScreen.MAIN_MENU;
  private volatile DirectAuthenticationState currentAuthState;
  private volatile String username = "";
  private volatile AuthMethod selectedMethod;
  private volatile boolean recoveryMode = false;
  private volatile CompletableFuture<DirectAuthenticationState> pendingFuture;
  private volatile TokenDisplay lastTokenDisplay;
  private volatile String bindingCode;

  /**
   * Creates a new AuthViewModel.
   *
   * @param signInFlow the flow for sign-in authentication
   * @param recoveryFlow the flow for password recovery (may be null if SSPR not supported)
   * @param issuer the Okta issuer URL (used for MyAccount API calls)
   */
  public AuthViewModel(
      DirectAuthenticationFlow signInFlow, DirectAuthenticationFlow recoveryFlow, String issuer) {
    this.signInFlow = signInFlow;
    this.recoveryFlow = recoveryFlow;
    this.issuer = issuer;
  }

  /**
   * Adds a listener for state change notifications.
   *
   * @param listener the listener to add
   */
  public void addListener(AuthViewModelListener listener) {
    listeners.add(listener);
  }

  public CliScreen getCurrentScreen() {
    return currentScreen;
  }

  public DirectAuthenticationState getCurrentAuthState() {
    return currentAuthState;
  }

  public String getUsername() {
    return username;
  }

  public AuthMethod getSelectedMethod() {
    return selectedMethod;
  }

  public TokenDisplay getLastTokenDisplay() {
    return lastTokenDisplay;
  }

  public String getBindingCode() {
    return bindingCode;
  }

  public boolean isRecoveryMode() {
    return recoveryMode;
  }

  /**
   * Navigates to the specified screen. Used by the view for menu-driven navigation.
   *
   * @param screen the target screen
   */
  public void navigateTo(CliScreen screen) {
    setCurrentScreen(screen);
  }

  /**
   * Sets the username and advances to authenticator selection.
   *
   * @param username the username entered by the user
   */
  public void setUsername(String username) {
    if (username == null || username.trim().isEmpty()) {
      notifyError("Username cannot be empty");
      return;
    }
    this.username = username.trim();
    CliLogger.debug(TAG, "Username set: " + this.username);
    setCurrentScreen(CliScreen.SELECT_AUTHENTICATOR);
  }

  /**
   * Selects an authentication method and advances to the appropriate input screen.
   *
   * @param method the selected authentication method
   */
  public void selectAuthMethod(AuthMethod method) {
    this.selectedMethod = method;
    if (method == AuthMethod.PASSWORD) {
      setCurrentScreen(CliScreen.PASSWORD_ENTRY);
    } else if (method == AuthMethod.OTP) {
      setCurrentScreen(CliScreen.CODE_ENTRY);
    } else {
      authenticate(null);
    }
  }

  /**
   * Starts authentication with the given credential.
   *
   * @param credential the password or OTP code (null for OOB methods)
   */
  public void authenticate(String credential) {
    if (selectedMethod == AuthMethod.PASSWORD || selectedMethod == AuthMethod.OTP) {
      if (credential == null || credential.isEmpty()) {
        String fieldName = (selectedMethod == AuthMethod.PASSWORD) ? "Password" : "Code";
        notifyError(fieldName + " cannot be empty");
        return;
      }
    }

    PrimaryFactor factor;
    factor = selectedMethod.asFactor(Objects.requireNonNullElse(credential, ""));

    DirectAuthenticationFlow flow = recoveryMode ? recoveryFlow : signInFlow;
    CliLogger.info(
        TAG,
        "Starting authentication (method: " + selectedMethod + ", recovery: " + recoveryMode + ")");
    pendingFuture =
        flow.startAsync(username, factor)
            .thenApply(
                state -> {
                  handleAuthState(state);
                  return state;
                });
  }

  /**
   * Resumes MFA with the selected secondary method.
   *
   * @param mfaMethod the MFA method to use
   */
  public void resumeMfa(AuthMethod mfaMethod) {
    if (!(currentAuthState instanceof MfaRequired)) {
      notifyError("MFA is not required in the current state");
      return;
    }
    MfaRequired mfaRequired = (MfaRequired) currentAuthState;

    SecondaryFactor factor;
    if (mfaMethod == AuthMethod.OTP) {
      setCurrentScreen(CliScreen.CODE_ENTRY);
      this.selectedMethod = mfaMethod;
      return;
    } else if (mfaMethod == AuthMethod.SMS) {
      factor = new PrimaryFactor.Oob(OobChannel.SMS);
    } else if (mfaMethod == AuthMethod.VOICE) {
      factor = new PrimaryFactor.Oob(OobChannel.VOICE);
    } else if (mfaMethod == AuthMethod.OKTA_VERIFY) {
      factor = new PrimaryFactor.Oob(OobChannel.PUSH);
    } else {
      notifyError("Unsupported MFA method: " + mfaMethod);
      return;
    }

    List<ChallengeGrantType> challengeTypes;
    challengeTypes = List.of(ChallengeGrantType.OobMfa.INSTANCE);

    CliLogger.info(TAG, "Initiating MFA challenge (method: " + mfaMethod + ")");
    pendingFuture =
        mfaRequired
            .challengeAsync(factor, challengeTypes)
            .thenApply(
                state -> {
                  handleAuthState(state);
                  return state;
                });
  }

  /**
   * Submits an OTP code for MFA when in CODE_ENTRY screen after MFA required.
   *
   * @param code the OTP code
   */
  public void submitMfaOtp(String code) {
    if (code == null || code.trim().isEmpty()) {
      notifyError("Code cannot be empty");
      return;
    }

    if (currentAuthState instanceof PromptContinuation) {
      PromptContinuation prompt = (PromptContinuation) currentAuthState;
      pendingFuture =
          prompt
              .proceedAsync(code.trim())
              .thenApply(
                  state -> {
                    handleAuthState(state);
                    return state;
                  });
    } else if (currentAuthState instanceof MfaRequired) {
      MfaRequired mfaRequired = (MfaRequired) currentAuthState;
      SecondaryFactor factor = new PrimaryFactor.Otp(code.trim());
      List<ChallengeGrantType> challengeTypes = List.of(ChallengeGrantType.OtpMfa.INSTANCE);
      pendingFuture =
          mfaRequired
              .resumeAsync(factor, challengeTypes)
              .thenApply(
                  state -> {
                    handleAuthState(state);
                    return state;
                  });
    }
  }

  /** Polls the server for OOB authentication result. */
  public void pollOob() {
    if (currentAuthState instanceof OobPendingContinuation) {
      OobPendingContinuation oob = (OobPendingContinuation) currentAuthState;
      pendingFuture =
          oob.proceedAsync()
              .thenApply(
                  state -> {
                    handleAuthState(state);
                    return state;
                  });
    } else if (currentAuthState instanceof TransferContinuation) {
      TransferContinuation transfer = (TransferContinuation) currentAuthState;
      pendingFuture =
          transfer
              .proceedAsync()
              .thenApply(
                  state -> {
                    handleAuthState(state);
                    return state;
                  });
    }
  }

  /** Switches to recovery mode for password reset. */
  public void switchToRecovery() {
    this.recoveryMode = true;
    setCurrentScreen(CliScreen.USERNAME_INPUT);
  }

  /**
   * Changes the user's password via the MyAccount API.
   *
   * @param accessToken the access token from recovery authentication
   * @param newPassword the new password
   * @return true if password change succeeded
   */
  public boolean changePassword(String accessToken, String newPassword) {
    try {
      CliLogger.info(TAG, "Changing password via MyAccount API");
      URL url = java.net.URI.create(issuer + "/idp/myaccount/password").toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("PUT");
      conn.setRequestProperty("Accept", "application/json; okta-version=1.0.0");
      conn.setRequestProperty("Authorization", "Bearer " + accessToken);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setDoOutput(true);

      String body = "{\"profile\":{\"password\":\"" + escapeJson(newPassword) + "\"}}";
      try (OutputStream os = conn.getOutputStream()) {
        os.write(body.getBytes(StandardCharsets.UTF_8));
      }

      int responseCode = conn.getResponseCode();
      CliLogger.debug(TAG, "Password change response: HTTP " + responseCode);
      conn.disconnect();

      if (responseCode >= 200 && responseCode < 300) {
        CliLogger.info(TAG, "Password changed successfully");
        return true;
      } else {
        String errorBody = readErrorStream(conn);
        CliLogger.debug(TAG, "Password change error body: " + errorBody);
        notifyError("Password change failed (HTTP " + responseCode + "): " + errorBody);
        return false;
      }
    } catch (IOException e) {
      CliLogger.error(TAG, "Password change failed", e);
      notifyError("Password change failed: " + e.getMessage());
      return false;
    }
  }

  /** Resets the ViewModel to its initial state. */
  public void reset() {
    this.username = "";
    this.selectedMethod = null;
    this.currentAuthState = null;
    this.recoveryMode = false;
    this.lastTokenDisplay = null;
    this.bindingCode = null;
    signInFlow.reset();
    if (recoveryFlow != null) {
      recoveryFlow.reset();
    }
    setCurrentScreen(CliScreen.MAIN_MENU);
  }

  /**
   * Waits for the pending future to complete.
   *
   * @return the resulting state, or null if no pending operation
   */
  public DirectAuthenticationState waitForResult() {
    CompletableFuture<DirectAuthenticationState> future = pendingFuture;
    if (future == null) {
      return null;
    }
    try {
      return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
      CliLogger.error(TAG, "Operation timed out or failed", e);
      notifyError("Operation timed out or failed");
      return null;
    }
  }

  @Override
  public void close() {
    signInFlow.close();
    if (recoveryFlow != null) {
      recoveryFlow.close();
    }
  }

  private void handleAuthState(DirectAuthenticationState state) {
    CliLogger.debug(TAG, "Auth state received: " + state.getClass().getSimpleName());
    this.currentAuthState = state;
    notifyAuthStateChanged(state);

    if (state instanceof DirectAuthenticationState.Authenticated) {
      DirectAuthenticationState.Authenticated auth =
          (DirectAuthenticationState.Authenticated) state;
      if (recoveryMode) {
        lastTokenDisplay = TokenDisplay.fromTokenInfo(auth.getToken());
        setCurrentScreen(CliScreen.PASSWORD_CHANGE);
      } else {
        lastTokenDisplay = TokenDisplay.fromTokenInfo(auth.getToken());
        setCurrentScreen(CliScreen.AUTHENTICATED);
      }
    } else if (state instanceof MfaRequired) {
      setCurrentScreen(CliScreen.MFA_REQUIRED);
    } else if (state instanceof PromptContinuation) {
      setCurrentScreen(CliScreen.CODE_ENTRY);
    } else if (state instanceof OobPendingContinuation) {
      setCurrentScreen(CliScreen.OOB_POLLING);
    } else if (state instanceof TransferContinuation) {
      TransferContinuation transfer = (TransferContinuation) state;
      this.bindingCode = transfer.getBindingCode();
      setCurrentScreen(CliScreen.OOB_POLLING);
    } else if (state instanceof WebAuthnContinuation) {
      notifyError("WebAuthn is not supported in the CLI. Please use a different method.");
      setCurrentScreen(CliScreen.ERROR);
    } else if (state instanceof DirectAuthenticationState.Error) {
      setCurrentScreen(CliScreen.ERROR);
    } else if (state instanceof DirectAuthenticationState.Canceled) {
      setCurrentScreen(CliScreen.MAIN_MENU);
    }
  }

  private void setCurrentScreen(CliScreen screen) {
    CliLogger.debug(TAG, "Screen: " + screen);
    this.currentScreen = screen;
    for (AuthViewModelListener listener : listeners) {
      listener.onScreenChanged(screen);
    }
  }

  private void notifyAuthStateChanged(DirectAuthenticationState state) {
    for (AuthViewModelListener listener : listeners) {
      listener.onAuthStateChanged(state);
    }
  }

  private void notifyError(String message) {
    for (AuthViewModelListener listener : listeners) {
      listener.onError(message);
    }
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String readErrorStream(HttpURLConnection conn) {
    try {
      InputStream errorStream = conn.getErrorStream();
      if (errorStream == null) {
        return "";
      }
      byte[] bytes = errorStream.readAllBytes();
      errorStream.close();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }
}
