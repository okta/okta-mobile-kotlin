/*
 * Copyright 2022-Present Okta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.okta.directauth.http

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.ChallengeGrantType.OobMfa
import com.okta.authfoundation.ChallengeGrantType.OtpMfa
import com.okta.authfoundation.ChallengeGrantType.WebAuthnMfa
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Token
import com.okta.directauth.http.model.ApiErrorResponse
import com.okta.directauth.http.model.ChallengeResponse
import com.okta.directauth.http.model.DirectAuthenticationErrorResponse
import com.okta.directauth.http.model.ErrorResponse
import com.okta.directauth.http.model.OobAuthenticateResponse
import com.okta.directauth.http.model.TokenResponse
import com.okta.directauth.model.BindingContext
import com.okta.directauth.model.BindingMethod
import com.okta.directauth.model.DirectAuthContinuation
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
import com.okta.directauth.model.OobChannel
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun ApiResponse.tokenResponseAsState(context: DirectAuthenticationContext): DirectAuthenticationState =
    runCatching {
        if (contentType != "application/json") {
            return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: $contentType"))
        }

        val httpStatusCode = HttpStatusCode.fromValue(statusCode)

        if (httpStatusCode == HttpStatusCode.OK) {
            val response = body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
            val tokenResponse = context.json.decodeFromString<TokenResponse>(response.toString(Charsets.UTF_8))
            return Authenticated(
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

        return handleErrorResponse(context, httpStatusCode) { apiError ->
            when (val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)) {
                MFA_REQUIRED -> {
                    apiError.mfaToken?.let { mfaToken ->
                        MfaRequired(context, MfaContext(context.grantTypes, mfaToken))
                    } ?: InternalError(INVALID_RESPONSE, null, IllegalStateException("No mfa_token found in body: HTTP $httpStatusCode"))
                }

                AUTHORIZATION_PENDING -> {
                    AuthorizationPending(context.clock.currentTimeEpochSecond())
                }

                else -> {
                    Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
                }
            }
        }
    }.getOrElse {
        InternalError(UNKNOWN_ERROR, it.message, it)
    }

internal fun ApiResponse.oobResponseAsState(context: DirectAuthenticationContext): DirectAuthenticationState =
    runCatching {
        if (contentType != "application/json") {
            return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: $contentType"))
        }

        val httpStatusCode = HttpStatusCode.fromValue(statusCode)

        if (httpStatusCode == HttpStatusCode.OK) {
            val response = body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
            val oobResponse = context.json.decodeFromString<OobAuthenticateResponse>(response.toString(Charsets.UTF_8))

            val bindingMethod = BindingMethod.fromString(oobResponse.bindingMethod)
            if (bindingMethod == BindingMethod.TRANSFER && oobResponse.bindingCode == null) throw IllegalStateException("binding_method: transfer without binding_code")

            val bindingContext =
                BindingContext(
                    oobResponse.oobCode,
                    oobResponse.expiresIn,
                    oobResponse.interval,
                    OobChannel.fromString(oobResponse.channel),
                    bindingMethod,
                    oobResponse.bindingCode,
                    null
                )
            return when (bindingContext.bindingMethod) {
                BindingMethod.NONE -> DirectAuthContinuation.OobPending(bindingContext, context)
                BindingMethod.PROMPT -> DirectAuthContinuation.Prompt(bindingContext, context)
                BindingMethod.TRANSFER -> DirectAuthContinuation.Transfer(bindingContext, context)
            }
        }

        return handleErrorResponse(context, httpStatusCode) { apiError ->
            val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)
            Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
        }
    }.getOrElse {
        InternalError(UNKNOWN_ERROR, it.message, it)
    }

internal fun ApiResponse.challengeResponseAsState(
    context: DirectAuthenticationContext,
    mfaContext: MfaContext,
): DirectAuthenticationState =
    runCatching {
        if (contentType != "application/json") {
            return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: $contentType"))
        }

        val httpStatusCode = HttpStatusCode.fromValue(statusCode)

        if (httpStatusCode == HttpStatusCode.OK) {
            val response = body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
            val challengeResponse = context.json.decodeFromString<ChallengeResponse>(response.toString(Charsets.UTF_8))

            val challengeType = challengeResponse.challengeType.asChallengeGrantType()
            val bindingContext =
                with(challengeResponse) {
                    when (challengeType) {
                        OobMfa -> {
                            val oobChannel = OobChannel.fromString(requireNotNull(channel))
                            val bindingMethod = BindingMethod.fromString(requireNotNull(bindingMethod))
                            if (bindingMethod == BindingMethod.TRANSFER) requireNotNull(bindingCode)
                            if (oobChannel == OobChannel.PUSH) requireNotNull(interval)

                            BindingContext(requireNotNull(oobCode), requireNotNull(expiresIn), interval, oobChannel, bindingMethod, bindingCode, challengeType)
                        }

                        OtpMfa -> {
                            BindingContext(challengeType, BindingMethod.PROMPT)
                        }

                        WebAuthnMfa -> {
                            TODO("https://oktainc.atlassian.net/browse/OKTA-1054126")
                        }
                    }
                }

            return when (bindingContext.bindingMethod) {
                BindingMethod.NONE -> DirectAuthContinuation.OobPending(bindingContext, context, mfaContext)
                BindingMethod.PROMPT -> DirectAuthContinuation.Prompt(bindingContext, context, mfaContext)
                BindingMethod.TRANSFER -> DirectAuthContinuation.Transfer(bindingContext, context, mfaContext)
            }
        }

        return handleErrorResponse(context, httpStatusCode) { apiError ->
            val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)
            Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
        }
    }.getOrElse {
        InternalError(UNKNOWN_ERROR, it.message, it)
    }

private fun ApiResponse.handleErrorResponse(
    context: DirectAuthenticationContext,
    httpStatusCode: HttpStatusCode,
    handleOAuth2Error: (DirectAuthenticationErrorResponse) -> DirectAuthenticationState,
): DirectAuthenticationState {
    return when (httpStatusCode.value) {
        in 400..499 -> {
            val apiError: ApiErrorResponse? =
                body?.takeIf { it.isNotEmpty() }?.let { response ->
                    val jsonElement = context.json.parseToJsonElement(response.toString(Charsets.UTF_8))

                    if ("error" in jsonElement.jsonObject) {
                        context.json.decodeFromJsonElement<DirectAuthenticationErrorResponse>(jsonElement)
                    } else if ("errorCode" in jsonElement.jsonObject) {
                        context.json.decodeFromJsonElement<ErrorResponse>(jsonElement)
                    } else {
                        null
                    }
                }

            when (apiError) {
                is DirectAuthenticationErrorResponse -> handleOAuth2Error(apiError)
                is ErrorResponse -> apiError.toApiError(httpStatusCode)
                else -> InternalError(INVALID_RESPONSE, null, IllegalStateException("No parsable error response body: HTTP $httpStatusCode"))
            }
        }

        in 500..599 -> {
            val apiError =
                body?.takeIf { it.isNotEmpty() }?.let { response ->
                    context.json.decodeFromString<ErrorResponse>(response.toString(Charsets.UTF_8))
                } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("No parsable error response body: HTTP $httpStatusCode"))

            apiError.toApiError(httpStatusCode)
        }

        else -> {
            InternalError(UNEXPECTED_HTTP_STATUS, null, IllegalStateException("Unexpected HTTP Status Code: $httpStatusCode"))
        }
    }
}

// helper to convert ErrorResponse to ApiError
private fun ErrorResponse.toApiError(httpStatusCode: HttpStatusCode): ApiError = ApiError(errorCode, errorSummary, errorLink, errorId, errorCauses?.map { it.errorSummary }, httpStatusCode)

// helper to convert a string to ChallengeGrantType
private fun String.asChallengeGrantType(): ChallengeGrantType =
    when (this) {
        OobMfa.value -> OobMfa
        OtpMfa.value -> OtpMfa
        WebAuthnMfa.value -> WebAuthnMfa
        else -> throw IllegalArgumentException("Unsupported challenge_grant_type: $this")
    }
