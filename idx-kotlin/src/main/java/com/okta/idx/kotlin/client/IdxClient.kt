/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.client

import android.net.Uri
import com.okta.idx.kotlin.dto.IdxRedirectResult
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.idx.kotlin.dto.TokenResponse
import com.okta.idx.kotlin.dto.v1.InteractResponse
import com.okta.idx.kotlin.dto.v1.IntrospectRequest
import com.okta.idx.kotlin.dto.v1.Token
import com.okta.idx.kotlin.dto.v1.toIdxResponse
import com.okta.idx.kotlin.util.PkceGenerator
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resumeWithException
import com.okta.idx.kotlin.dto.v1.Response as V1Response

/**
 * The IdxClient class is used to define and initiate an authentication workflow utilizing the Okta Identity Engine.
 */
class IdxClient internal constructor(
    private val configuration: IdxClientConfiguration,
    val clientContext: IdxClientContext,
) {
    companion object {
        /**
         * Used to create an IdxClient, and to start an authorization flow.
         */
        suspend fun start(configuration: IdxClientConfiguration): IdxClientResult<IdxClient> {
            val codeVerifier: String
            val state: String
            val request: Request

            withContext(configuration.computationDispatcher) {
                codeVerifier = PkceGenerator.codeVerifier()
                val codeChallenge = PkceGenerator.codeChallenge(codeVerifier)
                state = UUID.randomUUID().toString()
                val urlBuilder = configuration.issuer.newBuilder()
                    .addPathSegments("v1/interact")

                val formBody = FormBody.Builder()
                    .add("client_id", configuration.clientId)
                    .add("scope", configuration.scopes.joinToString(separator = " "))
                    .add("code_challenge", codeChallenge)
                    .add("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
                    .add("redirect_uri", configuration.redirectUri)
                    .add("state", state)
                    .build()

                request = Request.Builder()
                    .url(urlBuilder.build())
                    .post(formBody)
                    .build()
            }

            return configuration.performRequest<InteractResponse, IdxClient>(request) {
                val clientContext = IdxClientContext(
                    codeVerifier = codeVerifier,
                    interactionHandle = it.interactionHandle,
                    state = state,
                )
                IdxClient(
                    configuration = configuration,
                    clientContext = clientContext,
                )
            }
        }
    }

    /**
     * Resumes the authentication state to identify the available remediation steps.
     *
     * This method is usually performed after an IdxClient is created, but can also be called at any time to identify what next remediation steps are available to the user.
     */
    suspend fun resume(): IdxClientResult<IdxResponse> {
        val request: Request

        withContext(configuration.computationDispatcher) {
            val urlBuilder = configuration.issuer.newBuilder()
                .encodedPath("/idp/idx/introspect")

            val introspectRequest = IntrospectRequest(clientContext.interactionHandle)
            val jsonBody = configuration.json.encodeToString(introspectRequest)

            request = Request.Builder()
                .url(urlBuilder.build())
                .post(jsonBody.toRequestBody("application/ion+json; okta-version=1.0.0".toMediaType()))
                .build()
        }

        return configuration.performRequest(request, V1Response::toIdxResponse)
    }

    /**
     * Executes the remediation option and proceeds through the workflow using the supplied form parameters.
     *
     * This method is used to proceed through the authentication flow, using the data assigned to the nested fields' `value` to make selections.
     */
    suspend fun proceed(remediation: IdxRemediation): IdxClientResult<IdxResponse> {
        val request: Request

        withContext(configuration.computationDispatcher) {
            val urlBuilder = remediation.href.newBuilder()

            val requestBuilder = Request.Builder()
                .url(urlBuilder.build())

            if (remediation.method == "POST") {
                val jsonBody = remediation.toJsonContent().toString()
                requestBuilder.post(jsonBody.toRequestBody("application/ion+json; okta-version=1.0.0".toMediaType()))
            }

            remediation.accepts?.let {
                requestBuilder.addHeader("accept", it)
            }

            request = requestBuilder
                .build()
        }

        return configuration.performRequest(request, V1Response::toIdxResponse)
    }

    /**
     * Exchange the IdxRemediation.Type.ISSUE remediation type for tokens.
     */
    suspend fun exchangeCodes(remediation: IdxRemediation): IdxClientResult<TokenResponse> {
        if (remediation.type != IdxRemediation.Type.ISSUE) {
            throw IllegalStateException("Invalid remediation.")
        }
        val request: Request

        withContext(configuration.computationDispatcher) {
            remediation["code_verifier"]?.value = clientContext.codeVerifier

            val formBodyBuilder = FormBody.Builder()
            remediation.form.allFields.forEach { field ->
                val value = when (val fieldValue = field.value) {
                    is JsonPrimitive -> fieldValue.content
                    null -> ""
                    else -> fieldValue.toString()
                }
                if (field.name != null) {
                    formBodyBuilder.add(field.name, value)
                }
            }

            request = Request.Builder()
                .url(remediation.href)
                .post(formBodyBuilder.build())
                .build()
        }

        return configuration.performRequest(request, Token::toIdxResponse)
    }

    /**
     * Evaluates the given redirect url to determine what next steps can be performed. This is usually used when receiving a redirection from an IDP authentication flow.
     */
    suspend fun redirectResult(uri: Uri): IdxRedirectResult {
        val errorQueryParameter = uri.getQueryParameter("error")
        val stateQueryParameter = uri.getQueryParameter("state")
        if (errorQueryParameter == "interaction_required") {
            // Validate the state matches. This is a security assurance.
            if (clientContext.state != stateQueryParameter) {
                val error = "IDP redirect failed due to state mismatch."
                return IdxRedirectResult.Error(error)
            }
            return when (val resumeResult = resume()) {
                is IdxClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to resume.", resumeResult.exception)
                }
                is IdxClientResult.Response -> {
                    IdxRedirectResult.InteractionRequired(resumeResult.response)
                }
            }
        }
        if (errorQueryParameter != null) {
            val errorDescription =
                uri.getQueryParameter("error_description") ?: "An error occurred."
            return IdxRedirectResult.Error(errorDescription)
        }
        val interactionCodeQueryParameter = uri.getQueryParameter("interaction_code")
        if (interactionCodeQueryParameter != null) {
            // Validate the state matches. This is a security assurance.
            if (clientContext.state != stateQueryParameter) {
                val error = "IDP redirect failed due to state mismatch."
                return IdxRedirectResult.Error(error)
            }
            return when (val result = exchangeCodes(interactionCodeQueryParameter)) {
                is IdxClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to exchangeCodes.", result.exception)
                }
                is IdxClientResult.Response -> {
                    IdxRedirectResult.Tokens(result.response)
                }
            }
        }
        return IdxRedirectResult.Error("Unable to handle redirect url.")
    }

    private suspend fun exchangeCodes(interactionCode: String): IdxClientResult<TokenResponse> {
        val request: Request
        withContext(configuration.computationDispatcher) {
            val formBodyBuilder = FormBody.Builder()
                .add("grant_type", "interaction_code")
                .add("client_id", configuration.clientId)
                .add("interaction_code", interactionCode)
                .add("code_verifier", clientContext.codeVerifier)

            val url = configuration.issuer.newBuilder()
                .addPathSegments("v1/token")
                .build()

            request = Request.Builder()
                .url(url)
                .post(formBodyBuilder.build())
                .build()
        }

        return configuration.performRequest(request, Token::toIdxResponse)
    }
}

