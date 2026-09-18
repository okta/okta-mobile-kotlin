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

import com.okta.directauth.cli.AppConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The developer-supplied description of the Cross App Access resource app target, read from {@code
 * local.properties} at build time.
 *
 * <p>Deliberately carries no credential field. The target credential is read separately by {@code
 * ClientAuthentication}'s target-scoped overload and applied directly to the SDK's target builder —
 * never stored here — so it structurally cannot reach a display, log, or {@code toString()} call
 * anywhere this type is passed around.
 *
 * <p>Use {@link Builder} to construct instances — six same-typed {@code String} parameters is
 * exactly the shape a positional constructor silently gets wrong when two are transposed.
 */
public final class CrossAppAccessConfig {
  private final String idpIssuer;
  private final String idpClientId;
  private final String issuer;
  private final String authorizationServerId;
  private final String clientId;
  private final String resource;

  private CrossAppAccessConfig(Builder builder) {
    this.idpIssuer = builder.idpIssuer;
    this.idpClientId = builder.idpClientId;
    this.issuer = builder.issuer;
    this.authorizationServerId = builder.authorizationServerId;
    this.clientId = builder.clientId;
    this.resource = builder.resource;
  }

  public String getIdpIssuer() {
    return idpIssuer;
  }

  public String getIdpClientId() {
    return idpClientId;
  }

  public String getIssuer() {
    return issuer;
  }

  public String getAuthorizationServerId() {
    return authorizationServerId;
  }

  public String getClientId() {
    return clientId;
  }

  public String getResource() {
    return resource;
  }

  /**
   * Reads the Cross App Access target configuration baked from {@code local.properties}.
   *
   * @return the configuration
   */
  public static CrossAppAccessConfig fromAppConfig() {
    return new Builder()
        .idpIssuer(blankToNull(AppConfig.XAA_IDP_ISSUER))
        .idpClientId(blankToNull(AppConfig.XAA_IDP_CLIENT_ID))
        .issuer(blankToNull(AppConfig.XAA_TARGET_ISSUER))
        .authorizationServerId(blankToNull(AppConfig.XAA_TARGET_AUTHORIZATION_SERVER_ID))
        .clientId(blankToNull(AppConfig.XAA_TARGET_CLIENT_ID))
        .resource(blankToNull(AppConfig.XAA_TARGET_RESOURCE))
        .build();
  }

  /**
   * Validates this configuration, given whether the target credential was found.
   *
   * <p>There is no {@code hasIdpCredential} parameter: unlike the target, the requesting-app IdP
   * client does not need a confidential-client credential — {@code CrossAppAccessFlowImpl} only
   * enforces one for the target, and Okta's own "AI agent" registration explicitly supports a
   * credential-less "Client ID only" requesting app for clients that can't store a secret (this
   * sample included). A credential is still applied to the IdP client when {@code
   * ClientAuthentication}'s IdP-scoped overload finds one; it's simply never required.
   *
   * @param hasTargetCredential whether the target-scoped {@code ClientAuthentication} overload
   *     found a credential. Passed as a boolean, never the credential value itself, so this method
   *     cannot leak it even by accident.
   * @return the validation outcome
   */
  public Validation validate(boolean hasTargetCredential) {
    boolean hasIdpIssuer = idpIssuer != null;
    boolean hasIdpClientId = idpClientId != null;
    boolean hasIssuer = issuer != null;
    boolean hasAuthServerId = authorizationServerId != null;

    boolean nothingConfigured =
        !hasIdpIssuer
            && !hasIdpClientId
            && !hasIssuer
            && !hasAuthServerId
            && clientId == null
            && resource == null
            && !hasTargetCredential;
    if (nothingConfigured) {
      return new Validation.NotConfigured();
    }

    List<String> missing = new ArrayList<>();
    if (!hasIdpIssuer) {
      missing.add("xaaIdpIssuer (the requesting app's own org)");
    }
    if (!hasIdpClientId) {
      missing.add("xaaIdpClientId (the requesting app's own client id)");
    }
    if (!hasIssuer && !hasAuthServerId) {
      missing.add("xaaTargetIssuer or xaaTargetAuthorizationServerId");
    }
    // Only dangerous when authorizationServerId is absent: OAuth2ClientBuilder derives the
    // effective issuer from scheme+host+port only, silently discarding any path, then appends
    // "/oauth2/<id>" itself only when authorizationServerId is set. A lone path-bearing issuer
    // would silently resolve to that org's default authorization server instead of the one named
    // — exactly the failure mode CrossAppAccessTarget.forIssuer's own KDoc warns about. Once
    // authorizationServerId is also set, that id — not any path in issuer — decides the server,
    // so there is nothing left to warn about.
    if (hasIssuer && !hasAuthServerId && issuerHasPath(issuer)) {
      missing.add(
          "xaaTargetIssuer must be an origin with no path when xaaTargetAuthorizationServerId is "
              + "not also set — a path is silently discarded otherwise. Also set "
              + "xaaTargetAuthorizationServerId to the custom authorization server id to target a "
              + "custom server on this different org");
    }
    if (clientId == null) {
      missing.add(
          "xaaTargetClientId (for a same-org custom authorization server, this must be the same"
              + " value as xaaIdpClientId)");
    }
    if (!hasTargetCredential) {
      missing.add(
          "a target credential (xaaTargetClientSecret or xaaTargetClientAssertionPrivateKeyPem)");
    }

    return missing.isEmpty() ? new Validation.Complete() : new Validation.Incomplete(missing);
  }

