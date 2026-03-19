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

import com.okta.directauth.cli.CliLogger;
import java.io.Console;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Real stdin implementation using {@link Console} when available, with {@link Scanner} fallback for
 * IDE environments. Supports single-key reading via raw terminal mode on Unix/macOS.
 */
public final class SystemConsoleInput implements ConsoleInput {
  private static final String TAG = "Input";

  private final Console console;
  private final Scanner scanner;
  private final boolean rawModeSupported;

  public SystemConsoleInput() {
    this.console = System.console();
    this.scanner = (console == null) ? new Scanner(System.in) : null;
    this.rawModeSupported = detectRawModeSupport();
  }

  @Override
  public String readLine() {
    return readLine("");
  }

  @Override
  public String readLine(String prompt) {
    try {
      if (console != null) {
        return console.readLine("%s", prompt);
      }
      if (scanner != null) {
        if (!prompt.isEmpty()) {
          System.out.print(prompt);
        }
        if (scanner.hasNextLine()) {
          return scanner.nextLine();
        }
      }
    } catch (RuntimeException e) {
      // JDK 22+ JLine throws UserInterruptException on Ctrl+C
    }
    return null;
  }

  @Override
  public String readPassword(String prompt) {
    try {
      if (console != null) {
        char[] password = console.readPassword("%s", prompt);
        return (password != null) ? new String(password) : null;
      }
      if (scanner != null) {
        if (!prompt.isEmpty()) {
          System.out.print(prompt);
        }
        if (scanner.hasNextLine()) {
          return scanner.nextLine();
        }
      }
    } catch (RuntimeException e) {
      // JDK 22+ JLine throws UserInterruptException on Ctrl+C
    }
    return null;
  }

  @Override
  public String readKey(String prompt) {
    if (!rawModeSupported) {
      return readLine(prompt);
    }
    try {
      System.out.print(prompt);
      System.out.flush();
      stty("-icanon", "min", "1", "-echo");
      try (InputStream tty = new FileInputStream("/dev/tty")) {
        int ch = tty.read();
        if (ch == -1) {
          return null;
        }
        if (ch == 3) {
          // Ctrl+C
          return null;
        }
        String result = String.valueOf((char) ch);
        System.out.println(result);
        return result;
      }
    } catch (Exception e) {
      CliLogger.debug(TAG, "Raw read failed, falling back to readLine: " + e.getMessage());
      return readLine(prompt);
    } finally {
      try {
        stty("icanon", "echo");
      } catch (Exception e) {
        CliLogger.debug(TAG, "Failed to restore terminal: " + e.getMessage());
      }
    }
  }

  private static boolean detectRawModeSupport() {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) {
      return false;
    }
    try {
      Process p =
          new ProcessBuilder("/bin/sh", "-c", "stty -g < /dev/tty")
              .redirectErrorStream(true)
              .start();
      p.getInputStream().readAllBytes();
      return p.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private static void stty(String... args) throws IOException, InterruptedException {
    String sttyArgs = String.join(" ", args);
    Process p =
        new ProcessBuilder("/bin/sh", "-c", "stty " + sttyArgs + " < /dev/tty")
            .redirectErrorStream(true)
            .start();
    p.getInputStream().readAllBytes();
    p.waitFor();
  }
}
