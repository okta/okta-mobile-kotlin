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
package com.okta.directauth

import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.http.DirectAuthOobAuthenticateRequest
import com.okta.directauth.http.DirectAuthTokenRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.handlers.OobStepHandler
import com.okta.directauth.http.handlers.StepHandler
import com.okta.directauth.http.handlers.TokenStepHandler
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

internal class DirectAuthenticationFlowImpl(
    internal val context: DirectAuthenticationContext,
) : DirectAuthenticationFlow {
    private fun PrimaryFactor.startRequest(loginHint: String): StepHandler =
        when (this) {
            is PrimaryFactor.Password -> TokenStepHandler(DirectAuthTokenRequest.Password(context, loginHint, password), context)
            is PrimaryFactor.Oob -> OobStepHandler(DirectAuthOobAuthenticateRequest(context, loginHint, channel), context)
            is PrimaryFactor.Otp -> TokenStepHandler(DirectAuthTokenRequest.Otp(context, loginHint, passCode), context)
            PrimaryFactor.WebAuthn -> TODO("/oauth2/v1/primary-authenticate")
        }

    override val authenticationState: StateFlow<DirectAuthenticationState> = context.authenticationStateFlow.asStateFlow()

    override suspend fun start(
        loginHint: String,
        primaryFactor: PrimaryFactor,
    ): DirectAuthenticationState {
        val result =
            runCatching { primaryFactor.startRequest(loginHint).process() }.getOrElse {
                if (it is CancellationException) {
                    DirectAuthenticationState.Canceled
                } else {
                    DirectAuthenticationError.InternalError(EXCEPTION, it.message, it)
                }
            }
        context.authenticationStateFlow.value = result
        return result
    }

    override fun reset(): DirectAuthenticationState {
        context.authenticationStateFlow.value = DirectAuthenticationState.Idle
        return DirectAuthenticationState.Idle
    }
}
