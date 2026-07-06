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
package com.okta.directauth.cli;

import com.okta.authfoundation.client.jvm.AuthFoundationResult;
import com.okta.authfoundation.client.jvm.OAuth2ClientBuilder;
import com.okta.authfoundation.client.kmp.OAuth2Client;
import com.okta.directauth.cli.model.CliPreferences;
import com.okta.directauth.cli.oauth2.OAuth2Flows;
import com.okta.directauth.cli.oauth2.WrapperOAuth2Flows;
import com.okta.directauth.cli.view.ConsoleView;
import com.okta.directauth.cli.view.OAuth2ConsoleView;
import com.okta.directauth.cli.view.SystemConsoleInput;
import com.okta.directauth.cli.view.SystemConsoleOutput;
import com.okta.directauth.cli.viewmodel.AuthViewModel;
import com.okta.directauth.cli.viewmodel.OAuth2ViewModel;
import com.okta.directauth.jvm.DirectAuthResult;
import com.okta.directauth.jvm.DirectAuthenticationFlow;
import com.okta.directauth.jvm.DirectAuthenticationFlowBuilder;
import com.okta.directauth.model.DirectAuthenticationIntent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Entry point for the Okta Auth CLI sample demonstrating Direct Authentication and OAuth2 flows.
 */
@Command(
    name = "okta-direct-auth-cli",
    version = "okta-direct-auth-cli 1.0.0",
    description =
        "Interactive CLI demonstrating Okta Direct Authentication and OAuth2 authorization flows.",
    mixinStandardHelpOptions = true)
public final class Main implements Callable<Integer> {
  private static final String TAG = "Main";
  private static final String VERSION = "1.0.0";
  private static final String CONFIG_DOCS_URL =
      "https://developer.okta.com/docs/guides/configure-direct-auth-grants/";
  private static final List<String> OAUTH2_SCOPES =
      Arrays.asList("openid", "profile", "email", "offline_access");

  @Option(names = "--issuer", description = "Okta issuer URL")
  private String issuerArg;

  @Option(names = "--clientId", description = "OAuth 2.0 client ID")
  private String clientIdArg;

  @Option(
      names = "--authorizationServerId",
      description = "Authorization server ID (e.g., 'default')")
  private String authorizationServerIdArg;

  @Option(
      names = "--desktopSignInRedirectUri",
      description =
          "Loopback redirect URI for OAuth2 redirect-based flows (default:"
              + " http://localhost:8080/callback)")
  private String signInRedirectUriArg;

  @Option(
      names = "--format",
      description = "Token display format: ${COMPLETION-CANDIDATES} (default: raw)",
      defaultValue = "raw")
  private TokenFormat format;

  @Option(names = "--mode", description = "Pre-select demonstration mode: ${COMPLETION-CANDIDATES}")
  private DemoMode modeArg;

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable debug logging to stderr")
  private boolean verbose;

  private enum TokenFormat {
    raw,
    decoded
  }

  private enum DemoMode {
    direct,
    oauth2
  }

