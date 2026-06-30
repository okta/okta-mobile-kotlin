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

import com.okta.directauth.cli.model.DeviceCodeDisplay;
import com.okta.directauth.cli.model.OAuth2Flow;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.viewmodel.OAuth2ViewModel;
import com.okta.directauth.cli.viewmodel.OAuth2ViewModelListener;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * CLI view for the OAuth2 demonstration mode.
 *
 * <p>Renders the OAuth2 flow menu, collects user input, delegates to {@link OAuth2ViewModel}, and
 * displays token results and error messages. Token rendering reuses the stateless {@link
 * StateRenderer#renderToken(TokenDisplay, boolean)} method. OAuth2 errors are rendered from the
 * flow's exception message since {@link StateRenderer#renderError} is Direct-Auth-specific.
 *
 * <h3>Concurrency contract</h3>
 *
 * Each flow execution creates a private {@link CountDownLatch}(1) that is released when the
 * ViewModel transitions to {@code AUTHENTICATED} or {@code ERROR}. The latch reference is captured
 * into a local variable before the flow starts, so a callback from a prior cancelled flow cannot
 * release the latch belonging to a new flow. {@link #stop()} and {@link OAuth2ViewModel#reset()}
 * both release any active latch to prevent the menu loop from hanging on shutdown.
 */
public final class OAuth2ConsoleView implements OAuth2ViewModelListener {
  private final OAuth2ViewModel viewModel;
  private final ConsoleInput input;
  private final ConsoleOutput output;
  private volatile boolean running = true;

  // Holds the latch for the currently executing flow. Replaced atomically before each flow start.
  private final AtomicReference<CountDownLatch> activeLatch = new AtomicReference<>();
  // Holds the most recent error message for rendering in showError().
  private volatile String lastErrorMessage;

  /**
   * Creates a new OAuth2ConsoleView.
   *
   * @param viewModel the OAuth2 view model
   * @param input the console input source
   * @param output the console output target
   */
  public OAuth2ConsoleView(OAuth2ViewModel viewModel, ConsoleInput input, ConsoleOutput output) {
    this.viewModel = viewModel;
    this.input = input;
    this.output = output;
    viewModel.addListener(this);
  }

  /** Runs the OAuth2 menu loop until the user exits. */
  public void run() {
    while (running) {
      OAuth2Screen screen = viewModel.getCurrentScreen();
      if (screen == OAuth2Screen.MENU) {
        showFlowMenu();
      } else if (screen == OAuth2Screen.AUTHENTICATED) {
        showSuccess();
      } else if (screen == OAuth2Screen.ERROR) {
        showError();
      }
      // DEVICE_POLLING and BROWSER_WAITING are transient: the listener callback releases the latch.
    }
  }

  /**
   * Stops the run loop and releases any waiting latch so the thread can exit cleanly.
   *
   * <p>Safe to call from any thread (e.g. the JVM shutdown hook).
   */
  public void stop() {
    running = false;
    releaseLatch();
  }

  @Override
  public void onScreenChanged(OAuth2Screen screen) {
    if (screen == OAuth2Screen.AUTHENTICATED || screen == OAuth2Screen.ERROR) {
      releaseLatch();
    }
  }

  @Override
  public void onDeviceCode(DeviceCodeDisplay deviceCode) {
    output.println("");
    output.println("=== Device Authorization ===");
    output.println("Visit: " + deviceCode.getVerificationUri());
    if (deviceCode.getVerificationUriComplete() != null) {
      output.println("Or open: " + deviceCode.getVerificationUriComplete());
    }
    output.println("Enter code: " + deviceCode.getUserCode());
    output.println(
        "Code expires in " + deviceCode.getExpiresIn() + " seconds. Waiting for approval...");
  }

  @Override
  public void onResult(TokenDisplay token) {
    // Rendering happens in showSuccess() after the screen transitions to AUTHENTICATED.
  }

  @Override
  public void onError(String message) {
    lastErrorMessage = message;
    // Rendering happens in showError() after the screen transitions to ERROR.
  }

  private void showFlowMenu() {
    List<String> labels =
        Arrays.stream(OAuth2Flow.values()).map(OAuth2Flow::getLabel).collect(Collectors.toList());

    output.println("");
    output.println(StateRenderer.renderMenu("=== OAuth2 Flows ===", labels));
    output.print("[0] Back\nSelect option: ");

    String line = input.readLine();
    if (line == null) {
      running = false;
      return;
    }
    line = line.trim();

    if ("0".equals(line)) {
      running = false;
      return;
    }

    int choice;
    try {
      choice = Integer.parseInt(line) - 1;
    } catch (NumberFormatException e) {
      output.println("Invalid option. Please enter a number.");
      return;
    }

    OAuth2Flow[] flows = OAuth2Flow.values();
    if (choice < 0 || choice >= flows.length) {
      output.println("Invalid option. Please enter a number between 1 and " + flows.length + ".");
      return;
    }

    executeFlow(flows[choice]);
  }

  private void executeFlow(OAuth2Flow flow) {
    // Create a fresh latch for this invocation and register it atomically BEFORE starting the
    // flow. This ensures no callback from a prior invocation can release the new latch.
    CountDownLatch latch = new CountDownLatch(1);
    activeLatch.set(latch);

    boolean started = startFlow(flow);
    if (!started) {
      // Input validation failed; latch not needed.
      activeLatch.compareAndSet(latch, null);
      return;
    }

    // Block until onScreenChanged delivers AUTHENTICATED or ERROR, or stop() is called.
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Collects required inputs for {@code flow} and starts it via the ViewModel.
   *
   * @return true if the flow was started, false if input validation aborted early
   */
  private boolean startFlow(OAuth2Flow flow) {
    if (flow.requiresCredentials()) {
      String username = input.readLine("Username: ");
      if (username == null || username.trim().isEmpty()) {
        output.println("Username cannot be empty.");
        return false;
      }
      // readPassword masks the input so the credential is not echoed to the terminal.
      String password = input.readPassword("Password: ");
      if (password == null || password.isEmpty()) {
        output.println("Password cannot be empty.");
        return false;
      }
      viewModel.startResourceOwner(username.trim(), password);
      return true;
    }

    if (flow.requiresTokens()) {
      String idToken = input.readLine("ID Token: ");
      if (idToken == null || idToken.trim().isEmpty()) {
        output.println("ID token cannot be empty.");
        return false;
      }
      // Device secrets are bearer credentials; read without echo.
      String deviceSecret = input.readPassword("Device Secret: ");
      if (deviceSecret == null || deviceSecret.trim().isEmpty()) {
        output.println("Device secret cannot be empty.");
        return false;
      }
      viewModel.startTokenExchange(idToken.trim(), deviceSecret.trim());
      return true;
    }

    if (flow.requiresSessionToken()) {
      // Session tokens are bearer credentials; read without echo.
      String sessionToken = input.readPassword("Session Token: ");
      if (sessionToken == null || sessionToken.trim().isEmpty()) {
        output.println("Session token cannot be empty.");
        return false;
      }
      viewModel.startSessionToken(sessionToken.trim());
      return true;
    }

    // Flows that need no user input before starting:
    switch (flow) {
      case DEVICE_AUTHORIZATION:
        viewModel.startDeviceAuthorization();
        return true;
      case BROWSER_SIGN_IN:
        output.println("Opening browser for sign-in. Waiting for redirect...");
        viewModel.startBrowserSignIn();
        return true;
      default:
        output.println("Unknown flow: " + flow);
        return false;
    }
  }

  private void showSuccess() {
    TokenDisplay token = viewModel.getLastTokenDisplay();
    if (token != null) {
      output.println(StateRenderer.renderToken(token, viewModel.isDecoded()));
    }
    output.print("Press Enter to continue...");
    input.readLine();
    viewModel.reset();
  }

  private void showError() {
    output.println("");
    output.println("=== OAuth2 Error ===");
    String message = lastErrorMessage;
    if (message != null && !message.isBlank()) {
      output.println("Error: " + message);
    } else {
      output.println("An unknown error occurred.");
    }
    output.print("Press Enter to return to the menu...");
    input.readLine();
    lastErrorMessage = null;
    viewModel.reset();
  }

  private void releaseLatch() {
    CountDownLatch latch = activeLatch.getAndSet(null);
    if (latch != null) {
      latch.countDown();
    }
  }
}
