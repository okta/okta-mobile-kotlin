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
package com.okta.directauth.app.model

import com.okta.authfoundation.client.TokenInfo

/**
 * State within a single OAuth2 flow execution.
 *
 * Managed by [com.okta.directauth.app.viewModel.OAuth2FlowViewModel] and observed by
 * OAuth2 flow screens to render the appropriate UI.
 */
sealed class OAuth2FlowState {
    data object Idle : OAuth2FlowState()

    data object Loading : OAuth2FlowState()

    data class DeviceAuthPolling(
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val expiresIn: Int,
    ) : OAuth2FlowState()

    data object BrowserAuthWaiting : OAuth2FlowState()

    data class Authenticated(
        val tokenInfo: TokenInfo,
    ) : OAuth2FlowState()

    data class Error(
        val message: String,
    ) : OAuth2FlowState()
}
