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
package com.okta.directauth.http.model

import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Network model for WebAuthn challenge responses from the Okta server.
 *
 * This model is returned from both:
 * - The `/primary-authenticate` endpoint for primary WebAuthn flows.
 * - The `/challenge` endpoint for MFA WebAuthn flows.
 *
 * It contains the WebAuthn credential request options that are passed to the
 * platform's WebAuthn API. The raw JSON response is forwarded to the platform
 * API as-is (via [com.okta.directauth.model.DirectAuthContinuation.WebAuthn.challengeData]),
 * but this model is used to validate the response structure.
 */
@Serializable
internal data class WebAuthnChallengeResponse(
    val challengeType: String? = null,
    val publicKey: PublicKeyCredentialRequestOptions,
    val authenticatorEnrollments: List<AuthenticatorEnrollment>? = null,
) : ChallengeApiResponse {
    fun challengeData(
        json: Json,
        issuerUrl: String,
    ): String {
        val publicKeyCredentialRequestOptions =
            if (publicKey.rpId == null) {
                val rpId =
                    publicKey.extensions
                        ?.get("appid")
                        ?.jsonPrimitive
                        ?.content ?: issuerUrl
                publicKey.copy(rpId = Url(rpId).host)
            } else {
                publicKey
            }

        return json.encodeToString(PublicKeyCredentialRequestOptions.serializer(), publicKeyCredentialRequestOptions)
    }
}

/**
 * Represents an authenticator enrollment returned from the Okta server
 * as part of a WebAuthn challenge response.
 */
@Serializable
data class AuthenticatorEnrollment(
    val credentialId: String,
    val displayName: String? = null,
    val profile: Map<String, String>? = null,
)
