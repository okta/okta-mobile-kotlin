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
package com.okta.directauth.webauthn

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.okta.directauth.api.WebAuthnCeremonyHandler
import com.okta.directauth.model.WebAuthnAssertionResponse
import org.json.JSONObject

/**
 * Android implementation of [WebAuthnCeremonyHandler] using the Credential Manager API.
 *
 * This handler delegates the WebAuthn assertion ceremony to the Android Credential Manager,
 * which coordinates with platform authenticators (biometrics, screen lock) and external
 * authenticators (security keys) to perform the assertion.
 *
 * @param activity The [Activity] context required by the Credential Manager to display the
 * system credential picker UI.
 */
class AndroidWebAuthnCeremonyHandler(
    private val activity: Activity,
) : WebAuthnCeremonyHandler {
    override suspend fun performAssertion(challengeData: String): Result<WebAuthnAssertionResponse> =
        runCatching {
            val credentialManager = CredentialManager.create(activity)
            val option = GetPublicKeyCredentialOption(challengeData)
            val request = GetCredentialRequest(listOf(option))
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential as PublicKeyCredential
            val responseJson = JSONObject(credential.authenticationResponseJson)
            val response = responseJson.getJSONObject("response")
            WebAuthnAssertionResponse(
                clientDataJSON = response.getString("clientDataJSON"),
                authenticatorData = response.getString("authenticatorData"),
                signature = response.getString("signature"),
                userHandle = response.optString("userHandle").takeIf { it.isNotBlank() }
            )
        }
}
