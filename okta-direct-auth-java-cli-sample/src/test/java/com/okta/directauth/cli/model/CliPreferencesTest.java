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

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CliPreferencesTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Path prefsFile;

  @Before
  public void setUp() {
    prefsFile = tempFolder.getRoot().toPath().resolve("preferences.properties");
  }

  @Test
  public void getLastUsername_NoFile_ReturnsEmpty() {
    CliPreferences prefs = new CliPreferences(prefsFile);

    assertThat(prefs.getLastUsername()).isEmpty();
  }

  @Test
  public void setLastUsername_PersistsToFile() {
    CliPreferences prefs = new CliPreferences(prefsFile);

    prefs.setLastUsername("user@example.com");

    assertThat(Files.exists(prefsFile)).isTrue();
    assertThat(prefs.getLastUsername()).isEqualTo("user@example.com");
  }

  @Test
  public void setLastUsername_LoadedByNewInstance() {
    CliPreferences prefs1 = new CliPreferences(prefsFile);
    prefs1.setLastUsername("saved@example.com");

    CliPreferences prefs2 = new CliPreferences(prefsFile);

    assertThat(prefs2.getLastUsername()).isEqualTo("saved@example.com");
  }

  @Test
  public void setLastUsername_OverwritesPrevious() {
    CliPreferences prefs = new CliPreferences(prefsFile);
    prefs.setLastUsername("first@example.com");
    prefs.setLastUsername("second@example.com");

    CliPreferences reloaded = new CliPreferences(prefsFile);

    assertThat(reloaded.getLastUsername()).isEqualTo("second@example.com");
  }

  @Test
  public void corruptFile_ReturnsEmpty() throws IOException {
    Files.createDirectories(prefsFile.getParent());
    Files.writeString(prefsFile, "not\u0000valid\u0000properties");

    CliPreferences prefs = new CliPreferences(prefsFile);

    assertThat(prefs.getLastUsername()).isEmpty();
  }
}
