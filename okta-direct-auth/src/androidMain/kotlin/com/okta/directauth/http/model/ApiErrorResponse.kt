package com.okta.directauth.http.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ApiErrorResponse

@Serializable
internal data class DirectAuthenticationErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("mfa_token") val mfaToken: String? = null,
) : ApiErrorResponse

@Serializable
internal data class ErrorResponse(
    val errorCode: String,
    val errorSummary: String? = null,
    val errorLink: String? = null,
    val errorId: String? = null,
    val errorCauses: List<Cause>? = null,
) : ApiErrorResponse

@Serializable
internal class Cause(val errorSummary: String)