package com.okta.directauth.model

import com.okta.authfoundation.ChallengeGrantType

internal data class BindingContext(
    val oobCode: String,
    val expiresIn: Int,
    val interval: Int?,
    val channel: OobChannel,
    val bindingMethod: BindingMethod,
    val bindingCode: String?,
    val challengeType: ChallengeGrantType?,
) {
    constructor(challengeType: ChallengeGrantType, bindingMethod: BindingMethod)
        : this("", -1, null, OobChannel.PUSH, bindingMethod, null, challengeType)
}
