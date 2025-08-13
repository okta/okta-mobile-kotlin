package com.okta.directauth.http

import com.okta.directauth.model.DirectAuthenticationContext

sealed class DirectAuthOobAuthenticateRequest(internal val context: DirectAuthenticationContext) : DirectAuthRequest {
    // TODO: Implement OOB Authenticate Request
}