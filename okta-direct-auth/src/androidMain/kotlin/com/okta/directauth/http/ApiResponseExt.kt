package com.okta.directauth.http

import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Token
import com.okta.directauth.http.model.ApiErrorResponse
import com.okta.directauth.http.model.DirectAuthenticationErrorResponse
import com.okta.directauth.http.model.ErrorResponse
import com.okta.directauth.http.model.TokenResponse
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError.ApiError
import com.okta.directauth.model.DirectAuthenticationError.HttpError.Oauth2Error
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationErrorCode
import com.okta.directauth.model.DirectAuthenticationErrorCode.AUTHORIZATION_PENDING
import com.okta.directauth.model.DirectAuthenticationErrorCode.MFA_REQUIRED
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.DirectAuthenticationState.Authenticated
import com.okta.directauth.model.DirectAuthenticationState.AuthorizationPending
import com.okta.directauth.model.DirectAuthenticationState.MfaRequired
import com.okta.directauth.model.MfaContext
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun ApiResponse.tokenResponseAsState(context: DirectAuthenticationContext): DirectAuthenticationState = runCatching {
    if (contentType != "application/json") return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: $contentType"))

    val httpStatusCode = HttpStatusCode.fromValue(statusCode)

    return when {
        httpStatusCode == HttpStatusCode.OK -> {
            val response = body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
            val tokenResponse = context.json.decodeFromString<TokenResponse>(response.toString(Charsets.UTF_8))
            Authenticated(
                Token(
                    id = Uuid.random().toString(),
                    tokenType = tokenResponse.tokenType,
                    expiresIn = tokenResponse.expiresIn,
                    accessToken = tokenResponse.accessToken,
                    scope = tokenResponse.scope,
                    refreshToken = tokenResponse.refreshToken,
                    idToken = tokenResponse.idToken,
                    deviceSecret = tokenResponse.deviceSecret,
                    issuedTokenType = null, // This is not returned in the token response
                    oidcConfiguration = OidcConfiguration(context.clientId, context.scope.joinToString(" "), context.issuerUrl)
                )
            )
        }

        httpStatusCode.value in 400..499 -> {
            val apiError: ApiErrorResponse? = body?.takeIf { it.isNotEmpty() }?.let { response ->
                val jsonElement = context.json.parseToJsonElement(response.toString(Charsets.UTF_8))

                if ("error" in jsonElement.jsonObject) {
                    context.json.decodeFromJsonElement<DirectAuthenticationErrorResponse>(jsonElement)
                } else if ("errorCode" in jsonElement.jsonObject) {
                    context.json.decodeFromJsonElement<ErrorResponse>(jsonElement)
                } else null
            }

            when (apiError) {
                is DirectAuthenticationErrorResponse -> {
                    val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)
                    when (errorCode) {
                        MFA_REQUIRED -> apiError.mfaToken?.let { mfaToken ->
                            MfaRequired(context, MfaContext(context.grantTypes, mfaToken))
                        } ?: InternalError(INVALID_RESPONSE, null, IllegalStateException("No mfa_token found in body: HTTP $httpStatusCode"))

                        AUTHORIZATION_PENDING -> AuthorizationPending
                        else -> Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
                    }
                }

                is ErrorResponse -> apiError.toApiError(httpStatusCode)

                else -> InternalError(INVALID_RESPONSE, null, IllegalStateException("No parsable error response body: HTTP $httpStatusCode"))
            }
        }

        httpStatusCode.value in 500..599 -> {
            val apiError = body?.takeIf { it.isNotEmpty() }?.let { response ->
                context.json.decodeFromString<ErrorResponse>(response.toString(Charsets.UTF_8))
            } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("No parsable error response body: HTTP $httpStatusCode"))

            apiError.toApiError(httpStatusCode)
        }

        else -> InternalError(UNEXPECTED_HTTP_STATUS, null, IllegalStateException("Unexpected HTTP Status Code: $httpStatusCode"))
    }
}.getOrElse {
    InternalError(UNKNOWN_ERROR, it.message, it)
}

// helper to convert ErrorResponse to ApiError
private fun ErrorResponse.toApiError(httpStatusCode: HttpStatusCode): ApiError =
    ApiError(errorCode, errorSummary, errorLink, errorId, errorCauses?.map { it.errorSummary }, httpStatusCode)

