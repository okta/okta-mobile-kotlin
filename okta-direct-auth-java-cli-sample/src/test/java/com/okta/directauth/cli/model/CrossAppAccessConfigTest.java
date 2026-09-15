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

import org.junit.Test;

public class CrossAppAccessConfigTest {
  private static final String IDP_ISSUER = "https://idp.example.com";
  private static final String IDP_CLIENT_ID = "idp-client-id";
  private static final String TARGET_CLIENT_ID = "target-client-id";

  /**
   * Builds a config via {@link CrossAppAccessConfig.Builder}, matching the old positional shape.
   */
  private static CrossAppAccessConfig config(
      String idpIssuer,
      String idpClientId,
      String issuer,
      String authorizationServerId,
      String clientId,
      String resource) {
    return new CrossAppAccessConfig.Builder()
        .idpIssuer(idpIssuer)
        .idpClientId(idpClientId)
        .issuer(issuer)
        .authorizationServerId(authorizationServerId)
        .clientId(clientId)
        .resource(resource)
        .build();
  }

  @Test
  public void validate_NothingSetAndNoCredentials_ReturnsNotConfigured() {
    CrossAppAccessConfig config = config(null, null, null, null, null, null);

    assertThat(config.validate(false))
        .isInstanceOf(CrossAppAccessConfig.Validation.NotConfigured.class);
  }

  @Test
  public void validate_AuthorizationServerIdAndCredentials_ReturnsComplete() {
    CrossAppAccessConfig config =
        config(IDP_ISSUER, IDP_CLIENT_ID, null, "ausOther", TARGET_CLIENT_ID, null);

    assertThat(config.validate(true)).isInstanceOf(CrossAppAccessConfig.Validation.Complete.class);
  }

  @Test
  public void validate_IssuerOriginAndCredentials_ReturnsComplete() {
    CrossAppAccessConfig config =
        config(
            IDP_ISSUER,
            IDP_CLIENT_ID,
            "https://resource.example.com",
            null,
            TARGET_CLIENT_ID,
            null);

    assertThat(config.validate(true)).isInstanceOf(CrossAppAccessConfig.Validation.Complete.class);
  }

  @Test
  public void validate_IssuerOriginAndAuthorizationServerIdAndCredentials_ReturnsComplete() {
    // Both set names a custom authorization server on the DIFFERENT org named by getIssuer() —
    // not this app's own org, and not ambiguous.
    // CrossAppAccessTargetBuilder.setAuthorizationServerId
    // (SDK-level, forIssuer only) combines the two directly.
    CrossAppAccessConfig config =
        config(
            IDP_ISSUER,
            IDP_CLIENT_ID,
            "https://resource.example.com",
            "customAuthServer",
            TARGET_CLIENT_ID,
            null);

    assertThat(config.validate(true)).isInstanceOf(CrossAppAccessConfig.Validation.Complete.class);
  }

  @Test
  public void validate_IssuerWithPathAndNoAuthorizationServerId_ReturnsIncompleteNamingTheFix() {
    // A lone path-bearing issuer: OAuth2ClientBuilder derives the effective issuer from
    // scheme+host+port only, silently discarding the path, since no authorizationServerId is set
    // to make it meaningful. Without this check, the developer's intended custom server would be
    // silently replaced by the org's default one.
    CrossAppAccessConfig config =
        config(
            IDP_ISSUER,
            IDP_CLIENT_ID,
            "https://example.okta.com/oauth2/customAuthServer",
            null,
            TARGET_CLIENT_ID,
            null);

    Object result = config.validate(true);

    assertThat(result).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    CrossAppAccessConfig.Validation.Incomplete incomplete =
        (CrossAppAccessConfig.Validation.Incomplete) result;
    assertThat(
            incomplete.getMissingDescriptions().stream()
                .anyMatch(d -> d.contains("xaaTargetAuthorizationServerId")))
        .isTrue();
  }

