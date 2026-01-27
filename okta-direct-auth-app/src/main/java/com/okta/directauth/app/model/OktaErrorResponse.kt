package com.okta.directauth.app.model

import kotlinx.serialization.Serializable


@Serializable
data class Cause(val errorSummary: String)

@Serializable
data class OktaErrorResponse(
    val errorCode: String,
    val errorSummary: String? = null,
    val errorLink: String? = null,
    val errorId: String? = null,
    val errorCauses: List<Cause>? = null,
)