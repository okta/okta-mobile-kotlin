package com.okta.directauth.http.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChallengeResponse(
    @SerialName("challenge_type") val challengeType: String,
    @SerialName("oob_code") val oobCode: String? = null,
    val channel: String? = null,
    @SerialName("binding_method") val bindingMethod: String? = null,
    @SerialName("binding_code") val bindingCode: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val interval: Int? = null,
)