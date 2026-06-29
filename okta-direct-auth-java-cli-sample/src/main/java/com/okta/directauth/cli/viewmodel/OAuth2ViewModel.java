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

import com.okta.authfoundation.client.TokenInfo;
import com.okta.directauth.cli.CliLogger;
import com.okta.directauth.cli.model.DeviceCodeDisplay;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.oauth2.OAuth2Flows;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestration ViewModel for the OAuth2 demonstration flows.
 *
 * <p>Delegates to the {@link OAuth2Flows} seam (production: {@link
 * com.okta.directauth.cli.oauth2.WrapperOAuth2Flows}, tests: a fake) and notifies registered {@link
 * OAuth2ViewModelListener}s of state changes. All flow methods are non-blocking; results arrive via
 * listener callbacks.
 */
public final class OAuth2ViewModel implements Closeable {
  private static final String TAG = "OAuth2ViewModel";

  private final OAuth2Flows flows;
  private final boolean decoded;
  private final List<OAuth2ViewModelListener> listeners = new CopyOnWriteArrayList<>();

  private volatile OAuth2Screen currentScreen = OAuth2Screen.MENU;
  private volatile TokenDisplay lastTokenDisplay;
  private volatile CompletableFuture<?> pendingFuture;

  /**
   * Creates a new OAuth2ViewModel.
   *
   * @param flows the OAuth2 flow seam to use
   * @param decoded true to display decoded JWT claims, false for raw tokens
   */
  public OAuth2ViewModel(OAuth2Flows flows, boolean decoded) {
    this.flows = flows;
    this.decoded = decoded;
  }

  /**
   * Adds a listener for state change notifications.
   *
   * @param listener the listener to add
   */
  public void addListener(OAuth2ViewModelListener listener) {
    listeners.add(listener);
  }

  /**
   * Returns the current OAuth2 navigation screen.
   *
   * @return current screen
   */
  public OAuth2Screen getCurrentScreen() {
    return currentScreen;
  }

  /**
   * Returns the token display from the last successful flow, or null.
   *
   * @return last token display, or null
   */
  public TokenDisplay getLastTokenDisplay() {
    return lastTokenDisplay;
  }

  /**
   * Returns whether decoded JWT display mode is active.
   *
   * @return true if decoded mode
   */
  public boolean isDecoded() {
    return decoded;
  }

  /**
   * Starts the Resource Owner Password flow (non-blocking).
   *
   * @param username the user's username
   * @param password the user's password
   */
  public void startResourceOwner(String username, String password) {
    if (username == null || username.trim().isEmpty()) {
      notifyError("Username cannot be empty");
      return;
    }
    if (password == null || password.isEmpty()) {
      notifyError("Password cannot be empty");
      return;
    }
    CliLogger.info(TAG, "Starting Resource Owner flow for: " + username);
    pendingFuture =
        flows
            .resourceOwner(username, password)
            .thenAccept(this::handleSuccess)
            .exceptionally(
                t -> {
                  handleError("Resource Owner flow failed", t);
                  return null;
                });
  }

  /**
   * Starts the Device Authorization flow (non-blocking).
   *
   * <p>On success, fires {@link OAuth2ViewModelListener#onDeviceCode(DeviceCodeDisplay)} with the
   * code details, then polls for approval.
   */
  public void startDeviceAuthorization() {
    CliLogger.info(TAG, "Starting Device Authorization flow");
    setScreen(OAuth2Screen.DEVICE_POLLING);
    pendingFuture =
        flows
            .deviceStart()
            .thenCompose(
                context -> {
                  DeviceCodeDisplay display = DeviceCodeDisplay.fromContext(context);
                  CliLogger.info(TAG, "Device code received: " + context.getUserCode());
                  notifyDeviceCode(display);
                  return flows.deviceResume(context);
                })
            .thenAccept(this::handleSuccess)
            .exceptionally(
                t -> {
                  handleError("Device Authorization flow failed", t);
                  return null;
                });
  }

