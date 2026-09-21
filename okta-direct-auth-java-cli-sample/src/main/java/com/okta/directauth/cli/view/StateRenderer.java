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
import com.okta.directauth.cli.model.IdJagDisplay;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.jvm.DirectAuthenticationState;
import io.jsonwebtoken.Jwts;
import java.util.Base64;
import java.util.List;

/** Stateless utility class that converts states to display strings for the CLI. */
public final class StateRenderer {

  // "Cross App Access" and "ID-JAG" are this capability's real names — the same names used by
  // the SDK's own KDoc, Okta's Cross App Access documentation, and the governing IETF draft
  // (draft-ietf-oauth-identity-assertion-authz-grant). ID_JAG_FIRST_USE carries a one-time
  // plain-language gloss for a Cross App Access console section's first mention of the term;
  // every later mention in that same section uses ID_JAG_TERM alone.
  private static final String ID_JAG_TERM = "ID-JAG";
  private static final String ID_JAG_FIRST_USE =
      "ID-JAG (the short-lived assertion the identity provider issues, which is redeemed at the"
          + " resource app for a resource access token)";

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

  /**
   * Renders why Cross App Access is not yet usable.
   *
   * @param validation the validation outcome
   * @return the formatted message
   */
  public static String renderCrossAppAccessNotConfigured(
      CrossAppAccessConfig.Validation validation) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Cross App Access Is Not Configured ===\n");
    if (validation instanceof CrossAppAccessConfig.Validation.NotConfigured) {
      sb.append(
          "Add a resource app target to local.properties to try this out — see"
              + " okta-direct-auth-java-cli-sample/README.md.\n");
    } else if (validation instanceof CrossAppAccessConfig.Validation.Incomplete) {
      for (String description :
          ((CrossAppAccessConfig.Validation.Incomplete) validation).getMissingDescriptions()) {
        sb.append("- ").append(description).append("\n");
      }
    }
    return sb.toString();
  }

  /**
   * Renders the resource app target and requested scopes, shown before starting an exchange.
   *
   * @param config the resource app target configuration
   * @return the formatted target summary
   */
  public static String renderCrossAppAccessTarget(CrossAppAccessConfig config) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Cross App Access ===\n");
    sb.append(
        "Cross App Access lets this session be used to obtain a scoped access token for another"
            + " app (the resource app) in a different security domain — no second consent prompt,"
            + " no static API key.\n");
    sb.append("IdP Client ID: ").append(config.getIdpClientId()).append("\n");
    sb.append("IdP Issuer: ").append(config.getIdpIssuer()).append("\n");
    sb.append("Resource App Target: ")
        .append(config.getIssuer() != null ? config.getIssuer() : config.getAuthorizationServerId())
        .append("\n");
    return sb.toString();
  }

  /**
   * Renders an obtained ID-JAG's metadata — never its full value.
   *
   * @param idJag the ID-JAG display object
   * @return the formatted ID-JAG summary
   */
  public static String renderIdJag(IdJagDisplay idJag) {
    boolean firstUse = idJag.getRedemptionCount() == 0;
    StringBuilder sb = new StringBuilder();
    sb.append("=== ").append(firstUse ? ID_JAG_FIRST_USE : ID_JAG_TERM).append(" Obtained ===\n");
    sb.append("Audience: ").append(idJag.getAudience()).append("\n");
    sb.append("Expires In: ").append(idJag.getExpiresIn()).append("s\n");
    sb.append("Granted Scope: ")
        .append(idJag.getScope() == null ? "(none reported)" : idJag.getScope())
        .append("\n");
    sb.append("Resource Tokens Issued From This ")
        .append(ID_JAG_TERM)
        .append(": ")
        .append(idJag.getRedemptionCount())
        .append("\n");
    if (firstUse) {
      sb.append("This ")
          .append(ID_JAG_TERM)
          .append(
              " can be redeemed again for a fresh resource access token without another"
                  + " round trip to the identity provider.\n");
    }
    return sb.toString();
  }

  /**
   * Renders a resource access token — the result of a Cross App Access exchange.
   *
   * @param token the resource access token display object
   * @return the formatted result
   */
  public static String renderCrossAppAccessResult(TokenDisplay token) {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Resource Access Token ===\n");
    sb.append("This token is for the resource app — not your signed-in app.\n");
    sb.append("Access Token:\n").append(token.getAccessToken()).append("\n");
    sb.append("Granted Scope: ")
        .append(token.getScope() == null ? "(none reported)" : token.getScope())
        .append("\n");
    sb.append("Token Type: ").append(token.getTokenType()).append("\n");
    sb.append("Expires In: ").append(token.getExpiresIn()).append("s\n");
    return sb.toString();
  }
}
