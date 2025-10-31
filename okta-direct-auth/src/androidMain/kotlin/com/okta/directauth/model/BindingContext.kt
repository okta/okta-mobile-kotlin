package com.okta.directauth.model

internal data class BindingContext(
    val oobCode: String,
    val expiresIn: Int,
    val interval: Int?,
    val channel: OobChannel,
    val bindingMethod: BindingMethod,
    val bindingCode: String?,
)
