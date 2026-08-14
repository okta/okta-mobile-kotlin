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
 * A `client_assertion_type` and signed `client_assertion` JWT for JWT-based client authentication
 * (e.g. private_key_jwt), returned by a [ClientAssertionProvider].
 *
 * Deliberately not a `data class`: [assertion] is a client credential, and a generated `toString()`
 * would print it in full on any log/exception-message interpolation or crash report capture.
 */
class ClientAssertion(
    /** The `client_assertion_type` form parameter, e.g. `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`. */
    val type: String,
    /** The signed `client_assertion` JWT. */
    val assertion: String,
) {
    override fun toString(): String = "ClientAssertion(type=$type, assertion=***)"
}

/**
 * Provides a fresh [ClientAssertion] for each OAuth2 request that needs client authentication
 * (token endpoint requests and Pushed Authorization Requests).
 *
 * ```kotlin
 * clientAssertionProvider = ClientAssertionProvider { audience ->
 *     ClientAssertion(
 *         type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
 *         assertion = signJwt(issuer = clientId, subject = clientId, audience = audience)
 *     )
 * }
 * ```
 */
fun interface ClientAssertionProvider {
    /**
     * Invoked anew for every request that needs client authentication — never cached or reused
     * by the SDK — so the returned assertion can carry a unique `jti` and a correctly scoped,
     * non-expired `exp`/`aud`. See
     * [Okta's client authentication guide](https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth)
     * for the exact claim requirements (e.g. `exp` must not be more than one hour out, and a `jti`
     * may only be used once).
     *
     * Intentionally not a `suspend` function so it stays directly implementable from Java as a
     * lambda via [OAuth2ClientBuilder.clientAssertionProvider]/`setClientAssertionProvider`. The
     * SDK invokes it on [OAuth2ClientConfiguration.computeDispatcher] (default
     * [kotlinx.coroutines.Dispatchers.Default]), so implementations must be non-blocking CPU-bound
     * work only (e.g. an in-memory or hardware-backed signing call). If your implementation
     * performs blocking I/O — a network call to a remote signer, a blocking Keystore/HSM round
     * trip — set `computeDispatcher` to `Dispatchers.IO` (or another IO-appropriate dispatcher)
     * when building the client so that work doesn't starve the default dispatcher's limited
     * parallelism.
     *
     * If this throws, the exception propagates as the [Result.failure] of the request that
     * triggered it (`start`/`resume`/[OAuth2ClientConfiguration.clientAuthenticationFormParameters]
     * callers) — there is no dedicated exception type distinguishing a signer failure from a
     * network or server failure.
     *
     * @param audience the exact endpoint URL the request is being sent to (the token endpoint or
     *   the PAR endpoint), suitable for use as the assertion's `aud` claim.
     */
    fun provide(audience: String): ClientAssertion
}
