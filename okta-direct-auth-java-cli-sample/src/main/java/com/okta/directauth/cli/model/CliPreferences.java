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
package com.okta.directauth.cli.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persists CLI preferences (e.g., last username) to {@code
 * ~/.okta-direct-auth-cli/preferences.properties}.
 */
public final class CliPreferences {
  private static final String DIR_NAME = ".okta-direct-auth-cli";
  private static final String FILE_NAME = "preferences.properties";
  private static final String KEY_LAST_USERNAME = "lastUsername";

  private final Path filePath;
  private final Properties properties;

  public CliPreferences() {
    this(Paths.get(System.getProperty("user.home"), DIR_NAME, FILE_NAME));
  }

  CliPreferences(Path filePath) {
    this.filePath = filePath;
    this.properties = new Properties();
    load();
  }

  /**
   * Returns the last used username, or empty string if none saved.
   *
   * @return the last username
   */
  public String getLastUsername() {
    return properties.getProperty(KEY_LAST_USERNAME, "");
  }

  /**
   * Saves the username for future sessions.
   *
   * @param username the username to remember
   */
  public void setLastUsername(String username) {
    properties.setProperty(KEY_LAST_USERNAME, username);
    save();
  }

  private void load() {
    if (Files.exists(filePath)) {
      try (InputStream in = Files.newInputStream(filePath)) {
        properties.load(in);
      } catch (IOException e) {
        // Silently ignore - preferences are not critical
      }
    }
  }

  private void save() {
    try {
      Files.createDirectories(filePath.getParent());
      try (OutputStream out = Files.newOutputStream(filePath)) {
        properties.store(out, "Okta Direct Auth CLI Preferences");
      }
    } catch (IOException e) {
      // Silently ignore - preferences are not critical
    }
  }
}
