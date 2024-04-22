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

import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.jwt.Jwt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.toByteString

/**
 * Used for validating device secrets minted by an Authorization Server.
 */
fun interface DeviceSecretValidator {
    /**
     * An error used for describing errors when validating the token.
     */
    class Error(message: String) : IllegalStateException(message)

    /**
     * Called when the [OAuth2Client] receives a [Token] response.
     *
     * This should throw an [Exception] if the token is invalid.
     *
     * @param client the [OAuth2Client] that made the [Token] request.
     * @param deviceSecret the device secret from the [Token] response.
     * @param idToken the [Jwt] representing the id token from the [Token] response.
     */
    suspend fun validate(client: OAuth2Client, deviceSecret: String, idToken: Jwt)
}

internal class DefaultDeviceSecretValidator : DeviceSecretValidator {
    override suspend fun validate(client: OAuth2Client, deviceSecret: String, idToken: Jwt) {
        if (idToken.algorithm != "RS256") {
            throw DeviceSecretValidator.Error("Unsupported algorithm")
        }
        val expectedDeviceSecretHash = idToken.deserializeClaims(IdTokenDsHash.serializer()).dsHash ?: return
        val sha256 = deviceSecret.toByteArray(Charsets.US_ASCII).toByteString().sha256()
        val leftMost = sha256.substring(0, sha256.size / 2)
        val actualDeviceSecretHash = leftMost.base64Url().trimEnd('=')
        if (actualDeviceSecretHash != expectedDeviceSecretHash) {
            throw DeviceSecretValidator.Error("ID Token ds_hash didn't match the device secret.")
        }
    }
}

@Serializable
private class IdTokenDsHash(
    @SerialName("ds_hash") val dsHash: String? = null,
)
