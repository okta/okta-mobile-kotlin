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

import com.okta.directauth.cli.model.CrossAppAccessConfig;
import com.okta.directauth.cli.model.DeviceCodeDisplay;
import com.okta.directauth.cli.model.IdJagDisplay;
import com.okta.directauth.cli.model.OAuth2Flow;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.SubjectKind;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.viewmodel.CrossAppAccessViewModel;
import com.okta.directauth.cli.viewmodel.CrossAppAccessViewModelListener;
import com.okta.directauth.cli.viewmodel.OAuth2ViewModel;
import com.okta.directauth.cli.viewmodel.OAuth2ViewModelListener;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * CLI view for the OAuth2 demonstration mode, including Cross App Access.
 *
 * <p>Renders the OAuth2 flow menu, collects user input, delegates to {@link OAuth2ViewModel} (the
 * five standard grants) and {@link CrossAppAccessViewModel} (Cross App Access), and displays token
 * results and error messages. Token rendering reuses the stateless {@link
 * StateRenderer#renderToken(TokenDisplay, boolean)} method. OAuth2 errors are rendered from the
 * flow's exception message since {@link StateRenderer#renderError} is Direct-Auth-specific.
 *
 * <p>{@code onScreenChanged(OAuth2Screen)} and {@code onError(String)} are declared identically by
 * both {@link OAuth2ViewModelListener} and {@link CrossAppAccessViewModelListener} — both view
 * models share the same {@link OAuth2Screen} enum — so one override below satisfies both.
 *
 * <h3>Concurrency contract</h3>
 *
 * Each flow execution creates a private {@link CountDownLatch}(1) that is released when either
 * ViewModel transitions to a settled screen ({@code AUTHENTICATED}/{@code ERROR}, or the Cross App
 * Access equivalents). The latch reference is captured into a local variable before the flow
 * starts, so a callback from a prior cancelled flow cannot release the latch belonging to a new
 * flow. Cross App Access's step-by-step mode uses one latch cycle per step, since a step needs the
 * prompt to return control in between; {@link #stop()} and both ViewModels' {@code reset()} release
 * any active latch to prevent the menu loop from hanging on shutdown.
 */
public final class OAuth2ConsoleView
    implements OAuth2ViewModelListener, CrossAppAccessViewModelListener {
  private final OAuth2ViewModel viewModel;
  private final CrossAppAccessViewModel crossAppAccessViewModel;
  private final ConsoleInput input;
  private final ConsoleOutput output;
  private volatile boolean running = true;

  // Holds the latch for the currently executing flow. Replaced atomically before each flow start.
  // Shared between the two view models: only one of them is ever mid-flow at a time, since the
  // menu loop dispatches to exactly one flow (or Cross App Access) per selection.
  private final AtomicReference<CountDownLatch> activeLatch = new AtomicReference<>();
  // Holds the most recent error message for rendering in showError()/showCrossAppAccessError().
  private volatile String lastErrorMessage;

  /**
   * Creates a new OAuth2ConsoleView.
   *
   * @param viewModel the OAuth2 view model
   * @param crossAppAccessViewModel the Cross App Access view model
   * @param input the console input source
   * @param output the console output target
   */
  public OAuth2ConsoleView(
      OAuth2ViewModel viewModel,
      CrossAppAccessViewModel crossAppAccessViewModel,
      ConsoleInput input,
      ConsoleOutput output) {
    this.viewModel = viewModel;
    this.crossAppAccessViewModel = crossAppAccessViewModel;
    this.input = input;
    this.output = output;
    viewModel.addListener(this);
    crossAppAccessViewModel.addListener(this);
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
    // MENU/CROSS_APP_ACCESS_MENU are included so both ViewModels' reset() truly is
    // self-sufficient, per this class's own concurrency contract above, rather than depending on
    // stop() having already run first (e.g. the shutdown hook's ordering).
    if (screen == OAuth2Screen.MENU
        || screen == OAuth2Screen.AUTHENTICATED
        || screen == OAuth2Screen.ERROR
        || screen == OAuth2Screen.CROSS_APP_ACCESS_MENU
        || screen == OAuth2Screen.CROSS_APP_ACCESS_SIGNED_IN
        || screen == OAuth2Screen.CROSS_APP_ACCESS_ID_JAG
        || screen == OAuth2Screen.CROSS_APP_ACCESS_RESULT
        || screen == OAuth2Screen.CROSS_APP_ACCESS_ERROR) {
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
  public void onIdJag(IdJagDisplay idJag) {
    // Rendering happens in runCrossAppAccessStepByStep() after the screen transitions.
  }

  @Override
  public void onResourceToken(TokenDisplay token) {
    // Rendering happens after the screen transitions, alongside the ID-JAG (step-by-step) or
    // alone (one-action) — see runCrossAppAccessOneAction()/runCrossAppAccessStepByStep().
  }

  @Override
  public void onIntrospection(Boolean active) {
    // Rendering happens after the screen transitions, in runIntrospectPrompt()/
    // runCrossAppAccessStepByStep(), which read the verdict back from the ViewModel directly.
  }

  @Override
  public void onError(String message) {
    lastErrorMessage = message;
    // Rendering happens in showError()/showCrossAppAccessError() after the screen transitions.
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

    OAuth2Flow selected = flows[choice];
    if (selected == OAuth2Flow.CROSS_APP_ACCESS) {
      // Cross App Access is a multi-step interactive flow (subject selection, mode choice, an
      // optional redeem-again loop) that doesn't fit executeFlow()'s single-latch-await shape —
      // it manages its own latch cycles instead, one per step.
      runCrossAppAccessFlow();
    } else {
      executeFlow(selected);
    }
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
        output.println(
            "Opening browser for sign-in (Auth Code + PKCE; PAR when supported). Waiting for redirect...");
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

  /**
   * Runs the Cross App Access console flow: configuration check, subject selection, mode choice,
   * and either the one-action exchange or the step-by-step mode with its redeem-again loop.
   */
  private void runCrossAppAccessFlow() {
    CrossAppAccessConfig.Validation validation = crossAppAccessViewModel.validateConfig();
    if (!(validation instanceof CrossAppAccessConfig.Validation.Complete)) {
      output.println("");
      output.println(StateRenderer.renderCrossAppAccessNotConfigured(validation));
      output.print("Press Enter to return to the menu...");
      input.readLine();
      return;
    }

    output.println("");
    output.println(StateRenderer.renderCrossAppAccessTarget(crossAppAccessViewModel.getConfig()));

    if (!crossAppAccessViewModel.hasSession()) {
      output.println("");
      output.println("Cross App Access needs its own signed-in session, separate from the rest");
      output.println("of this CLI — sign in below.");
      output.print("Opening browser for sign-in. Waiting for redirect...");
      output.println("");
      if (!runCrossAppAccessAction(crossAppAccessViewModel::signIn)) {
        return;
      }
    }

    SubjectKind kind = promptSubjectKind();
    crossAppAccessViewModel.selectSubjectKind(kind);

    crossAppAccessViewModel.selectScope(promptScope());

    output.print(
        "[1] One-action exchange\n[2] Step-by-step (inspect the ID-JAG, redeem separately)\n[0]"
            + " Back\nSelect option: ");
    String mode = input.readLine();
    String trimmedMode = mode == null ? "0" : mode.trim();

    if ("2".equals(trimmedMode)) {
      runCrossAppAccessStepByStep();
    } else if ("1".equals(trimmedMode)) {
      runCrossAppAccessOneAction();
    } else {
      crossAppAccessViewModel.reset();
    }
  }

  /**
   * Prompts for which subject kind to present, defaulting to {@link SubjectKind#IDENTITY} — the
   * only kind universally accepted; the other two depend on the org's own configuration. A kind not
   * present in the current session cannot be selected.
   */
  private SubjectKind promptSubjectKind() {
    output.println("");
    output.println("=== Subject ===");
    output.println(
        "Only the identity token is universally accepted; access and refresh tokens depend on"
            + " your org's own configuration.");
    SubjectKind[] kinds = SubjectKind.values();
    for (int i = 0; i < kinds.length; i++) {
      // Called only after signIn() has succeeded, so a session is always present here.
      boolean available = crossAppAccessViewModel.isKindAvailable(kinds[i]);
      String suffix = available ? "" : " (unavailable — not present in this session)";
      output.println("[" + (i + 1) + "] " + kinds[i] + suffix);
    }
    output.print("Select subject kind (default 1 - IDENTITY): ");
    String line = input.readLine();
    if (line == null || line.trim().isEmpty()) {
      return SubjectKind.IDENTITY;
    }
    try {
      int choice = Integer.parseInt(line.trim()) - 1;
      if (choice >= 0
          && choice < kinds.length
          && crossAppAccessViewModel.isKindAvailable(kinds[choice])) {
        return kinds[choice];
      }
      if (choice >= 0 && choice < kinds.length) {
        output.println(
            "The "
                + kinds[choice]
                + " token is not present in this session; defaulting to"
                + " IDENTITY.");
        return SubjectKind.IDENTITY;
      }
    } catch (NumberFormatException ignored) {
      // Falls through to the default below.
    }
    output.println("Invalid option; defaulting to IDENTITY.");
    return SubjectKind.IDENTITY;
  }

  /**
   * Prompts for the requested scope, taking precedence over any scope configured on the target
   * itself. Must be a custom, resource-specific scope defined on the target's authorization server
   * (e.g. {@code chat.read}) — not a standard OIDC scope like {@code openid}/{@code profile}, which
   * the org authorization server rejects here with "The following scopes are not allowed for this
   * request", since this scope is requested at the ID-JAG exchange itself, for the target resource,
   * not for the identity provider. Prefilled with {@code chat.read} for sample testing purposes —
   * used as-is if the user enters nothing.
   */
  private List<String> promptScope() {
    output.println("");
    output.println("=== Scope ===");
    output.print("Requested scopes (space-separated) [chat.read]: ");
    String line = input.readLine();
    if (line == null || line.trim().isEmpty()) {
      return List.of("chat.read");
    }
    return Arrays.stream(line.trim().split("\\s+"))
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  private void runCrossAppAccessOneAction() {
    if (!runCrossAppAccessAction(crossAppAccessViewModel::exchange)) {
      return;
    }
    runIntrospectPrompt();
  }

  /**
   * After a resource token has been obtained (one-action mode), offers to introspect it before
   * returning to the menu — proves the token is actually accepted server-side, which the exchange
   * alone does not.
   */
  private void runIntrospectPrompt() {
    while (true) {
      output.println("");
      output.println(
          StateRenderer.renderCrossAppAccessResult(crossAppAccessViewModel.getLastTokenDisplay()));
      Boolean active = crossAppAccessViewModel.getIntrospectionActive();
      if (active != null) {
        output.println("Active: " + active);
      }
      output.print(
          (active == null ? "[1] Introspect this token" : "[1] Introspect again")
              + "\n[0] Back to menu\nSelect option: ");
      String line = input.readLine();
      if (!"1".equals(line == null ? "" : line.trim())) {
        crossAppAccessViewModel.reset();
        return;
      }
      if (!runCrossAppAccessAction(crossAppAccessViewModel::introspect)) {
        return;
      }
    }
  }

  private void runCrossAppAccessStepByStep() {
    if (!runCrossAppAccessAction(crossAppAccessViewModel::start)) {
      return;
    }

    while (true) {
      TokenDisplay resourceToken = crossAppAccessViewModel.getLastTokenDisplay();
      if (resourceToken != null) {
        output.println(StateRenderer.renderCrossAppAccessResult(resourceToken));
        Boolean active = crossAppAccessViewModel.getIntrospectionActive();
        if (active != null) {
          output.println("Active: " + active);
        }
      }
      output.println(StateRenderer.renderIdJag(crossAppAccessViewModel.getLastIdJagDisplay()));
      output.print(
          (resourceToken == null ? "[1] Redeem" : "[1] Redeem again")
              + (resourceToken != null ? "\n[2] Introspect this token" : "")
              + "\n[0] Back to menu\nSelect option: ");
      String line = input.readLine();
      String trimmed = line == null ? "" : line.trim();
      if ("1".equals(trimmed)) {
        if (!runCrossAppAccessAction(crossAppAccessViewModel::redeem)) {
          return;
        }
      } else if ("2".equals(trimmed) && resourceToken != null) {
        if (!runCrossAppAccessAction(crossAppAccessViewModel::introspect)) {
          return;
        }
      } else {
        crossAppAccessViewModel.reset();
        return;
      }
    }
  }

  /** The outcome of one {@link #awaitCrossAppAccessAction} latch cycle. */
  private enum ActionOutcome {
    SUCCEEDED,
    FAILED,
    /** {@link #stop()} released the latch before the ViewModel settled; {@link #running} is now false. */
    STOPPED
  }

  /**
   * Runs {@code action} through one latch cycle, blocking until it settles.
   *
   * @return {@link ActionOutcome#STOPPED} if {@link #stop()} released the latch instead of the
   *     action settling — checked first, since the current screen at that point is whatever it
   *     was before the action ran and would otherwise be misread as success; {@link
   *     ActionOutcome#FAILED} if the action ended in {@link OAuth2Screen#CROSS_APP_ACCESS_ERROR};
   *     {@link ActionOutcome#SUCCEEDED} otherwise
   */
  private ActionOutcome awaitCrossAppAccessAction(Runnable action) {
    CountDownLatch latch = new CountDownLatch(1);
    activeLatch.set(latch);
    action.run();
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!running) {
      return ActionOutcome.STOPPED;
    }
    return crossAppAccessViewModel.getCurrentScreen() == OAuth2Screen.CROSS_APP_ACCESS_ERROR
        ? ActionOutcome.FAILED
        : ActionOutcome.SUCCEEDED;
  }

  /**
   * Runs {@code action} through one latch cycle and handles the {@link ActionOutcome#FAILED} case
   * (shows the Cross App Access error screen) so call sites only need to branch on whether they
   * should keep going.
   *
   * @return true if {@code action} succeeded and the caller should proceed with its next prompt;
   *     false if the caller must return immediately — either the error screen was already shown
   *     ({@link ActionOutcome#FAILED}), or the CLI is shutting down ({@link
   *     ActionOutcome#STOPPED}, where showing anything or reading another prompt would hang)
   */
  private boolean runCrossAppAccessAction(Runnable action) {
    ActionOutcome outcome = awaitCrossAppAccessAction(action);
    if (outcome == ActionOutcome.FAILED) {
      showCrossAppAccessError();
    }
    return outcome == ActionOutcome.SUCCEEDED;
  }

  /**
   * Displays the Cross App Access error screen and returns to the menu.
   *
   * <p>Only called from {@link #runCrossAppAccessAction} on {@link ActionOutcome#FAILED}, which
   * happens only when the current screen is already {@link OAuth2Screen#CROSS_APP_ACCESS_ERROR} —
   * there is no success case to render here, and {@link ActionOutcome#STOPPED} deliberately skips
   * this (see {@link #awaitCrossAppAccessAction}).
   */
  private void showCrossAppAccessError() {
    output.println("");
    output.println("=== Cross App Access Error ===");
    String message = lastErrorMessage;
    output.println("Error: " + (message == null || message.isBlank() ? "Unknown error" : message));
    lastErrorMessage = null;
    output.print("Press Enter to return to the menu...");
    input.readLine();
    crossAppAccessViewModel.reset();
  }

  private void releaseLatch() {
    CountDownLatch latch = activeLatch.getAndSet(null);
    if (latch != null) {
      latch.countDown();
    }
  }
}
