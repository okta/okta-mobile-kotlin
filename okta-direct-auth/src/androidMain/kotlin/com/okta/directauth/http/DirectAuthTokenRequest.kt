package com.okta.directauth.http

import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiRequestMethod
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.MfaContext
import com.okta.directauth.model.PrimaryFactor

sealed class DirectAuthTokenRequest(internal val context: DirectAuthenticationContext) : DirectAuthRequest {

    private val path = if (context.authorizationServerId.isBlank()) "/oauth2/v1/token" else "/oauth2/${context.authorizationServerId}/v1/token"

    override fun url(): String = context.issuerUrl.trimEnd('/') + path

    override fun headers(): Map<String, List<String>> = mapOf("Accept" to listOf("application/json"))

    override fun method(): ApiRequestMethod = ApiRequestMethod.POST

    override fun contentType(): String = "application/x-www-form-urlencoded"

    override fun query(): Map<String, String>? = context.additionalParameters.takeIf { it.isNotEmpty() }

    internal fun DirectAuthenticationContext.formParameters(): Map<String, List<String>> = buildMap {
        put("client_id", listOf(clientId))
        put("scope", listOf(scope.joinToString(" ")))
        put("grant_types_supported", listOf(grantTypes.joinToString(" ") { it.value }))
    }

    class Password internal constructor(
        context: DirectAuthenticationContext,
        private val username: String,
        private val password: String,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("grant_type", listOf(GrantType.Password.value))
            put("username", listOf(username))
            put("password", listOf(password))
        }
    }

    class Otp internal constructor(
        context: DirectAuthenticationContext,
        private val loginHint: String,
        private val otp: String,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("grant_type", listOf(GrantType.Otp.value))
            put("login_hint", listOf(loginHint))
            put("otp", listOf(otp))

        }
    }

    class MfaOtp internal constructor(
        context: DirectAuthenticationContext,
        private val otp: String,
        private val mfaContext: MfaContext,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("grant_type", listOf(GrantType.OtpMfa.value))
            put("mfa_token", listOf(mfaContext.mfaToken))
            put("otp", listOf(otp))
        }
    }

    class Oob internal constructor(
        context: DirectAuthenticationContext,
        private val oobCode: String,
        private val bindingCode: String? = null,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("grant_type", listOf(GrantType.Oob.value))
            put("oob_code", listOf(oobCode))
            bindingCode?.takeIf { it.isNotBlank() }?.let { put("binding_code", listOf(it)) }
        }
    }

    class OobMfa internal constructor(
        context: DirectAuthenticationContext,
        private val oobCode: String,
        private val mfaContext: MfaContext,
        private val bindingCode: String? = null,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            if (context.clientSecret.isNotBlank()) put("client_secret", listOf(context.clientSecret))
            put("grant_type", listOf(GrantType.OobMfa.value))
            put("mfa_token", listOf(mfaContext.mfaToken))
            put("oob_code", listOf(oobCode))
            bindingCode?.takeIf { it.isNotBlank() }?.let { put("binding_code", listOf(it)) }
        }
    }

    class WebAuthn internal constructor(
        context: DirectAuthenticationContext,
        private val authenticatorData: String,
        private val clientDataJson: String,
        private val signature: String,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            put("grant_type", listOf(GrantType.WebAuthn.value))
            put("authenticatorData", listOf(authenticatorData))
            put("clientDataJSON", listOf(clientDataJson))
            put("signature", listOf(signature))
        }
    }

    class WebAuthnMfa internal constructor(
        context: DirectAuthenticationContext,
        private val mfaContext: MfaContext,
        private val authenticatorData: String,
        private val clientDataJson: String,
        private val signature: String,
    ) : DirectAuthTokenRequest(context) {
        override fun formParameters(): Map<String, List<String>> = buildMap {
            putAll(context.formParameters())
            put("mfa_token", listOf(mfaContext.mfaToken))
            put("grant_type", listOf(GrantType.WebAuthnMfa.value))
            put("authenticatorData", listOf(authenticatorData))
            put("clientDataJSON", listOf(clientDataJson))
            put("signature", listOf(signature))
        }
    }
}