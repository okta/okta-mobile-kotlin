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
package com.okta.directauth

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.api.http.ApiExecutor
import com.okta.authfoundation.api.http.log.AuthFoundationLogger
import com.okta.authfoundation.client.OidcClock
import com.okta.directauth.DirectAuthenticationFlowBuilder.Companion.create
import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.http.KtorHttpExecutor
import com.okta.directauth.log.AuthFoundationLoggerImpl
import com.okta.directauth.model.DirectAuthenticationContext
import com.okta.directauth.model.DirectAuthenticationIntent
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * A builder used to configure and create an instance of [DirectAuthenticationFlow].
 *
 * This class provides a fluent API for setting the necessary parameters for the
 * Direct Authentication flow, such as the issuer URL, client ID, and scopes.
 *
 * An instance of this builder should be created using the [create] factory method.
 */
class DirectAuthenticationFlowBuilder private constructor() {
    /**
     * The ID of the authorization server to use for the authentication flow.
     *
     * This value identifies which authorization server will issue the tokens. For more information,
     * see the guide on [Okta Authorization Servers](https://developer.okta.com/docs/concepts/auth-servers/).
     *
     * Defaults to an empty string, which specifies the org authorization server. To use a
     * custom authorization server, set this to its ID or use `"default"` for the default
     * custom authorization server.
     */
    var authorizationServerId: String = ""

    /**
     * The client secret of the application.
     *
     * This is an optional parameter and may not be required for all OAuth 2.0 flows,
     * especially for public clients. If your application is registered as a confidential
     * client, you should provide the client secret here.
     *
     * Defaults to an empty string.
     */
    var clientSecret: String = ""

    /**
     * The intended user action for the authentication flow.
     *
     * This parameter indicates what the user is trying to achieve with the authentication request.
     * For example, [DirectAuthenticationIntent.SIGN_IN] or [DirectAuthenticationIntent.RECOVERY].
     *
     * Defaults to [DirectAuthenticationIntent.SIGN_IN].
     */
    var directAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN

    /**
     * The list of grant types the client application supports for the Direct Authentication flow.
     *
     * This list informs the authorization server about the authentication methods the client can handle,
     * such as passwords, OTP, or OOB (out-of-band) factors.
     */
    var supportedGrantType = listOf(GrantType.Password, GrantType.Oob, GrantType.Otp, ChallengeGrantType.OobMfa, ChallengeGrantType.OtpMfa, GrantType.WebAuthn, ChallengeGrantType.WebAuthnMfa)

    /**
     * A list of Authentication Context Class Reference values.
     *
     * This OIDC parameter requests that the user be authenticated with a particular level of assurance.
     * The values are defined by the authorization server's policy.
     */
    var acrValues = listOf<String>()

    /**
     * The HTTP client executor used to make network requests.
     *
     * This allows consumers to provide a custom implementation for making HTTP requests,
     * such as one that integrates with their existing networking stack or adds custom
     * headers for logging or analytics.
     *
     * Defaults to an instance of [KtorHttpExecutor] which uses Ktor's HTTP client.
     */
    var apiExecutor: ApiExecutor = KtorHttpExecutor()

    /**
     * The logger used by the SDK to output diagnostic information.
     *
     * This allows consumers to integrate the SDK's logging with their application's
     * existing logging framework, such as Timber or a custom solution. It can also be
     * replaced with a mock implementation for testing purposes.
     *
     * Defaults to an instance of [AuthFoundationLoggerImpl] which logs to Android's Logcat.
     */
    var logger: AuthFoundationLogger = AuthFoundationLoggerImpl()

    /**
     * The clock used for time-sensitive operations.
     *
     * This defaults to a clock that returns the current time in epoch seconds.
     */
    var clock: OidcClock = OidcClock { System.currentTimeMillis() / 1000 }

    /**
     * Any additional query string parameters you would like to supply to the authorization server for all requests from this flow.
     *
     * Defaults to an empty map.
     */
    var additionalParameter: Map<String, String> = emptyMap()

    companion object {
        /**
         * Creates an instance of [DirectAuthenticationFlow] using the builder pattern.
         *
         * @param issuerUrl The base URL of the Authorization Server. This is the issuer URI for the authorization server that will be used for the flow. For example: `https://dev-123456.okta.com`.
         * @param clientId The client ID of the application. This ID is obtained from the Okta developer console when you register your application.
         * @param scope The OAuth 2.0 scopes the application is requesting. Scopes are used to specify what access privileges are being requested for access tokens. For example: `openid`, `profile`, `email`, and `offline_access`.
         * @param buildAction A lambda with a [DirectAuthenticationFlowBuilder] receiver to configure
         * the flow's parameters.
         * @return A [Result] containing the configured [DirectAuthenticationFlow] on success,
         * or an exception on failure.
         */
        fun create(
            issuerUrl: String,
            clientId: String,
            scope: List<String>,
            buildAction: (DirectAuthenticationFlowBuilder.() -> Unit)? = null,
        ): Result<DirectAuthenticationFlow> =
            runCatching {
                val builder = DirectAuthenticationFlowBuilder()
                buildAction?.invoke(builder)

                require(
                    runCatching {
                        val url = Url(issuerUrl)
                        url.protocol == URLProtocol.HTTPS && url.host.isNotBlank()
                    }.getOrDefault(false)
                ) { "issuerUrl must be a valid https URL." }

                require(clientId.isNotBlank()) { "clientId must be set and not empty." }
                require(scope.isNotEmpty()) { "scope must be set and not empty." }

                Result.success(
                    DirectAuthenticationFlowImpl(
                        DirectAuthenticationContext(
                            issuerUrl,
                            clientId,
                            scope,
                            builder.authorizationServerId,
                            builder.clientSecret,
                            builder.supportedGrantType,
                            builder.acrValues,
                            builder.directAuthenticationIntent,
                            builder.apiExecutor,
                            builder.logger,
                            builder.clock,
                            builder.additionalParameter
                        )
                    )
                )
            }.getOrElse { Result.failure(it) }
    }
}
