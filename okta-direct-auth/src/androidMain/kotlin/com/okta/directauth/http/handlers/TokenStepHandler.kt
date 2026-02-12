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

import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.credential.Token
import com.okta.directauth.http.DirectAuthTokenRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.INVALID_RESPONSE
import com.okta.directauth.http.UNSUPPORTED_CONTENT_TYPE
import com.okta.directauth.http.model.TokenResponse
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError.Oauth2Error
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationErrorCode
import com.okta.directauth.model.DirectAuthenticationErrorCode.AUTHORIZATION_PENDING
import com.okta.directauth.model.DirectAuthenticationErrorCode.MFA_REQUIRED
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.DirectAuthenticationState.Authenticated
import com.okta.directauth.model.DirectAuthenticationState.AuthorizationPending
import com.okta.directauth.model.DirectAuthenticationState.Canceled
import com.okta.directauth.model.DirectAuthenticationState.MfaRequired
import com.okta.directauth.model.MfaContext
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class TokenStepHandler(
    override val request: DirectAuthTokenRequest,
    override val context: DirectAuthenticationContext,
) : StepHandler {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun process(): DirectAuthenticationState =
        runCatching {
            val apiResponse = context.apiExecutor.execute(request).getOrThrow()

            if (apiResponse.contentType != "application/json") {
                return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: ${apiResponse.contentType}"))
            }

            val httpStatusCode = HttpStatusCode.fromValue(apiResponse.statusCode)

            if (httpStatusCode == HttpStatusCode.OK) {
                val response = apiResponse.body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
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

            return apiResponse.handleErrorResponse(context, httpStatusCode) { apiError ->
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
            if (it is CancellationException) {
                Canceled
            } else {
                InternalError(EXCEPTION, it.message, it)
            }
        }
}
