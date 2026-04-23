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
package com.okta.authfoundation.client.dto

/**
 * Response from a device authorization request per
 * [RFC 8628](https://datatracker.ietf.org/doc/html/rfc8628).
 *
 * @property verificationUri the URI the user should visit to authorize the device.
 * @property verificationUriComplete a convenience URI combining [verificationUri] and [userCode].
 * @property deviceCode the device verification code used to poll for tokens.
 * @property userCode the short code the user enters at [verificationUri].
 * @property interval the minimum polling interval in seconds.
 * @property expiresIn the lifetime in seconds of [deviceCode] and [userCode].
 */
data class DeviceAuthorizationInfo(
    val verificationUri: String,
    val verificationUriComplete: String?,
    val deviceCode: String,
    val userCode: String,
    val interval: Int,
    val expiresIn: Int,
)
