package com.okta.directauth.http.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OobAuthenticateResponse(
    @SerialName("oob_code") val oobCode: String,
    val channel: String,
    @SerialName("binding_method") val bindingMethod: String,
    @SerialName("binding_code") val bindingCode: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int? = null,
)