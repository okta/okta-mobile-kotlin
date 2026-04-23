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
package com.okta.authfoundation.client.internal

import com.okta.authfoundation.client.dto.DeviceAuthorizationInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class SerializableDeviceAuthorizationResponse(
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("interval") val interval: Int = 5,
    @SerialName("expires_in") val expiresIn: Int,
) {
    fun toDeviceAuthorizationInfo(): DeviceAuthorizationInfo =
        DeviceAuthorizationInfo(
            verificationUri = verificationUri,
            verificationUriComplete = verificationUriComplete,
            deviceCode = deviceCode,
            userCode = userCode,
            interval = interval,
            expiresIn = expiresIn
        )
}
