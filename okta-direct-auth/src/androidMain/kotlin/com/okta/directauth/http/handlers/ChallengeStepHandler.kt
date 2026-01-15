package com.okta.directauth.http.handlers

import com.okta.authfoundation.ChallengeGrantType.OobMfa
import com.okta.authfoundation.ChallengeGrantType.OtpMfa
import com.okta.authfoundation.ChallengeGrantType.WebAuthnMfa
import com.okta.directauth.http.DirectAuthChallengeRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.INVALID_RESPONSE
import com.okta.directauth.http.UNSUPPORTED_CONTENT_TYPE
import com.okta.directauth.http.model.ChallengeResponse
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

internal class ChallengeStepHandler(override val request: DirectAuthChallengeRequest, override val context: DirectAuthenticationContext, val mfaContext: MfaContext) : StepHandler {
    override suspend fun process(): DirectAuthenticationState = runCatching {
        val apiResponse = context.apiExecutor.execute(request).getOrThrow()

        if (apiResponse.contentType != "application/json") {
            return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: ${apiResponse.contentType}"))
        }

        val httpStatusCode = HttpStatusCode.fromValue(apiResponse.statusCode)

        if (httpStatusCode == HttpStatusCode.OK) {
            val response = apiResponse.body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
            val challengeResponse = context.json.decodeFromString<ChallengeResponse>(response.toString(Charsets.UTF_8))

            val challengeType = challengeResponse.challengeType.asChallengeGrantType()
            val bindingContext = with(challengeResponse) {
                when (challengeType) {
                    OobMfa -> {
                        val oobChannel = OobChannel.fromString(requireNotNull(channel))
                        val bindingMethod = BindingMethod.fromString(requireNotNull(bindingMethod))
                        if (bindingMethod == BindingMethod.TRANSFER) requireNotNull(bindingCode)
                        if (oobChannel == OobChannel.PUSH) requireNotNull(interval)

                        BindingContext(requireNotNull(oobCode), requireNotNull(expiresIn), interval, oobChannel, bindingMethod, bindingCode, challengeType)
                    }

                    OtpMfa -> BindingContext(challengeType, BindingMethod.PROMPT)

                    WebAuthnMfa -> TODO("https://oktainc.atlassian.net/browse/OKTA-1054126")
                }
            }

            return when (bindingContext.bindingMethod) {
                BindingMethod.NONE -> DirectAuthContinuation.OobPending(bindingContext, context, mfaContext)
                BindingMethod.PROMPT -> DirectAuthContinuation.Prompt(bindingContext, context, mfaContext)
                BindingMethod.TRANSFER -> DirectAuthContinuation.Transfer(bindingContext, context, mfaContext)
            }
        }

        return apiResponse.handleErrorResponse(context, httpStatusCode) { apiError ->
            val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)
            Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
        }

    }.getOrElse {
        if (it is CancellationException) Canceled
        else InternalError(EXCEPTION, it.message, it)
    }
}