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

import com.okta.directauth.cli.model.CliPreferences;
import com.okta.directauth.cli.view.ConsoleView;
import com.okta.directauth.cli.view.SystemConsoleInput;
import com.okta.directauth.cli.view.SystemConsoleOutput;
import com.okta.directauth.cli.viewmodel.AuthViewModel;
import com.okta.directauth.jvm.DirectAuthResult;
import com.okta.directauth.jvm.DirectAuthenticationFlow;
import com.okta.directauth.jvm.DirectAuthenticationFlowBuilder;
import com.okta.directauth.model.DirectAuthenticationIntent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Entry point for the Java CLI direct authentication sample app. */
public final class Main {
  private static final String TAG = "Main";
  private static final String VERSION = "1.0.0";
  private static final String CONFIG_DOCS_URL =
      "https://developer.okta.com/docs/guides/configure-direct-auth-grants/";

  private Main() {}

  /**
   * Main entry point.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    if (hasFlag(args, "help") || hasFlag(args, "h")) {
      printHelp();
      return;
    }

    if (hasFlag(args, "version") || hasFlag(args, "v")) {
      System.out.println("okta-direct-auth-cli " + VERSION);
      return;
    }

    if (hasFlag(args, "verbose")) {
      CliLogger.setVerbose(true);
    }

    CliLogger.info(TAG, "Starting Okta Direct Auth CLI v" + VERSION);
    CliLogger.debug(TAG, "Java " + System.getProperty("java.version"));
    CliLogger.debug(
        TAG, "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

    boolean decodedFormat = parseArg(args, "format", "raw").equals("decoded");
    CliLogger.debug(TAG, "Token format: " + (decodedFormat ? "decoded" : "raw"));

    String issuer = resolveConfig(args, "issuer", AppConfig.ISSUER, "Issuer URL");
    String clientId = resolveConfig(args, "clientId", AppConfig.CLIENT_ID, "Client ID");
    String authorizationServerId =
        resolveConfig(
            args,
            "authorizationServerId",
            AppConfig.AUTHORIZATION_SERVER_ID,
            "Authorization Server ID (e.g., 'default')");

    if (issuer.isEmpty() || clientId.isEmpty() || authorizationServerId.isEmpty()) {
      System.err.println("=== Configuration Error ===");
      System.err.println("Missing required configuration values.");
      System.err.println("See: " + CONFIG_DOCS_URL);
      System.exit(1);
    }

    CliLogger.debug(TAG, "Issuer: " + issuer);
    CliLogger.debug(TAG, "Client ID: " + clientId);
    CliLogger.debug(TAG, "Authorization Server ID: " + authorizationServerId);

    List<String> signInScopes = Arrays.asList("openid", "profile", "email");
    List<String> recoveryScopes = List.of("okta.myAccount.password.manage");

    CliLogger.info(TAG, "Building sign-in flow (scopes: " + signInScopes + ")");
    DirectAuthResult<DirectAuthenticationFlow> signInResult =
        new DirectAuthenticationFlowBuilder(issuer, clientId, signInScopes)
            .setAuthorizationServerId(authorizationServerId)
            .setIntent(DirectAuthenticationIntent.SIGN_IN)
            .build();

    CliLogger.info(TAG, "Building recovery flow (scopes: " + recoveryScopes + ")");
    DirectAuthResult<DirectAuthenticationFlow> recoveryResult =
        new DirectAuthenticationFlowBuilder(issuer, clientId, recoveryScopes)
            .setAuthorizationServerId(authorizationServerId)
            .setIntent(DirectAuthenticationIntent.RECOVERY)
            .build();

    if (signInResult.isFailure()) {
      CliLogger.error(
          TAG,
          "Failed to create sign-in flow",
          Objects.requireNonNull(signInResult.exceptionOrNull()));
      System.err.println("Failed to create sign-in flow: " + signInResult.exceptionOrNull());
      System.err.println("See: " + CONFIG_DOCS_URL);
      System.exit(1);
    }

    DirectAuthenticationFlow signInFlow = signInResult.getOrThrow();
    DirectAuthenticationFlow recoveryFlow =
        recoveryResult.isSuccess() ? recoveryResult.getOrThrow() : null;
    if (recoveryFlow == null) {
      CliLogger.info(TAG, "Recovery flow unavailable (password recovery disabled)");
    }

    CliPreferences preferences = new CliPreferences();
    CliLogger.debug(
        TAG,
        "Preferences loaded (last username: "
            + (preferences.getLastUsername().isEmpty() ? "<none>" : preferences.getLastUsername())
            + ")");

    AuthViewModel viewModel = new AuthViewModel(signInFlow, recoveryFlow, issuer);
    ConsoleView view =
        new ConsoleView(
            viewModel,
            new SystemConsoleInput(),
            new SystemConsoleOutput(),
            decodedFormat,
            preferences);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  CliLogger.debug(TAG, "Shutdown hook triggered");
                  view.stop();
                  viewModel.close();
                }));

    CliLogger.info(TAG, "Ready. Entering interactive mode.");
    try {
      view.run();
    } finally {
      CliLogger.info(TAG, "Exiting.");
      viewModel.close();
    }
  }

  /**
   * Resolves a configuration value from: (1) CLI arg, (2) local.properties default, (3) interactive
   * prompt.
   */
  private static String resolveConfig(
      String[] args, String key, String defaultValue, String promptLabel) {
    String fromArg = parseArg(args, key, "");
    if (!fromArg.isEmpty()) {
      CliLogger.debug(TAG, "Config '" + key + "' from CLI arg");
      return fromArg;
    }
    if (!defaultValue.isEmpty()) {
      CliLogger.debug(TAG, "Config '" + key + "' from local.properties");
      return defaultValue;
    }
    if (System.console() == null) {
      CliLogger.error(TAG, "Config '" + key + "' is required but stdin is not available");
      return "";
    }
    CliLogger.debug(TAG, "Config '" + key + "' prompting user");
    System.out.print(promptLabel + ": ");
    String line = System.console().readLine();
    return (line != null) ? line.trim() : "";
  }

