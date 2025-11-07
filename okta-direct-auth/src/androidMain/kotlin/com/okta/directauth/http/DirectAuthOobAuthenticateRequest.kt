package com.okta.directauth.http

import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.OobChannel

internal class DirectAuthOobAuthenticateRequest(internal val context: DirectAuthenticationContext, val loginHint: String, val oobChannel: OobChannel) : DirectAuthRequest {

    private val path = if (context.authorizationServerId.isBlank()) "/oauth2/v1/oob-authenticate" else "/oauth2/${context.authorizationServerId}/v1/oob-authenticate"

    override fun url(): String = context.issuerUrl.trimEnd('/') + path

    override fun headers(): Map<String, List<String>> = mapOf("Accept" to listOf("application/json"))

    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun query(): Map<String, String>? = context.additionalParameters.takeIf { it.isNotEmpty() }

    override fun formParameters(): Map<String, List<String>> = buildMap {
        if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
        put("client_id", listOf(context.clientId))
        put("login_hint", listOf(loginHint))
        put("channel_hint", listOf(oobChannel.value))
    }
}
