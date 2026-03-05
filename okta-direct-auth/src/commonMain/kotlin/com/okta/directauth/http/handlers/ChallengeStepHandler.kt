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

import com.okta.authfoundation.ChallengeGrantType.OobMfa
import com.okta.authfoundation.ChallengeGrantType.OtpMfa
import com.okta.directauth.http.DirectAuthChallengeRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.INVALID_RESPONSE
import com.okta.directauth.http.model.ChallengeApiResponse
import com.okta.directauth.http.model.ChallengeResponse
import com.okta.directauth.http.model.WebAuthnChallengeResponse
import com.okta.directauth.model.BindingContext
import com.okta.directauth.model.BindingMethod
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError.Oauth2Error
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationErrorCode
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.DirectAuthenticationState.Canceled
import com.okta.directauth.model.MfaContext
import com.okta.directauth.model.OobChannel
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

internal class ChallengeStepHandler(
    override val request: DirectAuthChallengeRequest,
    override val context: DirectAuthenticationContext,
    val mfaContext: MfaContext,
) : StepHandler {
    override suspend fun process(): DirectAuthenticationState =
        runCatching {
            val apiResponse = context.apiExecutor.execute(request).getOrThrow()

            apiResponse.validateContentType(expectedContentType)?.let { return it }

            val httpStatusCode = HttpStatusCode.fromValue(apiResponse.statusCode)

            if (httpStatusCode == HttpStatusCode.OK) {
                val response = apiResponse.body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))

                when (val challengeApiResponse = context.json.decodeFromString<ChallengeApiResponse>(response.toString(Charsets.UTF_8))) {
                    is WebAuthnChallengeResponse -> {
                        return DirectAuthContinuation.WebAuthn(
                            webAuthnChallengeResponse = challengeApiResponse,
                            context = context,
                            mfaContext = mfaContext
                        )
                    }

                    is ChallengeResponse -> {
                        val challengeType = challengeApiResponse.challengeType.asChallengeGrantType()

                        val bindingContext =
                            with(challengeApiResponse) {
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

                                    else -> {
                                        error("Unsupported challenge type: $challengeType")
                                    }
                                }
                            }

                        return when (bindingContext.bindingMethod) {
                            BindingMethod.NONE -> DirectAuthContinuation.OobPending(bindingContext, context, mfaContext)
                            BindingMethod.PROMPT -> DirectAuthContinuation.Prompt(bindingContext, context, mfaContext)
                            BindingMethod.TRANSFER -> DirectAuthContinuation.Transfer(bindingContext, context, mfaContext)
                        }
                    }
                }
            }

            return apiResponse.handleErrorResponse(context, httpStatusCode) { apiError ->
                val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)
                Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
            }
        }.getOrElse {
            if (it is CancellationException) {
                Canceled
            } else {
                InternalError(EXCEPTION, it.message, it)
            }
        }
}
