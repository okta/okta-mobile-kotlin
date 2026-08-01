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
package com.okta.directauth.api

import com.okta.directauth.model.WebAuthnAssertionResponse

/**
 * Handler for performing a WebAuthn assertion ceremony on the current platform.
 *
 * Platform implementations call the native WebAuthn API (e.g., Android Credential Manager)
 * with the server-provided challenge data and return the assertion response.
 *
 * The full handshake, orchestrated by [com.okta.directauth.model.DirectAuthContinuation.WebAuthn],
 * runs as follows: the server issues a challenge, which is obtained as raw JSON via
 * `DirectAuthContinuation.WebAuthn.challengeData()`; that JSON is passed to this handler's
 * [performAssertion], which invokes the platform's native WebAuthn API to run the ceremony
 * (e.g., prompting for biometrics or a security key) and returns a [WebAuthnAssertionResponse];
 * `DirectAuthContinuation.WebAuthn.proceed()` then exchanges that assertion response with the
 * server for tokens.
 *
 * Most Android apps should use `AndroidWebAuthnCeremonyHandler` rather than implementing this
 * interface directly.
 */
interface WebAuthnCeremonyHandler {
    /**
     * Performs a WebAuthn assertion using the platform's native API.
     *
     * @param challengeData The raw JSON challenge data from the Okta server, to be passed
     * directly to the platform's WebAuthn API.
     * @return A [Result] containing the [WebAuthnAssertionResponse] on success, or an exception
     * on failure (e.g., user cancellation, no matching credential).
     */
    suspend fun performAssertion(challengeData: String): Result<WebAuthnAssertionResponse>
}
