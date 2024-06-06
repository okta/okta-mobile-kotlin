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
package com.okta.idx.kotlin.dto.v1

import com.okta.authfoundation.client.OAuth2Client
import com.okta.idx.kotlin.client.InteractionCodeFlowContext
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.util.PkceGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

internal fun IdxRemediation.asJsonRequest(client: OAuth2Client): Request {
    val requestBuilder = Request.Builder().url(href)

    if (method == "POST") {
        val jsonBody = client.configuration.json.encodeToString(toJsonContent())
        requestBuilder.post(jsonBody.toRequestBody("application/ion+json; okta-version=1.0.0".toMediaType()))
    }

    accepts?.let {
        requestBuilder.addHeader("accept", it)
    }

    return requestBuilder.build()
}

internal fun IdxRemediation.toJsonContent(): JsonElement {
    return form.toJsonContent()
}

private fun IdxRemediation.Form.toJsonContent(): JsonElement {
    val result = mutableMapOf<String, JsonElement>()

    for (field in allFields) {
        val name = field.name ?: continue
        val value = field.toJsonContent() ?: continue
        result[name] = value
    }

    return JsonObject(result)
}

private fun IdxRemediation.Form.Field.toJsonContent(): JsonElement? {
    value?.asJsonElement()?.let { return it }
    selectedOption?.toJsonContent()?.let { return it }
    form?.toJsonContent()?.let { return it }
    return null
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

internal fun IdxRemediation.asFormRequest(): Request {
    val formBodyBuilder = FormBody.Builder()
    form.allFields.forEach { field ->
        val value = when (val fieldValue = field.value) {
            is JsonPrimitive -> fieldValue.content
            null -> ""
            else -> fieldValue.toString()
        }
        if (field.name != null) {
            formBodyBuilder.add(field.name, value)
        }
    }

    return Request.Builder()
        .url(href)
        .post(formBodyBuilder.build())
        .build()
}

internal suspend fun tokenRequestFromInteractionCode(
    client: OAuth2Client,
    flowContext: InteractionCodeFlowContext,
    interactionCode: String,
): Request {
    val formBodyBuilder = FormBody.Builder()
        .add("grant_type", "interaction_code")
        .add("client_id", client.configuration.clientId)
        .add("interaction_code", interactionCode)
        .add("code_verifier", flowContext.codeVerifier)

    return Request.Builder()
        .url(client.endpointsOrNull()!!.tokenEndpoint)
        .post(formBodyBuilder.build())
        .build()
}

internal suspend fun introspectRequest(
    client: OAuth2Client,
    flowContext: InteractionCodeFlowContext,
): Request {
    val urlBuilder = client.endpointsOrNull()!!.issuer.newBuilder()
        .encodedPath("/idp/idx/introspect")

    val introspectRequest = IntrospectRequest(flowContext.interactionHandle)
    val jsonBody = client.configuration.json.encodeToString(introspectRequest)

    return Request.Builder()
        .url(urlBuilder.build())
        .post(jsonBody.toRequestBody("application/ion+json; okta-version=1.0.0".toMediaType()))
        .build()
}

internal class InteractContext private constructor(
    val codeVerifier: String,
    val state: String,
    val request: Request,
    val nonce: String,
    val maxAge: Int?
) {
    companion object {
        suspend fun create(
            client: OAuth2Client,
            redirectUrl: String,
            extraParameters: Map<String, String> = emptyMap(),
            codeVerifier: String = PkceGenerator.codeVerifier(),
            state: String = UUID.randomUUID().toString(),
            nonce: String = UUID.randomUUID().toString(),
        ): InteractContext? {
            val codeChallenge = PkceGenerator.codeChallenge(codeVerifier)
            val endpoints = client.endpointsOrNull() ?: return null
            val urlBuilder = endpoints.issuer.newBuilder()
                .addPathSegments("v1/interact")

            val formBody = FormBody.Builder()
                .add("client_id", client.configuration.clientId)
                .add("scope", client.configuration.defaultScope)
                .add("code_challenge", codeChallenge)
                .add("code_challenge_method", PkceGenerator.CODE_CHALLENGE_METHOD)
                .add("redirect_uri", redirectUrl)
                .add("state", state)
                .add("nonce", nonce)

            for (extraParameter in extraParameters) {
                formBody.add(extraParameter.key, extraParameter.value)
            }

            val maxAge = extraParameters["max_age"]?.toIntOrNull()

            val request = Request.Builder()
                .url(urlBuilder.build())
                .post(formBody.build())
                .build()

            return InteractContext(codeVerifier, state, request, nonce, maxAge)
        }
    }
}
