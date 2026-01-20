package com.okta.directauth

import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.http.DirectAuthOobAuthenticateRequest
import com.okta.directauth.http.DirectAuthStartRequest
import com.okta.directauth.http.DirectAuthTokenRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.oobResponseAsState
import com.okta.directauth.http.tokenResponseAsState
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DirectAuthenticationFlowImpl(internal val context: DirectAuthenticationContext) : DirectAuthenticationFlow {

    private fun PrimaryFactor.startRequest(loginHint: String): DirectAuthStartRequest =
        when (this) {
            is PrimaryFactor.Password -> DirectAuthTokenRequest.Password(context, loginHint, password)
            is PrimaryFactor.Oob -> DirectAuthOobAuthenticateRequest(context, loginHint, channel)
            is PrimaryFactor.Otp -> DirectAuthTokenRequest.Otp(context, loginHint, passCode)
            PrimaryFactor.WebAuthn -> TODO("/oauth2/v1/primary-authenticate")
        }

    override val authenticationState: StateFlow<DirectAuthenticationState> = context.authenticationStateFlow.asStateFlow()

    override suspend fun start(loginHint: String, primaryFactor: PrimaryFactor): DirectAuthenticationState {
        val result = runCatching {
            val request = primaryFactor.startRequest(loginHint)
            val response = context.apiExecutor.execute(request).getOrThrow()
            when (request) {
                is DirectAuthTokenRequest -> response.tokenResponseAsState(context)
                is DirectAuthOobAuthenticateRequest -> response.oobResponseAsState(context)
            }
        }.getOrElse { InternalError(EXCEPTION, it.message, it) }

        context.authenticationStateFlow.value = result

        return result
    }

    override fun reset(): DirectAuthenticationState {
        context.authenticationStateFlow.value = DirectAuthenticationState.Idle
        return DirectAuthenticationState.Idle
    }
}
