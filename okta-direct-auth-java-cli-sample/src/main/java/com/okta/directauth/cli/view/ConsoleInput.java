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

/** Abstraction for console input, enabling test injection. */
public interface ConsoleInput {

  /**
   * Reads a line of text from the user.
   *
   * @return the line entered by the user, or null if input is unavailable
   */
  String readLine();

  /**
   * Reads a line of text from the user, displaying the given prompt.
   *
   * @param prompt the prompt to display
   * @return the line entered by the user, or null if input is unavailable
   */
  String readLine(String prompt);

  /**
   * Reads a password from the user, displaying the given prompt. The input should be masked if
   * possible.
   *
   * @param prompt the prompt to display
   * @return the password entered by the user, or null if input is unavailable
   */
  String readPassword(String prompt);

  /**
   * Reads a single key press from the user without requiring Enter. Falls back to {@link
   * #readLine(String)} on platforms that do not support raw terminal mode.
   *
   * @param prompt the prompt to display
   * @return the single character as a string, or null if input is unavailable
   */
  String readKey(String prompt);
}