  /**
   * Parses a {@code --key=value} argument from the args array.
   *
   * @return the value, or the default if not found
   */
  private static String parseArg(String[] args, String key, String defaultValue) {
    String prefix = "--" + key + "=";
    for (String arg : args) {
      if (arg.startsWith(prefix)) {
        return arg.substring(prefix.length());
      }
    }
    return defaultValue;
  }

  /** Returns true if a boolean flag (e.g., {@code --verbose}, {@code --help}) is present. */
  private static boolean hasFlag(String[] args, String flag) {
    String dashed = "--" + flag;
    String singleDash = "-" + flag;
    for (String arg : args) {
      if (dashed.equals(arg) || singleDash.equals(arg)) {
        return true;
      }
    }
    return false;
  }

  private static void printHelp() {
    System.out.println("okta-direct-auth-cli v" + VERSION);
    System.out.println();
    System.out.println("Usage: okta-direct-auth-cli [OPTIONS]");
    System.out.println();
    System.out.println("Options:");
    System.out.println("  --issuer=URL                  Okta issuer URL");
    System.out.println("  --clientId=ID                 OAuth 2.0 client ID");
    System.out.println(
        "  --authorizationServerId=ID    Authorization server ID (default: 'default')");
    System.out.println("  --format=raw|decoded          Token display format (default: raw)");
    System.out.println("  --verbose                     Enable debug logging to stderr");
    System.out.println("  --version, -v                 Show version and exit");
    System.out.println("  --help, -h                    Show this help and exit");
    System.out.println();
    System.out.println("Configuration:");
    System.out.println(
        "  Values are resolved in order: CLI args > local.properties > interactive prompt.");
    System.out.println("  See: " + CONFIG_DOCS_URL);
    System.out.println();
    System.out.println("Examples:");
    System.out.println("  okta-direct-auth-cli --issuer=https://dev-123.okta.com --clientId=abc");
    System.out.println("  okta-direct-auth-cli --format=decoded --verbose");
  }
}