internal fun IdxRemediation.toJsonContent(): JsonElement {
    return form.toJsonContent()
}

private fun IdxRemediation.Form.toJsonContent(): JsonElement {
    val result = mutableMapOf<String, JsonElement>()

    for (field in allFields) {
        val name = field.name ?: continue
        field.form?.toJsonContent()?.let {
            result[name] = it
        }
        field.value?.asJsonElement()?.let {
            result[name] = it
        }
        field.selectedOption?.selectedOptionToJsonContent()?.let {
            result[name] = it
        }
    }

    return JsonObject(result)
}

private fun Any?.asJsonElement(): JsonElement {
    return when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        else -> throw IllegalStateException("Unknown type")
    }
}

private fun IdxRemediation.Form.Field.selectedOptionToJsonContent(): JsonElement? {
    val localValue = value ?: return null
    if (localValue is JsonPrimitive) {
        return localValue
    }
    val jsonObject = localValue as? JsonObject ?: return null
    val formElement = jsonObject["form"] as? JsonObject ?: return null
    val values = formElement["value"] as? JsonArray ?: return null

    val result = mutableMapOf<String, JsonElement>()
    values.forEach { valueJson ->
        val valueJsonObject = valueJson as? JsonObject
        if (valueJsonObject?.containsKey("name") == true && valueJsonObject.containsKey("value")) {
            val name = valueJsonObject["name"] as? JsonPrimitive
            val value = valueJsonObject["value"]
            if (name != null && value != null) {
                result[name.content] = value
            }
        }
    }

    return JsonObject(result)
}

private suspend inline fun <reified Raw, Dto> IdxClientConfiguration.performRequest(
    request: Request,
    crossinline responseMapper: (Raw) -> Dto
): IdxClientResult<Dto> {
    return withContext(ioDispatcher) {
        try {
            val okHttpResponse = okHttpCallFactory.newCall(request).await()
            val responseBody = okHttpResponse.body!!.string()
            val rawResponse = json.decodeFromString<Raw>(responseBody)
            IdxClientResult.Response(responseMapper(rawResponse))
        } catch (e: Exception) {
            IdxClientResult.Error(e)
        }
    }
}

private suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        val callback = ContinuationCallback(this, continuation)
        enqueue(callback)
        continuation.invokeOnCancellation(callback)
    }
}

private class ContinuationCallback(
    private val call: Call,
    private val continuation: CancellableContinuation<Response>
) : Callback, CompletionHandler {

    @ExperimentalCoroutinesApi
    override fun onResponse(call: Call, response: Response) {
        continuation.resume(response, this)
    }

    override fun onFailure(call: Call, e: IOException) {
        if (!call.isCanceled()) {
            continuation.resumeWithException(e)
        }
    }

    override fun invoke(cause: Throwable?) {
        try {
            call.cancel()
        } catch (_: Throwable) {
        }
    }
}
