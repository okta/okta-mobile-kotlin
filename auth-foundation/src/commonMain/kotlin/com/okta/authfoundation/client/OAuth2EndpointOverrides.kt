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
package com.okta.authfoundation.client

/**
 * Optional per-endpoint URL overrides for an [OAuth2Client].
 *
 * When provided via [OAuth2ClientBuilder.endpointOverrides], each non-null field replaces the
 * corresponding URL that would otherwise be obtained from the OpenID Connect discovery document.
 *
 * **Skip-discovery optimization**: When **all** 8 fields are non-null, the SDK skips the HTTP
 * request to `{issuerUrl}/.well-known/openid-configuration` entirely and uses the override values
 * directly. This is useful for environments where the discovery URL is unavailable or when
 * startup latency must be minimized.
 *
 * **Partial overrides**: When only some fields are non-null, the discovery document is still
 * fetched. The override values then win over the discovered values for those specific fields.
 *
 * All non-null values must be valid HTTPS URLs; validation occurs at [OAuth2ClientBuilder] build time.
 *
 * Example — override only the token endpoint:
 * ```kotlin
 * val client = OAuth2ClientBuilder.create(
 *     issuerUrl = "https://your-okta-domain.okta.com/oauth2/default",
 *     clientId = "client-id",
 *     scope = listOf("openid"),
 * ) {
 *     endpointOverrides = OAuth2EndpointOverrides(
 *         tokenEndpoint = "https://proxy.example.com/token"
 *     )
 * }.getOrThrow()
 * ```
 *
 * @param authorizationEndpoint override URL for the authorization endpoint, or `null` to use discovery.
 * @param tokenEndpoint override URL for the token endpoint, or `null` to use discovery.
 * @param userInfoEndpoint override URL for the user-info endpoint, or `null` to use discovery.
 * @param jwksUri override URL for the JWKS URI, or `null` to use discovery.
 * @param introspectionEndpoint override URL for the introspection endpoint, or `null` to use discovery.
 * @param revocationEndpoint override URL for the revocation endpoint, or `null` to use discovery.
 * @param endSessionEndpoint override URL for the end-session (logout) endpoint, or `null` to use discovery.
 * @param deviceAuthorizationEndpoint override URL for the device authorization endpoint, or `null` to use discovery.
 */
class OAuth2EndpointOverrides(
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val userInfoEndpoint: String? = null,
    val jwksUri: String? = null,
    val introspectionEndpoint: String? = null,
    val revocationEndpoint: String? = null,
    val endSessionEndpoint: String? = null,
    val deviceAuthorizationEndpoint: String? = null,
)
