package com.okta.directauth.model

import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.client.OidcClock
import kotlinx.coroutines.flow.MutableStateFlow

internal data class DirectAuthenticationContext(
    val issuerUrl: String,
    val clientId: String,
    val scope: List<String>,
    val clientSecret: String,
    val grantTypes: List<GrantType>,
    val acrValues: List<String>,
    val directAuthenticationIntent: DirectAuthenticationIntent,
    val apiExecutor: ApiExecutor,
    val logger: AuthFoundationLogger,
    val clock: OidcClock,
    val additionalParameters: Map<String, String>,
) {
    val authenticationStateFlow: MutableStateFlow<DirectAuthenticationState> = MutableStateFlow(DirectAuthenticationState.Idle)
}
