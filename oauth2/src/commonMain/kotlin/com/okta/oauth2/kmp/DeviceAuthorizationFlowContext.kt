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

/**
 * Holds the state for an in-progress [DeviceAuthorizationFlow] session.
 *
 * @property verificationUri the URI the user should visit on another device to authorize.
 * @property verificationUriComplete a convenience URI that includes the [userCode], or null if not provided.
 * @property userCode the short code to display to the user.
 * @property expiresIn seconds until this authorization session expires.
 */
data class DeviceAuthorizationFlowContext(
    val verificationUri: String,
    val verificationUriComplete: String?,
    val userCode: String,
    val expiresIn: Int,
    internal val deviceCode: String,
    internal val interval: Int,
)
