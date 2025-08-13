package com.okta.directauth

import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DirectAuthenticationFlowImpl(private val context: DirectAuthenticationContext) : DirectAuthenticationFlow {
    override val authenticationState: StateFlow<DirectAuthenticationState> = context.authenticationStateFlow.asStateFlow()

    override suspend fun start(loginHint: String, primaryFactor: PrimaryFactor): Result<DirectAuthenticationState> {
        TODO("Not yet implemented")
    }
}