  /**
   * Validates the resource app target and IdP identity configuration, additionally requiring that
   * the requesting-app IdP client actually built successfully.
   *
   * <p>{@link #validate(boolean)} alone cannot see this: a {@code Complete} result there only
   * means {@code xaaIdpIssuer}/{@code xaaIdpClientId} are non-null static fields, not that {@code
   * OAuth2ClientBuilder} succeeded in building a client from them. Only checked when the
   * single-argument overload would otherwise return {@link Validation.Complete} — if it didn't,
   * {@code idpIssuer}/{@code idpClientId} can't both be non-null anyway, so {@code idpFlowAvailable}
   * being false there is expected, not a build failure.
   *
   * @param hasTargetCredential see {@link #validate(boolean)}
   * @param idpFlowAvailable whether the requesting-app IdP client (and the flow built from it)
   *     actually built successfully
   * @return the validation outcome
   */
  public Validation validate(boolean hasTargetCredential, boolean idpFlowAvailable) {
    Validation result = validate(hasTargetCredential);
    if (idpFlowAvailable || !(result instanceof Validation.Complete)) {
      return result;
    }
    List<String> missing = new ArrayList<>();
    missing.add(
        "the Cross App Access IdP client failed to build — see startup logs for details");
    return new Validation.Incomplete(missing);
  }

  private static boolean issuerHasPath(String issuer) {
    int schemeEnd = issuer.indexOf("://");
    String withoutScheme = schemeEnd >= 0 ? issuer.substring(schemeEnd + 3) : issuer;
    String trimmed =
        withoutScheme.endsWith("/")
            ? withoutScheme.substring(0, withoutScheme.length() - 1)
            : withoutScheme;
    return trimmed.contains("/");
  }

  private static String blankToNull(String value) {
    return (value == null || value.trim().isEmpty()) ? null : value.trim();
  }

  /**
   * The result of validating a {@link CrossAppAccessConfig} against whether a target credential is
   * available, evaluated before any network request.
   *
   * <p>{@link NotConfigured} and {@link Incomplete} are deliberately distinct: the first is the
   * expected state for a developer who has not opted into this capability, the second is a mistake
   * worth pointing at.
   */
  public abstract static class Validation {
    private Validation() {}

    /** The target names exactly one authorization server and a credential is available. */
    public static final class Complete extends Validation {}

    /** No target key and no credential are set — the normal state when not opted in. */
    public static final class NotConfigured extends Validation {}

    /** Some values are present, but at least one required value is missing. */
    public static final class Incomplete extends Validation {
      private final List<String> missingDescriptions;

      Incomplete(List<String> missingDescriptions) {
        this.missingDescriptions =
            Collections.unmodifiableList(new ArrayList<>(missingDescriptions));
      }

      public List<String> getMissingDescriptions() {
        return missingDescriptions;
      }
    }
  }

  /**
   * Builder for {@link CrossAppAccessConfig}. All six values are optional; see {@link #validate}.
   */
  public static final class Builder {
    private String idpIssuer;
    private String idpClientId;
    private String issuer;
    private String authorizationServerId;
    private String clientId;
    private String resource;

    /**
     * @param idpIssuer the requesting app's own org — always that org's default authorization
     *     server, or null. There is no paired authorization-server-id parameter here, unlike {@link
     *     #issuer} below: the ID-JAG exchange must be submitted to the org's own authorization
     *     server, never a custom one
     */
    public Builder idpIssuer(String idpIssuer) {
      this.idpIssuer = idpIssuer;
      return this;
    }

    /**
     * @param idpClientId the requesting app's own client id, registered as a separate app
     *     integration in Okta, or null
     */
    public Builder idpClientId(String idpClientId) {
      this.idpClientId = idpClientId;
      return this;
    }

    /**
     * @param issuer names a target's org — a different Okta org from this app's own — or null.
     *     Three states, not two: set alone (a bare origin, no path — see {@link #validate} for why
     *     a path is rejected here), names that org's default authorization server; unset with
     *     {@link #authorizationServerId} set names a custom authorization server on this app's own
     *     org instead; both set names a custom authorization server on this (different) org, with
     *     {@link #authorizationServerId} appended to this origin exactly like the primary client's
     *     own issuer+authorizationServerId combine
     */
    public Builder issuer(String issuer) {
      this.issuer = issuer;
      return this;
    }

    /**
     * @param authorizationServerId names a target's custom authorization server id, or null.
     *     Resolved against this app's own org when {@link #issuer} is unset; resolved against
     *     {@link #issuer}'s org instead when both are set
     */
    public Builder authorizationServerId(String authorizationServerId) {
      this.authorizationServerId = authorizationServerId;
      return this;
    }

    /**
     * @param clientId client id at the target, or null
     */
    public Builder clientId(String clientId) {
      this.clientId = clientId;
      return this;
    }

    /**
     * @param resource optional RFC 8707 resource indicator, or null
     */
    public Builder resource(String resource) {
      this.resource = resource;
      return this;
    }

    public CrossAppAccessConfig build() {
      return new CrossAppAccessConfig(this);
    }
  }
}