  @Test
  public void validate_TargetNamedButNoTargetCredential_ReturnsIncomplete() {
    CrossAppAccessConfig config =
        config(
            IDP_ISSUER,
            IDP_CLIENT_ID,
            "https://resource.example.com",
            null,
            TARGET_CLIENT_ID,
            null);

    Object result = config.validate(false);

    assertThat(result).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    CrossAppAccessConfig.Validation.Incomplete incomplete =
        (CrossAppAccessConfig.Validation.Incomplete) result;
    assertThat(
            incomplete.getMissingDescriptions().stream()
                .anyMatch(d -> d.toLowerCase().contains("target credential")))
        .isTrue();
  }

  @Test
  public void validate_TargetCredentialButNoTargetName_ReturnsIncomplete() {
    CrossAppAccessConfig config =
        config(IDP_ISSUER, IDP_CLIENT_ID, null, null, TARGET_CLIENT_ID, null);

    Object result = config.validate(true);

    assertThat(result).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    CrossAppAccessConfig.Validation.Incomplete incomplete =
        (CrossAppAccessConfig.Validation.Incomplete) result;
    assertThat(
            incomplete.getMissingDescriptions().stream()
                .anyMatch(d -> d.contains("xaaTargetIssuer")))
        .isTrue();
  }

  @Test
  public void validate_MissingTargetClientId_ReturnsIncompleteNamingIt() {
    CrossAppAccessConfig config =
        config(IDP_ISSUER, IDP_CLIENT_ID, "https://resource.example.com", null, null, null);

    Object result = config.validate(true);

    assertThat(result).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    CrossAppAccessConfig.Validation.Incomplete incomplete =
        (CrossAppAccessConfig.Validation.Incomplete) result;
    assertThat(
            incomplete.getMissingDescriptions().stream()
                .anyMatch(d -> d.contains("xaaTargetClientId")))
        .isTrue();
  }

  @Test
  public void validate_MissingIdpIssuer_ReturnsIncompleteNamingIt() {
    CrossAppAccessConfig config =
        config(null, IDP_CLIENT_ID, "https://resource.example.com", null, TARGET_CLIENT_ID, null);

    Object result = config.validate(true);

    assertThat(result).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    CrossAppAccessConfig.Validation.Incomplete incomplete =
        (CrossAppAccessConfig.Validation.Incomplete) result;
    assertThat(
            incomplete.getMissingDescriptions().stream().anyMatch(d -> d.contains("xaaIdpIssuer")))
        .isTrue();
  }

  @Test
  public void validate_MissingIdpClientId_ReturnsIncompleteNamingIt() {
    CrossAppAccessConfig config =
        config(IDP_ISSUER, null, "https://resource.example.com", null, TARGET_CLIENT_ID, null);

    Object result = config.validate(true);

    assertThat(result).isInstanceOf(CrossAppAccessConfig.Validation.Incomplete.class);
    CrossAppAccessConfig.Validation.Incomplete incomplete =
        (CrossAppAccessConfig.Validation.Incomplete) result;
    assertThat(
            incomplete.getMissingDescriptions().stream()
                .anyMatch(d -> d.contains("xaaIdpClientId")))
        .isTrue();
  }

  @Test
  public void validate_NoIdpCredentialButTargetCredentialPresent_ReturnsComplete() {
    // Unlike the target, the IdP client's credential is never required: the SDK only enforces
    // one for the target, and Okta's own "AI agent" registration explicitly supports a
    // credential-less "Client ID only" requesting app for clients that can't store a secret.
    CrossAppAccessConfig config =
        config(
            IDP_ISSUER,
            IDP_CLIENT_ID,
            "https://resource.example.com",
            null,
            TARGET_CLIENT_ID,
            null);

    assertThat(config.validate(true)).isInstanceOf(CrossAppAccessConfig.Validation.Complete.class);
  }
}