  @Override
  public Integer call() {
    if (verbose) {
      CliLogger.setVerbose(true);
    }

    CliLogger.info(TAG, "Starting Okta Auth CLI v" + VERSION);
    CliLogger.debug(TAG, "Java " + System.getProperty("java.version"));
    CliLogger.debug(
        TAG, "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
    CliLogger.debug(TAG, "Token format: " + format);

    String issuer = resolveConfig(issuerArg, AppConfig.ISSUER, "Issuer URL");
    String clientId = resolveConfig(clientIdArg, AppConfig.CLIENT_ID, "Client ID");
    String authorizationServerId =
        resolveConfig(
            authorizationServerIdArg,
            AppConfig.AUTHORIZATION_SERVER_ID,
            "Authorization Server ID (e.g., 'default')");
    // JVM: prefer desktopSignInRedirectUri, fall back to signInRedirectUri.
    String defaultRedirectUri =
        !AppConfig.DESKTOP_SIGN_IN_REDIRECT_URI.isEmpty()
            ? AppConfig.DESKTOP_SIGN_IN_REDIRECT_URI
            : AppConfig.SIGN_IN_REDIRECT_URI;
    String signInRedirectUri =
        resolveConfig(
            signInRedirectUriArg,
            defaultRedirectUri,
            "Desktop Sign-In Redirect URI (OAuth2 redirect-based flows, e.g. http://localhost:8080/callback)");

    if (issuer.isEmpty() || clientId.isEmpty() || authorizationServerId.isEmpty()) {
      System.err.println("=== Configuration Error ===");
      System.err.println("Missing required configuration values.");
      System.err.println("See: " + CONFIG_DOCS_URL);
      return 1;
    }

    CliLogger.debug(TAG, "Issuer: " + issuer);
    CliLogger.debug(TAG, "Client ID: " + clientId);
    CliLogger.debug(TAG, "Authorization Server ID: " + authorizationServerId);
    CliLogger.debug(TAG, "Sign-In Redirect URI: " + signInRedirectUri);

    // --- Build Direct Auth flows ---
    List<String> signInScopes = Arrays.asList("openid", "profile", "email");
    List<String> recoveryScopes = List.of("okta.myAccount.password.manage");

    CliLogger.info(TAG, "Building Direct Auth sign-in flow (scopes: " + signInScopes + ")");
    DirectAuthResult<DirectAuthenticationFlow> signInResult =
        new DirectAuthenticationFlowBuilder(issuer, clientId, signInScopes)
            .setAuthorizationServerId(authorizationServerId)
            .setIntent(DirectAuthenticationIntent.SIGN_IN)
            .build();

    CliLogger.info(TAG, "Building Direct Auth recovery flow (scopes: " + recoveryScopes + ")");
    DirectAuthResult<DirectAuthenticationFlow> recoveryResult =
        new DirectAuthenticationFlowBuilder(issuer, clientId, recoveryScopes)
            .setAuthorizationServerId(authorizationServerId)
            .setIntent(DirectAuthenticationIntent.RECOVERY)
            .build();

    if (signInResult.isFailure()) {
      Throwable cause = signInResult.exceptionOrNull();
      CliLogger.error(TAG, "Failed to create sign-in flow", cause);
      System.err.println("Failed to create sign-in flow: " + cause);
      System.err.println("See: " + CONFIG_DOCS_URL);
      return 1;
    }

    DirectAuthenticationFlow signInFlow = signInResult.getOrThrow();
    DirectAuthenticationFlow recoveryFlow =
        recoveryResult.isSuccess() ? recoveryResult.getOrThrow() : null;
    if (recoveryFlow == null) {
      CliLogger.info(TAG, "Recovery flow unavailable (password recovery disabled)");
    }

    // --- Build OAuth2 client and flows ---
    CliLogger.info(TAG, "Building OAuth2 client (scopes: " + OAUTH2_SCOPES + ")");
    AuthFoundationResult<OAuth2Client> oauth2ClientResult =
        new OAuth2ClientBuilder(issuer, clientId, OAUTH2_SCOPES)
            .setAuthorizationServerId(authorizationServerId)
            .build();

    if (oauth2ClientResult.isFailure()) {
      Throwable cause = oauth2ClientResult.exceptionOrNull();
      CliLogger.error(TAG, "Failed to create OAuth2 client", cause);
      System.err.println("Failed to create OAuth2 client: " + cause);
      return 1;
    }

    OAuth2Client oauth2Client = oauth2ClientResult.getOrThrow();
    OAuth2Flows oauth2Flows;
    try {
      oauth2Flows = new WrapperOAuth2Flows(oauth2Client, OAUTH2_SCOPES, signInRedirectUri);
    } catch (IllegalArgumentException e) {
      System.err.println("=== Configuration Error ===");
      System.err.println("Invalid desktopSignInRedirectUri: " + e.getMessage());
      System.err.println(
          "Use --desktopSignInRedirectUri or set desktopSignInRedirectUri in local.properties.");
      return 1;
    }

    // --- Wire views and view models ---
    CliPreferences preferences = new CliPreferences();
    CliLogger.debug(
        TAG,
        "Preferences loaded (last username: "
            + (preferences.getLastUsername().isEmpty() ? "<none>" : preferences.getLastUsername())
            + ")");

    boolean decoded = format == TokenFormat.decoded;

    AuthViewModel directAuthViewModel = new AuthViewModel(signInFlow, recoveryFlow, issuer);
    ConsoleView directAuthView =
        new ConsoleView(
            directAuthViewModel,
            new SystemConsoleInput(),
            new SystemConsoleOutput(),
            decoded,
            preferences);

    OAuth2ViewModel oauth2ViewModel = new OAuth2ViewModel(oauth2Flows, decoded);
    OAuth2ConsoleView oauth2View =
        new OAuth2ConsoleView(oauth2ViewModel, new SystemConsoleInput(), new SystemConsoleOutput());

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  CliLogger.debug(TAG, "Shutdown hook triggered");
                  directAuthView.stop();
                  directAuthViewModel.close();
                  oauth2View.stop();
                  oauth2ViewModel.close();
                }));

    // --- Top-level mode selection ---
    DemoMode mode = modeArg;
    if (mode == null) {
      mode = promptMode();
    }

    CliLogger.info(TAG, "Mode selected: " + mode);

    try {
      if (mode == DemoMode.direct) {
        CliLogger.info(TAG, "Entering Direct Authentication mode.");
        directAuthView.run();
      } else {
        CliLogger.info(TAG, "Entering OAuth2 Flows mode.");
        oauth2View.run();
      }
    } finally {
      CliLogger.info(TAG, "Exiting.");
      directAuthViewModel.close();
      oauth2ViewModel.close();
    }
    return 0;
  }

  private static DemoMode promptMode() {
    System.out.println("\n=== Okta Auth CLI ===");
    System.out.println("[1] Direct Authentication");
    System.out.println("[2] OAuth2 Flows");
    System.out.println("[3] Exit");
    System.out.print("Select option: ");
    if (System.console() != null) {
      String line = System.console().readLine();
      if (line != null) {
        switch (line.trim()) {
          case "1":
            return DemoMode.direct;
          case "2":
            return DemoMode.oauth2;
          case "3":
            System.exit(0);
        }
      }
    }
    // Default to direct auth if input unavailable
    return DemoMode.direct;
  }

  /**
   * Resolves a configuration value from: (1) CLI arg, (2) local.properties default, (3) interactive
   * prompt.
   */
  private static String resolveConfig(String argValue, String defaultValue, String promptLabel) {
    if (argValue != null && !argValue.isEmpty()) {
      CliLogger.debug("Main", "Config '" + promptLabel + "' from CLI arg");
      return argValue;
    }
    if (!defaultValue.isEmpty()) {
      CliLogger.debug("Main", "Config '" + promptLabel + "' from local.properties");
      return defaultValue;
    }
    if (System.console() == null) {
      CliLogger.error(
          "Main", "Config '" + promptLabel + "' is required but stdin is not available");
      return "";
    }
    CliLogger.debug("Main", "Config '" + promptLabel + "' prompting user");
    System.out.print(promptLabel + ": ");
    String line = System.console().readLine();
    return (line != null) ? line.trim() : "";
  }

  /**
   * Main entry point.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }
}
