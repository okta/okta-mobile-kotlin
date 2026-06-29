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

/** The five OAuth2 standard flows demonstrated by this sample. */
public enum OAuth2Flow {
  RESOURCE_OWNER("Resource Owner Password", true, false, false),
  DEVICE_AUTHORIZATION("Device Authorization", false, false, false),
  BROWSER_SIGN_IN("Browser Sign-In (Auth Code + PKCE)", false, false, false),
  TOKEN_EXCHANGE("Token Exchange", false, true, false),
  SESSION_TOKEN("Session Token", false, false, true);

  private final String label;
  private final boolean requiresCredentials;
  private final boolean requiresTokens;
  private final boolean requiresSessionToken;

  OAuth2Flow(
      String label,
      boolean requiresCredentials,
      boolean requiresTokens,
      boolean requiresSessionToken) {
    this.label = label;
    this.requiresCredentials = requiresCredentials;
    this.requiresTokens = requiresTokens;
    this.requiresSessionToken = requiresSessionToken;
  }

  /**
   * Returns the human-readable display label for this flow.
   *
   * @return display label
   */
  public String getLabel() {
    return label;
  }

  /**
   * Returns true if this flow requires a username and password input.
   *
   * @return true if credentials are required
   */
  public boolean requiresCredentials() {
    return requiresCredentials;
  }

  /**
   * Returns true if this flow requires an ID token and device secret input.
   *
   * @return true if token inputs are required
   */
  public boolean requiresTokens() {
    return requiresTokens;
  }

  /**
   * Returns true if this flow requires a session token input.
   *
   * @return true if a session token is required
   */
  public boolean requiresSessionToken() {
    return requiresSessionToken;
  }
}
