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

import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.client.kmp.AccessTokenValidator
import com.okta.authfoundation.client.kmp.DefaultAccessTokenValidator
import com.okta.authfoundation.client.kmp.DefaultDeviceSecretValidator
import com.okta.authfoundation.client.kmp.DefaultIdTokenValidator
import com.okta.authfoundation.client.kmp.DeviceSecretValidator
import com.okta.authfoundation.client.kmp.IdTokenValidator
import kotlinx.serialization.json.Json

/**
 * Immutable configuration for [OAuth2Client].
 *
 * Created via [OAuth2ClientBuilder]. Contains all settings needed to construct and operate an OAuth2 client
 * without requiring global singletons.
 */
class OAuth2ClientConfiguration internal constructor(
    /** The application's client ID. */
    val clientId: String,
    /** The default access scopes required by the client. */
    val defaultScope: String,
    /**
     * The effective issuer URL used for OIDC discovery and token validation.
     *
     * Derived by [OAuth2ClientBuilder] from the base org URL and [authorizationServerId]:
     * - No [authorizationServerId]: the base org URL itself.
     * - With [authorizationServerId]: `"$baseUrl/oauth2/$authorizationServerId"`.
     */
    val issuerUrl: String,
    /** The HTTP executor used for all network requests. */
    val apiExecutor: ApiExecutor,
    /** The clock used for time-related operations (token expiry, JWT validation). */
    val clock: OidcClock,
    /** The JSON serializer for encoding/decoding responses. */
    val json: Json,
    /** The cache used to optimize network calls. */
    val cache: Cache,
    /**
     * The authorization server ID provided at build time, or null for the org authorization server.
     *
     * This is stored for reference; the effective issuer URL is already reflected in [issuerUrl].
     */
    val authorizationServerId: String?,
    /** Optional client secret for confidential clients. */
    val clientSecret: String?,
    /** Optional ACR values. */
    val acrValues: String?,
    /** Validator for ID tokens. ID tokens are validated after token refresh. */
    val idTokenValidator: IdTokenValidator = DefaultIdTokenValidator(),
    /** Validator for access tokens. Validates `at_hash` claim against the access token hash. */
    val accessTokenValidator: AccessTokenValidator = DefaultAccessTokenValidator(),
    /** Validator for device secrets. Validates `ds_hash` claim against the device secret hash. */
    val deviceSecretValidator: DeviceSecretValidator = DefaultDeviceSecretValidator(),
    /** Optional per-endpoint URL overrides. Non-null fields win over discovery results. */
    val endpointOverrides: OAuth2EndpointOverrides? = null,
)
