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
package com.okta.directauth.http.handlers

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.ChallengeGrantType.OobMfa
import com.okta.authfoundation.ChallengeGrantType.OtpMfa
import com.okta.authfoundation.ChallengeGrantType.WebAuthnMfa
import com.okta.authfoundation.api.http.ApiResponse
import com.okta.directauth.http.INVALID_RESPONSE
import com.okta.directauth.http.UNEXPECTED_HTTP_STATUS
import com.okta.directauth.http.model.ApiErrorResponse
import com.okta.directauth.http.model.DirectAuthenticationErrorResponse
import com.okta.directauth.http.model.ErrorResponse
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError.ApiError
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationState
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

// helper to convert ErrorResponse to ApiError
internal fun ErrorResponse.toApiError(httpStatusCode: HttpStatusCode): ApiError = ApiError(errorCode, errorSummary, errorLink, errorId, errorCauses?.map { it.errorSummary }, httpStatusCode)

// helper to convert a string to ChallengeGrantType
internal fun String.asChallengeGrantType(): ChallengeGrantType =
    when (this) {
        OobMfa.value -> OobMfa
        OtpMfa.value -> OtpMfa
        WebAuthnMfa.value -> WebAuthnMfa
        else -> throw IllegalArgumentException("Unsupported challenge_grant_type: $this")
    }

internal fun ApiResponse.handleErrorResponse(
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
