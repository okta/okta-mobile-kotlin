package com.okta.directauth.http.handlers

import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationState

internal interface StepHandler {
    val request: ApiFormRequest

    val context: DirectAuthenticationContext

    suspend fun process(): DirectAuthenticationState
}
