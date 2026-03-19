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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.jvm.DirectAuthenticationState;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class StateRendererTest {

  @Test
  public void renderToken_RawFormat_ShowsAccessAndIdToken() {
    TokenDisplay token =
        new TokenDisplay.Builder("eyJhbGciOiJS.access", "Bearer", 3600)
            .idToken("eyJhbGciOiJS.idtoken")
            .build();

    String result = StateRenderer.renderToken(token, false);

    assertThat(result).contains("=== Authentication Successful ===");
    assertThat(result).contains("Access Token:");
    assertThat(result).contains("eyJhbGciOiJS.access");
    assertThat(result).contains("ID Token:");
    assertThat(result).contains("eyJhbGciOiJS.idtoken");
  }

  @Test
  public void renderToken_RawFormat_NoIdToken() {
    TokenDisplay token = new TokenDisplay.Builder("access_token_value", "Bearer", 3600).build();

    String result = StateRenderer.renderToken(token, false);

    assertThat(result).contains("Access Token:");
    assertThat(result).contains("access_token_value");
    assertThat(result).doesNotContain("ID Token:");
  }

  @Test
  public void renderToken_DecodedFormat_NoIdToken() {
    TokenDisplay token = new TokenDisplay.Builder("access", "Bearer", 3600).build();

    String result = StateRenderer.renderToken(token, true);

    assertThat(result).contains("No ID token available");
  }

  @Test
  public void renderError_ApiError_FormatsCorrectly() {
    DirectAuthenticationState.Error.HttpError.ApiError apiError =
        mock(DirectAuthenticationState.Error.HttpError.ApiError.class);
    when(apiError.getErrorCode()).thenReturn("E0000004");
    when(apiError.getErrorSummary()).thenReturn("Authentication failed");
    when(apiError.getErrorCauses()).thenReturn(Arrays.asList("Invalid credentials"));

    String result = StateRenderer.renderError(apiError);

    assertThat(result).contains("=== Authentication Error ===");
    assertThat(result).contains("Error: E0000004");
    assertThat(result).contains("Summary: Authentication failed");
    assertThat(result).contains("Causes: Invalid credentials");
  }

  @Test
  public void renderError_Oauth2Error_FormatsCorrectly() {
    DirectAuthenticationState.Error.HttpError.Oauth2Error oauth2Error =
        mock(DirectAuthenticationState.Error.HttpError.Oauth2Error.class);
    when(oauth2Error.getError()).thenReturn("invalid_grant");
    when(oauth2Error.getErrorDescription()).thenReturn("The credentials are invalid");

    String result = StateRenderer.renderError(oauth2Error);

    assertThat(result).contains("Error: invalid_grant");
    assertThat(result).contains("Description: The credentials are invalid");
  }

  @Test
  public void renderError_InternalError_FormatsCorrectly() {
    DirectAuthenticationState.Error.InternalError internalError =
        mock(DirectAuthenticationState.Error.InternalError.class);
    when(internalError.getErrorCode()).thenReturn("network_error");
    when(internalError.getDescription()).thenReturn("Connection refused");

    String result = StateRenderer.renderError(internalError);

    assertThat(result).contains("Error: network_error");
    assertThat(result).contains("Description: Connection refused");
  }

  @Test
  public void renderError_ApiError_NullCauses() {
    DirectAuthenticationState.Error.HttpError.ApiError apiError =
        mock(DirectAuthenticationState.Error.HttpError.ApiError.class);
    when(apiError.getErrorCode()).thenReturn("E0000004");
    when(apiError.getErrorSummary()).thenReturn("Authentication failed");
    when(apiError.getErrorCauses()).thenReturn(null);

    String result = StateRenderer.renderError(apiError);

    assertThat(result).contains("Error: E0000004");
    assertThat(result).doesNotContain("Causes:");
  }

  @Test
  public void renderError_ApiError_EmptyCauses() {
    DirectAuthenticationState.Error.HttpError.ApiError apiError =
        mock(DirectAuthenticationState.Error.HttpError.ApiError.class);
    when(apiError.getErrorCode()).thenReturn("E0000004");
    when(apiError.getErrorCauses()).thenReturn(Collections.emptyList());

    String result = StateRenderer.renderError(apiError);

    assertThat(result).doesNotContain("Causes:");
  }

  @Test
  public void renderMenu_FormatsNumberedOptions() {
    String result = StateRenderer.renderMenu("Choose:", Arrays.asList("Alpha", "Beta", "Gamma"));

    assertThat(result).contains("Choose:");
    assertThat(result).contains("[1] Alpha");
    assertThat(result).contains("[2] Beta");
    assertThat(result).contains("[3] Gamma");
  }

  @Test
  public void renderBindingCode_FormatsCode() {
    String result = StateRenderer.renderBindingCode("AB");

    assertThat(result).contains("Binding code: AB");
  }

  @Test
  public void renderPasswordChangeSuccess_ReturnsSuccessMessage() {
    String result = StateRenderer.renderPasswordChangeSuccess();

    assertThat(result).contains("=== Password Changed Successfully ===");
  }
}
