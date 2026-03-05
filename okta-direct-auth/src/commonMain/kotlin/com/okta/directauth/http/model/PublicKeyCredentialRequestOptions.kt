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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * WebAuthn public key credential request options returned by the Okta server.
 *
 * Maps to the W3C `PublicKeyCredentialRequestOptions` dictionary. These options
 * are passed to the platform's WebAuthn API to initiate an assertion ceremony.
 *
 * @param challenge The base64url-encoded challenge from the server.
 * @param rpId The relying party identifier (usually the Okta domain).
 * @param allowCredentials An optional list of credentials the server will accept.
 * @param timeout The time (in milliseconds) the caller is willing to wait for the operation.
 * @param userVerification A hint for the authenticator about user verification.
 * @param hints Hints about the preferred authenticator type.
 * @param extensions Additional WebAuthn extensions as raw JSON.
 */
@Serializable
internal data class PublicKeyCredentialRequestOptions(
    val challenge: String,
    val rpId: String? = null,
    val allowCredentials: List<PublicKeyCredentialDescriptor>? = null,
    val timeout: Long? = null,
    val userVerification: UserVerificationRequirement? = null,
    val hints: List<PublicKeyCredentialHints>? = null,
    val extensions: JsonObject? = null,
)

/**
 * Describes a public key credential that the server will accept.
 *
 * Maps to the W3C `PublicKeyCredentialDescriptor` dictionary.
 *
 * @param id The base64url-encoded credential ID.
 * @param type The credential type (always [PublicKeyCredentialType.PUBLIC_KEY]).
 * @param transports Optional transport hints for the authenticator.
 */
@Serializable
internal data class PublicKeyCredentialDescriptor(
    val id: String,
    val type: PublicKeyCredentialType,
    val transports: List<AuthenticatorTransport>? = null,
)

/**
 * The type of public key credential.
 *
 * @see <a href="https://www.w3.org/TR/webauthn-3/#enumdef-publickeycredentialtype">W3C WebAuthn Spec</a>
 */
@Serializable
internal enum class PublicKeyCredentialType {
    @SerialName("public-key")
    PUBLIC_KEY,
}

/**
 * Authenticator transport mechanisms.
 *
 * Hints about how the client might communicate with a particular authenticator.
 *
 * @see <a href="https://www.w3.org/TR/webauthn-3/#enumdef-authenticatortransport">W3C WebAuthn Spec</a>
 */
@Serializable
internal enum class AuthenticatorTransport {
    @SerialName("ble")
    BLE,

    @SerialName("nfc")
    NFC,

    @SerialName("internal")
    PLATFORM,

    @SerialName("usb")
    USB,

    @SerialName("smart-card")
    SMART_CARD,

    @SerialName("hybrid")
    HYBRID,
}

/**
 * A hint about the user verification requirement for the ceremony.
 *
 * @see <a href="https://www.w3.org/TR/webauthn-3/#enumdef-userverificationrequirement">W3C WebAuthn Spec</a>
 */
@Serializable
internal enum class UserVerificationRequirement {
    @SerialName("required")
    REQUIRED,

    @SerialName("preferred")
    PREFERRED,

    @SerialName("discouraged")
    DISCOURAGED,
}

/**
 * Hints about the preferred authenticator type for the ceremony.
 *
 * @see <a href="https://www.w3.org/TR/webauthn-3/#enumdef-publickeycredentialhints">W3C WebAuthn Spec</a>
 */
@Serializable
internal enum class PublicKeyCredentialHints {
    @SerialName("security-key")
    SECURITY_KEY,

    @SerialName("client-device")
    CLIENT_DEVICE,

    @SerialName("hybrid")
    HYBRID,
}