  /**
   * Starts the Browser Sign-In (Authorization Code + PKCE) flow (non-blocking).
   *
   * <p>Opens the system browser and waits for the loopback redirect.
   */
  public void startBrowserSignIn() {
    CliLogger.info(TAG, "Starting Browser Sign-In flow");
    setScreen(OAuth2Screen.BROWSER_WAITING);
    pendingFuture =
        flows
            .browserSignIn()
            .thenAccept(this::handleSuccess)
            .exceptionally(
                t -> {
                  handleError("Browser Sign-In flow failed", t);
                  return null;
                });
  }

  /**
   * Starts the Token Exchange flow (non-blocking).
   *
   * @param idToken an existing ID token
   * @param deviceSecret an existing device secret
   */
  public void startTokenExchange(String idToken, String deviceSecret) {
    if (idToken == null || idToken.trim().isEmpty()) {
      notifyError("ID token cannot be empty");
      return;
    }
    if (deviceSecret == null || deviceSecret.trim().isEmpty()) {
      notifyError("Device secret cannot be empty");
      return;
    }
    CliLogger.info(TAG, "Starting Token Exchange flow");
    pendingFuture =
        flows
            .tokenExchange(idToken.trim(), deviceSecret.trim())
            .thenAccept(this::handleSuccess)
            .exceptionally(
                t -> {
                  handleError("Token Exchange flow failed", t);
                  return null;
                });
  }

  /**
   * Starts the Session Token flow (non-blocking).
   *
   * @param sessionToken a session token from the Okta Authn API
   */
  public void startSessionToken(String sessionToken) {
    if (sessionToken == null || sessionToken.trim().isEmpty()) {
      notifyError("Session token cannot be empty");
      return;
    }
    CliLogger.info(TAG, "Starting Session Token flow");
    pendingFuture =
        flows
            .sessionToken(sessionToken.trim())
            .thenAccept(this::handleSuccess)
            .exceptionally(
                t -> {
                  handleError("Session Token flow failed", t);
                  return null;
                });
  }

  /** Cancels any pending flow and returns to the OAuth2 menu. */
  public void reset() {
    CompletableFuture<?> pending = pendingFuture;
    if (pending != null) {
      pending.cancel(true);
    }
    pendingFuture = null;
    lastTokenDisplay = null;
    setScreen(OAuth2Screen.MENU);
  }

  @Override
  public void close() {
    reset();
    flows.close();
  }

  private void handleSuccess(TokenInfo tokenInfo) {
    CliLogger.info(TAG, "Flow succeeded");
    lastTokenDisplay = TokenDisplay.fromTokenInfo(tokenInfo);
    setScreen(OAuth2Screen.AUTHENTICATED);
    for (OAuth2ViewModelListener listener : listeners) {
      listener.onResult(lastTokenDisplay);
    }
  }

  private void handleError(String context, Throwable t) {
    // Unwrap CompletableFuture wrappers to get the root cause.
    Throwable cause = t;
    while (cause.getCause() != null && cause != cause.getCause()) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      // Provide an actionable fallback rather than an opaque class name.
      message =
          "Unexpected error ("
              + cause.getClass().getSimpleName()
              + "). Check --verbose for details.";
    }
    CliLogger.error(TAG, context + ": " + message, cause);
    // notifyError is the single source of truth for the ERROR transition.
    notifyError(message);
  }

  private void setScreen(OAuth2Screen screen) {
    CliLogger.debug(TAG, "Screen: " + screen);
    this.currentScreen = screen;
    for (OAuth2ViewModelListener listener : listeners) {
      listener.onScreenChanged(screen);
    }
  }

  private void notifyDeviceCode(DeviceCodeDisplay deviceCode) {
    for (OAuth2ViewModelListener listener : listeners) {
      listener.onDeviceCode(deviceCode);
    }
  }

  /**
   * Single source of truth for the ERROR screen transition and error callbacks.
   *
   * <p>Callers must NOT call {@link #setScreen(OAuth2Screen)} with {@code ERROR} separately.
   */
  private void notifyError(String message) {
    setScreen(OAuth2Screen.ERROR);
    for (OAuth2ViewModelListener listener : listeners) {
      listener.onError(message);
    }
  }
}
