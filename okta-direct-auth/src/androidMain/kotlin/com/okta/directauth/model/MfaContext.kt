package com.okta.directauth.model

import com.okta.authfoundation.GrantType

internal data class MfaContext(val supportedChallengeTypes: List<GrantType>, val mfaToken: String)
