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

import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.jvm.DirectAuthenticationState;
import io.jsonwebtoken.Jwts;
import java.util.Base64;
import java.util.List;

/** Stateless utility class that converts states to display strings for the CLI. */
public final class StateRenderer {

  private StateRenderer() {}

  /**
   * Renders token information for display.
   *
   * @param token the token display object
   * @param decoded true to show decoded JWT claims, false for raw JWT strings
   * @return the formatted token display string
   */
  public static String renderToken(TokenDisplay token, boolean decoded) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Authentication Successful ===\n");

    if (decoded) {
      String idToken = token.getIdToken();
      if (idToken != null && !idToken.isEmpty()) {
        try {
          String[] parts = idToken.split("\\.");
          if (parts.length >= 2) {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            String unsecureHeader = headerJson.replace("RS256", "none").replace("ES256", "none");
            String unsecureHeaderEncoded =
                Base64.getUrlEncoder().withoutPadding().encodeToString(unsecureHeader.getBytes());
            String unsecuredJwt = unsecureHeaderEncoded + "." + parts[1] + ".";
            var claims =
                Jwts.parser().unsecured().build().parseUnsecuredClaims(unsecuredJwt).getPayload();

            sb.append("Issuer:  ").append(claims.get("iss", String.class)).append("\n");
            sb.append("Subject: ").append(claims.get("sub", String.class)).append("\n");
            String name = claims.get("name", String.class);
            if (name != null) {
              sb.append("Name:    ").append(name).append("\n");
            }
            String email = claims.get("email", String.class);
            if (email != null) {
              sb.append("Email:   ").append(email).append("\n");
            }
          }
        } catch (Exception e) {
          sb.append("Failed to decode ID token: ").append(e.getMessage()).append("\n");
          sb.append("Raw ID Token:\n").append(idToken).append("\n");
        }
      } else {
        sb.append("No ID token available (openid scope may not be included).\n");
      }
    } else {
      sb.append("Access Token:\n").append(token.getAccessToken()).append("\n");
      String idToken = token.getIdToken();
      if (idToken != null && !idToken.isEmpty()) {
        sb.append("\nID Token:\n").append(idToken).append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Renders an authentication error for display.
   *
   * @param error the error state
   * @return the formatted error string
   */
  public static String renderError(DirectAuthenticationState.Error error) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Authentication Error ===\n");

    if (error instanceof DirectAuthenticationState.Error.HttpError.ApiError) {
      DirectAuthenticationState.Error.HttpError.ApiError apiError =
          (DirectAuthenticationState.Error.HttpError.ApiError) error;
      sb.append("Error: ").append(apiError.getErrorCode()).append("\n");
      if (apiError.getErrorSummary() != null) {
        sb.append("Summary: ").append(apiError.getErrorSummary()).append("\n");
      }
      if (apiError.getErrorCauses() != null && !apiError.getErrorCauses().isEmpty()) {
        sb.append("Causes: ").append(String.join(", ", apiError.getErrorCauses())).append("\n");
      }
    } else if (error instanceof DirectAuthenticationState.Error.HttpError.Oauth2Error) {
      DirectAuthenticationState.Error.HttpError.Oauth2Error oauth2Error =
          (DirectAuthenticationState.Error.HttpError.Oauth2Error) error;
      sb.append("Error: ").append(oauth2Error.getError()).append("\n");
      if (oauth2Error.getErrorDescription() != null) {
        sb.append("Description: ").append(oauth2Error.getErrorDescription()).append("\n");
      }
    } else if (error instanceof DirectAuthenticationState.Error.InternalError) {
      DirectAuthenticationState.Error.InternalError internalError =
          (DirectAuthenticationState.Error.InternalError) error;
      sb.append("Error: ").append(internalError.getErrorCode()).append("\n");
      if (internalError.getDescription() != null) {
        sb.append("Description: ").append(internalError.getDescription()).append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Renders a numbered menu for display.
   *
   * @param title the menu title
   * @param options the list of option labels
   * @return the formatted menu string
   */
  public static String renderMenu(String title, List<String> options) {
    StringBuilder sb = new StringBuilder();
    sb.append(title).append("\n");
    for (int i = 0; i < options.size(); i++) {
      sb.append("[").append(i + 1).append("] ").append(options.get(i)).append("\n");
    }
    return sb.toString();
  }

  /**
   * Renders a binding code for device transfer display.
   *
   * @param code the binding code
   * @return the formatted binding code string
   */
  public static String renderBindingCode(String code) {
    return "Binding code: " + code + "\n";
  }

  /**
   * Renders a password change success message.
   *
   * @return the success message
   */
  public static String renderPasswordChangeSuccess() {
    return "=== Password Changed Successfully ===\n";
  }
}
