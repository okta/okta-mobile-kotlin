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
package com.okta.directauth.app

import com.okta.directauth.app.model.ConfigValidation
import com.okta.directauth.app.model.CrossAppAccessConfig
import com.okta.directauth.app.model.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrossAppAccessConfigTest {
    // A complete IdP identity, held constant across target-focused tests below so each one
    // isolates exactly the target-side piece it's naming in its own test name.
    private fun completeIdp(
        idpIssuer: String? = "https://idp.example.com",
        idpClientId: String? = "idp-client-id",
    ) = idpIssuer to idpClientId

    private fun config(
        idpIssuer: String? = "https://idp.example.com",
        idpClientId: String? = "idp-client-id",
        issuer: String? = null,
        authorizationServerId: String? = null,
        clientId: String? = null,
        resource: String? = null,
    ) = CrossAppAccessConfig(idpIssuer, idpClientId, issuer, authorizationServerId, clientId, resource)

    @Test
    fun validate_NothingSetAndNoCredentials_ReturnsNotConfigured() {
        val emptyConfig = CrossAppAccessConfig(null, null, null, null, null, null)

        assertEquals(ConfigValidation.NotConfigured, emptyConfig.validate(hasTargetCredential = false))
    }

    @Test
    fun validate_AuthorizationServerIdAndCredentials_ReturnsComplete() {
        val (idpIssuer, idpClientId) = completeIdp()
        val cfg = config(idpIssuer = idpIssuer, idpClientId = idpClientId, authorizationServerId = "ausOther")

        assertEquals(ConfigValidation.Complete, cfg.validate(hasTargetCredential = true))
    }

    @Test
    fun validate_IssuerOriginAndCredentials_ReturnsComplete() {
        val cfg = config(issuer = "https://resource.example.com")

        assertEquals(ConfigValidation.Complete, cfg.validate(hasTargetCredential = true))
    }

    @Test
    fun validate_IssuerWithTrailingSlashAndCredentials_ReturnsComplete() {
        val cfg = config(issuer = "https://resource.example.com/")

        assertEquals(ConfigValidation.Complete, cfg.validate(hasTargetCredential = true))
    }

    @Test
    fun validate_IssuerOriginAndAuthorizationServerIdAndCredentials_ReturnsComplete() {
        // Both set names a custom authorization server on the DIFFERENT org named by `issuer` —
        // not this app's own org, and not ambiguous. CrossAppAccessTarget.forIssuer's
        // authorizationServerId builder property (SDK-level) combines the two directly.
        val cfg = config(issuer = "https://resource.example.com", authorizationServerId = "customAuthServer")

        assertEquals(ConfigValidation.Complete, cfg.validate(hasTargetCredential = true))
    }

    @Test
    fun validate_IssuerWithPathAndNoAuthorizationServerId_ReturnsIncompleteNamingTheFix() {
        // A lone path-bearing issuer: OAuth2ClientBuilder derives the effective issuer from
        // scheme+host+port only, silently discarding the path, since no authorizationServerId is
        // set to make it meaningful. Without this check, the developer's intended custom server
        // would be silently replaced by the org's default one.
        val cfg = config(issuer = "https://example.okta.com/oauth2/customAuthServer")

        val result = cfg.validate(hasTargetCredential = true)

        assertIs<ConfigValidation.Incomplete>(result)
        assertTrue(result.missingDescriptions.any { it.contains("xaaTargetAuthorizationServerId") })
    }

    @Test
    fun validate_TargetNamedButNoTargetCredential_ReturnsIncomplete() {
        val cfg = config(issuer = "https://resource.example.com")

        val result = cfg.validate(hasTargetCredential = false)

        assertIs<ConfigValidation.Incomplete>(result)
        assertTrue(result.missingDescriptions.any { it.contains("target credential", ignoreCase = true) })
    }

    @Test
    fun validate_TargetCredentialButNoTargetName_ReturnsIncomplete() {
        val cfg = config(clientId = "someClientId")

        val result = cfg.validate(hasTargetCredential = true)

        assertIs<ConfigValidation.Incomplete>(result)
        assertTrue(result.missingDescriptions.any { it.contains("xaaTargetIssuer") })
    }

    @Test
    fun validate_MissingIdpIssuer_ReturnsIncompleteNamingIt() {
        val cfg = config(idpIssuer = null, issuer = "https://resource.example.com")

        val result = cfg.validate(hasTargetCredential = true)

        assertIs<ConfigValidation.Incomplete>(result)
        assertTrue(result.missingDescriptions.any { it.contains("xaaIdpIssuer") })
    }

    @Test
    fun validate_MissingIdpClientId_ReturnsIncompleteNamingIt() {
        val cfg = config(idpClientId = null, issuer = "https://resource.example.com")

        val result = cfg.validate(hasTargetCredential = true)

        assertIs<ConfigValidation.Incomplete>(result)
        assertTrue(result.missingDescriptions.any { it.contains("xaaIdpClientId") })
    }

    @Test
    fun validate_NoIdpCredentialButTargetCredentialPresent_ReturnsComplete() {
        // Unlike the target, the IdP client's credential is never required: the SDK only enforces
        // one for the target, and Okta's own "AI agent" registration explicitly supports a
        // credential-less "Client ID only" requesting app for clients that can't store a secret.
        val cfg = config(issuer = "https://resource.example.com")

        assertEquals(ConfigValidation.Complete, cfg.validate(hasTargetCredential = true))
    }
}
