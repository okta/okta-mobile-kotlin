package com.okta.directauth.http

import com.okta.authfoundation.api.http.ApiFormRequest

interface DirectAuthRequest : ApiFormRequest

sealed interface DirectAuthStartRequest : DirectAuthRequest