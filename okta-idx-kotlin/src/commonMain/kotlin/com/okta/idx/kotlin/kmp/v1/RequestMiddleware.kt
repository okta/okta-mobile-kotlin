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
package com.okta.idx.kotlin.kmp.v1

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.api.http.ApiRequestBody
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.idx.kotlin.kmp.IdxRemediation
import com.okta.idx.kotlin.kmp.InteractionCodeFlowContext
import com.okta.idx.kotlin.kmp.PkceGenerator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A simple [ApiRequestBody] holding a pre-encoded JSON string. */
internal class JsonBodyRequest(
    private val requestUrl: String,
    private val jsonBody: String,
    private val extraHeaders: Map<String, List<String>> = emptyMap(),
) : ApiRequestBody {
    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun headers(): Map<String, List<String>> = extraHeaders

    override fun url(): String = requestUrl

    override fun contentType(): String = "application/ion+json; okta-version=1.0.0"

    override fun body(): ByteArray = jsonBody.encodeToByteArray()
}

/** A simple [ApiFormRequest] built from an ordered list of form parameters (repeated keys allowed). */
internal class FormBodyRequest(
    private val requestUrl: String,
    formParameters: List<Pair<String, String>>,
    private val extraHeaders: Map<String, List<String>> = emptyMap(),
) : ApiFormRequest {
    private val grouped: Map<String, List<String>> =
        buildMap<String, MutableList<String>> {
            for ((key, value) in formParameters) {
                getOrPut(key) { mutableListOf() }.add(value)
            }
        }

    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun headers(): Map<String, List<String>> = extraHeaders

    override fun url(): String = requestUrl

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun formParameters(): Map<String, List<String>> = grouped
}

@OptIn(InternalAuthFoundationApi::class)
internal fun IdxRemediation.asJsonRequest(
    client: OAuth2Client,
    extraHeaders: Map<String, List<String>> = emptyMap(),
): JsonBodyRequest {
    val headers = (accepts?.let { mapOf("accept" to listOf(it)) } ?: emptyMap()) + extraHeaders
    val jsonBody =
        if (method == "POST") {
            client.configuration.json.encodeToString(toJsonContent())
        } else {
            ""
        }
    return JsonBodyRequest(href, jsonBody, headers)
}

internal fun IdxRemediation.toJsonContent(): JsonElement = form.toJsonContent()

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

private fun Any?.asJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        else -> throw IllegalStateException("Unknown type")
    }

internal fun IdxRemediation.asFormRequest(extraHeaders: Map<String, List<String>> = emptyMap()): FormBodyRequest {
    val formParameters = mutableListOf<Pair<String, String>>()
    form.allFields.forEach { field ->
        val value =
            when (val fieldValue = field.value) {
                is JsonPrimitive -> fieldValue.content
                null -> ""
                else -> fieldValue.toString()
            }
        if (field.name != null) {
            formParameters += field.name to value
        }
    }

    return FormBodyRequest(href, formParameters, extraHeaders)
}

@OptIn(InternalAuthFoundationApi::class)
internal fun tokenRequestFormParamsFromInteractionCode(
    client: OAuth2Client,
    flowContext: InteractionCodeFlowContext,
    interactionCode: String,
): Map<String, String> =
    mapOf(
        "grant_type" to "interaction_code",
        "client_id" to client.configuration.clientId,
        "interaction_code" to interactionCode,
        "code_verifier" to flowContext.codeVerifier
    )

@OptIn(InternalAuthFoundationApi::class)
internal suspend fun introspectRequest(
    client: OAuth2Client,
    flowContext: InteractionCodeFlowContext,
    extraHeaders: Map<String, List<String>> = emptyMap(),
): JsonBodyRequest? {
    val issuer = client.endpointsOrNull()?.issuer ?: return null
    val introspectUrl = issuer.trimEnd('/') + "/idp/idx/introspect"
    val introspectRequest = IntrospectRequest(flowContext.interactionHandle)
    val jsonBody = client.configuration.json.encodeToString(introspectRequest)
    return JsonBodyRequest(introspectUrl, jsonBody, extraHeaders)
}

@OptIn(InternalAuthFoundationApi::class, ExperimentalUuidApi::class)
internal class InteractContext private constructor(
    val codeVerifier: String,
    val state: String,
    private val formParameters: List<Pair<String, String>>,
    private val interactUrl: String,
    val nonce: String,
    val maxAge: Int?,
) {
    fun toRequest(extraHeaders: Map<String, List<String>> = emptyMap()): FormBodyRequest = FormBodyRequest(interactUrl, formParameters, extraHeaders)

    companion object {
        suspend fun create(
            client: OAuth2Client,
            redirectUrl: String,
            extraParameters: Map<String, String> = emptyMap(),
            codeVerifier: String = PkceGenerator.codeVerifier(),
            state: String = Uuid.random().toString(),
            nonce: String = Uuid.random().toString(),
        ): InteractContext? {
            val codeChallenge = PkceGenerator.codeChallenge(codeVerifier)
            val issuer = client.endpointsOrNull()?.issuer ?: return null
            val interactUrl =
                issuer.trimEnd('/') +
                    if (issuer.contains("/oauth2")) "/v1/interact" else "/oauth2/v1/interact"

            val formParameters =
                mutableListOf(
                    "client_id" to client.configuration.clientId,
                    "scope" to client.configuration.defaultScope.joinToString(" "),
                    "code_challenge" to codeChallenge,
                    "code_challenge_method" to PkceGenerator.CODE_CHALLENGE_METHOD,
                    "redirect_uri" to redirectUrl,
                    "state" to state,
                    "nonce" to nonce
                )

            for (extraParameter in extraParameters) {
                formParameters += extraParameter.key to extraParameter.value
            }

            val maxAge = extraParameters["max_age"]?.toIntOrNull()

            return InteractContext(codeVerifier, state, formParameters, interactUrl, nonce, maxAge)
        }
    }
}
