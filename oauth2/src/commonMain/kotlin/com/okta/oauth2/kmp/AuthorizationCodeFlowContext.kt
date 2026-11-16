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
package com.okta.oauth2.kmp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A model representing the context and current state for an authorization code session.
 *
 * The two PAR properties are appended after [maxAge] — rather than interleaved — so `@JvmOverloads`
 * regenerates the exact 6-parameter constructor already released in oauth2 3.0.0. Note this only
 * preserves direct-construction call sites: the compiler-generated `copy`/`copy$default` for a data
 * class always take every parameter, so their bytecode shape still changed with this addition.
 *
 * @property url the authorization URL to open in a browser.
 * @property redirectUrl the redirect URL configured for the client.
 * @property codeVerifier the PKCE code verifier (internal).
 * @property state the random state parameter for CSRF protection (internal).
 * @property nonce the random nonce for replay protection (internal).
 * @property maxAge the max_age value from the request if provided (internal).
 * @property usedPushedAuthorizationRequest true when start() used PAR and built a request_uri URL.
 * @property pushedAuthorizationRequestUri the request_uri returned by PAR when available.
 */
data class AuthorizationCodeFlowContext
    @JvmOverloads
    constructor(
        val url: String,
        val redirectUrl: String,
        internal val codeVerifier: String,
        internal val state: String,
        internal val nonce: String,
        internal val maxAge: Int?,
        val usedPushedAuthorizationRequest: Boolean = false,
        val pushedAuthorizationRequestUri: String? = null,
    ) {
        /**
         * Serializes this context to a JSON string so an in-progress flow can be persisted and later
         * restored with [fromJson], for example to survive process death while the user is in the browser.
         *
         * The result contains the PKCE `code_verifier`. Treat it as a secret: store it only in
         * app-private storage and delete it once the flow completes.
         */
        fun toJson(): String =
            persistenceJson.encodeToString(
                PersistedContext.serializer(),
                PersistedContext(url, redirectUrl, codeVerifier, state, nonce, maxAge, usedPushedAuthorizationRequest, pushedAuthorizationRequestUri)
            )

        companion object {
            /**
             * Restores a context previously produced by [toJson].
             *
             * @return a [Result] containing the context, or a failure if [json] is malformed.
             */
            fun fromJson(json: String): Result<AuthorizationCodeFlowContext> =
                runCatching {
                    persistenceJson.decodeFromString(PersistedContext.serializer(), json).let {
                        AuthorizationCodeFlowContext(
                            it.url,
                            it.redirectUrl,
                            it.codeVerifier,
                            it.state,
                            it.nonce,
                            it.maxAge,
                            it.usedPushedAuthorizationRequest,
                            it.pushedAuthorizationRequestUri
                        )
                    }
                }
        }

        // codeVerifier is the PKCE secret guarding the authorization code exchange; the default
        // data-class toString() would otherwise print it in full on any log/exception interpolation.
        override fun toString(): String =
            "AuthorizationCodeFlowContext(url=$url, redirectUrl=$redirectUrl, codeVerifier=***, " +
                "state=$state, nonce=$nonce, maxAge=$maxAge, " +
                "usedPushedAuthorizationRequest=$usedPushedAuthorizationRequest, " +
                "pushedAuthorizationRequestUri=$pushedAuthorizationRequestUri)"
    }

// Tolerates fields added or removed by a later SDK version, so a context written before an app upgrade
// is still readable after it.
private val persistenceJson = Json { ignoreUnknownKeys = true }

@Serializable
private class PersistedContext(
    val url: String,
    val redirectUrl: String,
    val codeVerifier: String,
    val state: String,
    val nonce: String,
    val maxAge: Int? = null,
    val usedPushedAuthorizationRequest: Boolean = false,
    val pushedAuthorizationRequestUri: String? = null,
)
