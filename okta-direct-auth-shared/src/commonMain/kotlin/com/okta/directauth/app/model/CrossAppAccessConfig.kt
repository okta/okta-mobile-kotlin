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
package com.okta.directauth.app.model

import com.okta.directauth.app.AppConfig

/**
 * The developer-supplied description of Cross App Access's two sides, read from
 * `local.properties` at build time: the requesting app's own IdP identity ([idpIssuer]/
 * [idpClientId]) and the resource app target it exchanges for.
 *
 * Deliberately carries no credential fields. Both credentials are read separately by
 * [com.okta.directauth.app.platform.crossAppAccessIdpCredential] /
 * [com.okta.directauth.app.platform.crossAppAccessTargetCredential] and applied directly to the
 * SDK's client/target builders — never stored here — so this type structurally cannot reach a
 * display, log, or `toString()` call anywhere it's passed around.
 */
class CrossAppAccessConfig(
    /**
     * The requesting app's own org — always the org's *default* authorization server. There is
     * no paired authorization-server-id property here, unlike [issuer] below: the ID-JAG exchange
     * must be submitted to the org's own authorization server, never a custom one.
     */
    val idpIssuer: String?,
    /** The requesting app's own client id, registered as a separate app integration in Okta. */
    val idpClientId: String?,
    /**
     * Names a target's **org** — a **different** Okta org from this app's own. Three states, not
     * two:
     * - Set alone (a bare origin, e.g. `https://resource-org.okta.com`, no path — see
     *   [ConfigValidation.Incomplete] below for why a path is rejected here): that org's *default*
     *   authorization server.
     * - Unset, [authorizationServerId] set: a custom authorization server on **this app's own**
     *   org instead — see [authorizationServerId].
     * - Both set: a custom authorization server on this (*different*) org — [authorizationServerId]
     *   is then appended to this origin, exactly like the primary client's own
     *   `issuer`+`authorizationServerId` combine, rather than resolved against this app's own org.
     */
    val issuer: String?,
    /**
     * Names a target's custom authorization server id. Resolved against **this app's own org**
     * when [issuer] is unset; resolved against [issuer]'s org instead when both are set. See
     * [issuer]'s doc for the three-state combination this pairs with.
     */
    val authorizationServerId: String?,
    /** Client id at the target; `null` reuses the primary client's. */
    val clientId: String?,
    /** Optional RFC 8707 resource indicator. */
    val resource: String?,
) {
    companion object {
        /** Reads the Cross App Access configuration baked from `local.properties`. */
        fun fromAppConfig(): CrossAppAccessConfig =
            CrossAppAccessConfig(
                idpIssuer = AppConfig.XAA_IDP_ISSUER.trim().ifBlank { null },
                idpClientId = AppConfig.XAA_IDP_CLIENT_ID.trim().ifBlank { null },
                issuer = AppConfig.XAA_TARGET_ISSUER.trim().ifBlank { null },
                authorizationServerId = AppConfig.XAA_TARGET_AUTHORIZATION_SERVER_ID.trim().ifBlank { null },
                clientId = AppConfig.XAA_TARGET_CLIENT_ID.trim().ifBlank { null },
                resource = AppConfig.XAA_TARGET_RESOURCE.trim().ifBlank { null }
            )
    }
}

/**
 * The result of validating a [CrossAppAccessConfig] against whether a target credential is
 * available, evaluated before any network request.
 *
 * [NotConfigured] and [Incomplete] are deliberately distinct: the first is the expected state for
 * a developer who has not opted into this capability, the second is a mistake worth pointing at.
 */
sealed class ConfigValidation {
    /** The target names exactly one authorization server and a credential is available. */
    data object Complete : ConfigValidation()

    /** No target key and no credential are set — the normal state when not opted in. */
    data object NotConfigured : ConfigValidation()

    /** Some values are present, but at least one required value is missing. */
    data class Incomplete(
        val missingDescriptions: List<String>,
    ) : ConfigValidation()
}

/**
 * Validates [this] configuration, given whether the target credential was found.
 *
 * There is no `hasIdpCredential` parameter: unlike the target, the requesting-app IdP client does
 * not need a confidential-client credential — [com.okta.oauth2.kmp.CrossAppAccessFlowImpl] only
 * enforces one for the target, and Okta's own "AI agent" registration explicitly supports a
 * credential-less "Client ID only" requesting app for clients that can't store a secret (this
 * sample included). A credential is still applied to the IdP client when
 * [com.okta.directauth.app.platform.crossAppAccessIdpCredential] resolves to one; it's simply
 * never required.
 *
 * @param hasTargetCredential whether [com.okta.directauth.app.platform.crossAppAccessTargetCredential]
 *   resolved to something other than `None`. Passed as a boolean, never the credential value
 *   itself, so this function cannot leak it even by accident.
 */
fun CrossAppAccessConfig.validate(hasTargetCredential: Boolean): ConfigValidation {
    val hasIdpIssuer = idpIssuer != null
    val hasIdpClientId = idpClientId != null
    val hasIssuer = issuer != null
    val hasAuthServerId = authorizationServerId != null

    val nothingConfigured =
        !hasIdpIssuer && !hasIdpClientId &&
            !hasIssuer && !hasAuthServerId && clientId == null && resource == null && !hasTargetCredential
    if (nothingConfigured) {
        return ConfigValidation.NotConfigured
    }

    val missing = mutableListOf<String>()
    if (!hasIdpIssuer) {
        missing += "xaaIdpIssuer (the requesting app's own org)"
    }
    if (!hasIdpClientId) {
        missing += "xaaIdpClientId (the requesting app's own client id)"
    }
    if (!hasIssuer && !hasAuthServerId) {
        missing += "xaaTargetIssuer or xaaTargetAuthorizationServerId"
    }
    // Only dangerous when authorizationServerId is absent: OAuth2ClientBuilder derives the
    // effective issuer from scheme+host+port only, silently discarding any path, then appends
    // "/oauth2/<id>" itself only when authorizationServerId is set. A lone path-bearing issuer
    // would silently resolve to that org's *default* authorization server instead of the one
    // named — exactly the failure mode CrossAppAccessTarget.forIssuer's own KDoc warns about.
    // Once authorizationServerId is also set, that id — not any path in issuer — decides the
    // server, so there is nothing left to warn about.
    if (hasIssuer && !hasAuthServerId && issuerHasPath(issuer)) {
        missing += "xaaTargetIssuer must be an origin with no path when xaaTargetAuthorizationServerId is " +
            "not also set — a path is silently discarded otherwise. Also set xaaTargetAuthorizationServerId " +
            "to the custom authorization server id to target a custom server on this different org"
    }
    if (!hasTargetCredential) {
        missing += "a target credential (xaaTargetClientSecret or xaaTargetClientAssertionPrivateKeyPem)"
    }

    return if (missing.isEmpty()) ConfigValidation.Complete else ConfigValidation.Incomplete(missing)
}

/** Whether [issuer] carries a path beyond its origin (scheme, host, non-default port). */
private fun issuerHasPath(issuer: String): Boolean {
    val withoutScheme = issuer.substringAfter("://", issuer)
    val trimmed = withoutScheme.removeSuffix("/")
    return trimmed.contains('/')
}
