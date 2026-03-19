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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Simple logger for CLI debug output. Logs to stderr so it doesn't interfere with CLI output. */
public final class CliLogger {
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

  private static volatile boolean verbose = false;

  private CliLogger() {}

  /** Enables or disables verbose logging. */
  public static void setVerbose(boolean enabled) {
    verbose = enabled;
  }

  /**
   * Logs a debug message to stderr if verbose mode is enabled.
   *
   * @param tag the source component (e.g., "Main", "ViewModel", "View")
   * @param message the debug message
   */
  public static void debug(String tag, String message) {
    if (verbose) {
      String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
      System.err.println("[" + timestamp + "] [DEBUG] [" + tag + "] " + message);
    }
  }

  /**
   * Logs an info message to stderr if verbose mode is enabled.
   *
   * @param tag the source component
   * @param message the info message
   */
  public static void info(String tag, String message) {
    if (verbose) {
      String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
      System.err.println("[" + timestamp + "] [INFO]  [" + tag + "] " + message);
    }
  }

  /**
   * Logs an error message to stderr (always, regardless of verbose mode).
   *
   * @param tag the source component
   * @param message the error message
   */
  public static void error(String tag, String message) {
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
    System.err.println("[" + timestamp + "] [ERROR] [" + tag + "] " + message);
  }

  /**
   * Logs an error message with a throwable to stderr (always, regardless of verbose mode).
   *
   * @param tag the source component
   * @param message the error message
   * @param throwable the exception
   */
  public static void error(String tag, String message, Throwable throwable) {
    error(tag, message + ": " + throwable.getMessage());
    if (verbose) {
      throwable.printStackTrace(System.err);
    }
  }
}
