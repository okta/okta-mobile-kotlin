package com.okta.directauth.http.handlers

import com.okta.directauth.http.DirectAuthOobAuthenticateRequest
import com.okta.directauth.http.EXCEPTION
import com.okta.directauth.http.INVALID_RESPONSE
import com.okta.directauth.http.UNSUPPORTED_CONTENT_TYPE
import com.okta.directauth.http.model.OobAuthenticateResponse
import com.okta.directauth.model.BindingContext
import com.okta.directauth.model.BindingMethod
import com.okta.directauth.model.DirectAuthContinuation
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationError.HttpError.Oauth2Error
import com.okta.directauth.model.DirectAuthenticationError.InternalError
import com.okta.directauth.model.DirectAuthenticationErrorCode
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.DirectAuthenticationState.Canceled
import com.okta.directauth.model.OobChannel
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException

internal class OobStepHandler(override val request: DirectAuthOobAuthenticateRequest, override val context: DirectAuthenticationContext) : StepHandler {
    override suspend fun process(): DirectAuthenticationState = runCatching {
        val apiResponse = context.apiExecutor.execute(request).getOrThrow()
        if (apiResponse.contentType != "application/json") {
            return InternalError(UNSUPPORTED_CONTENT_TYPE, null, IllegalStateException("Unsupported content type: ${apiResponse.contentType}"))
        }

        val httpStatusCode = HttpStatusCode.fromValue(apiResponse.statusCode)

        if (httpStatusCode == HttpStatusCode.OK) {
            val response = apiResponse.body?.takeIf { it.isNotEmpty() } ?: return InternalError(INVALID_RESPONSE, null, IllegalStateException("Empty response body: HTTP $httpStatusCode"))
            val oobResponse = context.json.decodeFromString<OobAuthenticateResponse>(response.toString(Charsets.UTF_8))

            val bindingMethod = BindingMethod.fromString(oobResponse.bindingMethod)
            if (bindingMethod == BindingMethod.TRANSFER && oobResponse.bindingCode == null) throw IllegalStateException("binding_method: transfer without binding_code")

            val bindingContext = BindingContext(
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

        return apiResponse.handleErrorResponse(context, httpStatusCode) { apiError ->
            val errorCode = DirectAuthenticationErrorCode.fromString(apiError.error)
            Oauth2Error(errorCode.code, httpStatusCode, apiError.errorDescription)
        }
    }.getOrElse {
        if (it is CancellationException) Canceled
        else InternalError(EXCEPTION, it.message, it)
    }
}