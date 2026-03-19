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
package com.okta.directauth.jvm

import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.authfoundation.client.OidcClock
import com.okta.directauth.model.DirectAuthenticationIntent
import com.okta.directauth.DirectAuthenticationFlowBuilder as KotlinBuilder

/**
 * A Java-idiomatic builder for creating instances of [DirectAuthenticationFlow].
 *
 * This builder provides method-chaining setters for optional parameters and delegates
 * to the Kotlin [KotlinBuilder] for the actual construction.
 *
 * @param issuerUrl The base URL of the Authorization Server.
 * @param clientId The client ID of the application.
 * @param scope The OAuth 2.0 scopes the application is requesting.
 */
class DirectAuthenticationFlowBuilder(
    private val issuerUrl: String,
    private val clientId: String,
    private val scope: List<String>,
) {
    private var authorizationServerId: String = ""
    private var clientSecret: String = ""
    private var intent: DirectAuthenticationIntent = DirectAuthenticationIntent.SIGN_IN
    private var supportedGrantTypes: List<GrantType> =
        listOf(
            GrantType.Password,
            GrantType.Oob,
            GrantType.Otp,
            ChallengeGrantType.OobMfa,
            ChallengeGrantType.OtpMfa,
            GrantType.WebAuthn,
            ChallengeGrantType.WebAuthnMfa
        )
    private var acrValues: List<String> = emptyList()
    private var clock: OidcClock? = null
    private var additionalParameters: Map<String, String> = emptyMap()

    /**
     * Sets the authorization server ID.
     *
     * @param authorizationServerId The ID of the authorization server.
     * @return This builder for chaining.
     */
    fun setAuthorizationServerId(authorizationServerId: String): DirectAuthenticationFlowBuilder =
        apply {
            this.authorizationServerId = authorizationServerId
        }

    /**
     * Sets the client secret.
     *
     * @param clientSecret The client secret of the application.
     * @return This builder for chaining.
     */
    fun setClientSecret(clientSecret: String): DirectAuthenticationFlowBuilder =
        apply {
            this.clientSecret = clientSecret
        }

    /**
     * Sets the authentication intent.
     *
     * @param intent The intended user action for the flow.
     * @return This builder for chaining.
     */
    fun setIntent(intent: DirectAuthenticationIntent): DirectAuthenticationFlowBuilder =
        apply {
            this.intent = intent
        }

    /**
     * Sets the supported grant types.
     *
     * @param supportedGrantTypes The list of grant types the client supports.
     * @return This builder for chaining.
     */
    fun setSupportedGrantTypes(supportedGrantTypes: List<GrantType>): DirectAuthenticationFlowBuilder =
        apply {
            this.supportedGrantTypes = supportedGrantTypes
        }

    /**
     * Sets the ACR values.
     *
     * @param acrValues A list of Authentication Context Class Reference values.
     * @return This builder for chaining.
     */
    fun setAcrValues(acrValues: List<String>): DirectAuthenticationFlowBuilder =
        apply {
            this.acrValues = acrValues
        }

    /**
     * Sets the clock used for time-sensitive operations.
     *
     * @param clock The [OidcClock] to use.
     * @return This builder for chaining.
     */
    fun setClock(clock: OidcClock): DirectAuthenticationFlowBuilder =
        apply {
            this.clock = clock
        }

    /**
     * Sets additional query string parameters for all requests.
     *
     * @param additionalParameters A map of additional parameters.
     * @return This builder for chaining.
     */
    fun setAdditionalParameters(additionalParameters: Map<String, String>): DirectAuthenticationFlowBuilder =
        apply {
            this.additionalParameters = additionalParameters
        }

    /**
     * Creates a [DirectAuthenticationFlow] instance with the configured parameters.
     *
     * @return A [DirectAuthResult] containing the [DirectAuthenticationFlow] on success, or an exception on failure.
     */
    fun build(): DirectAuthResult<DirectAuthenticationFlow> {
        val kotlinResult =
            KotlinBuilder.create(issuerUrl, clientId, scope) {
                authorizationServerId = this@DirectAuthenticationFlowBuilder.authorizationServerId
                clientSecret = this@DirectAuthenticationFlowBuilder.clientSecret
                directAuthenticationIntent = this@DirectAuthenticationFlowBuilder.intent
                supportedGrantType = this@DirectAuthenticationFlowBuilder.supportedGrantTypes
                acrValues = this@DirectAuthenticationFlowBuilder.acrValues
                this@DirectAuthenticationFlowBuilder.clock?.let { clock = it }
                additionalParameter = this@DirectAuthenticationFlowBuilder.additionalParameters
            }
        return DirectAuthResult.fromKotlinResult(kotlinResult.map { DirectAuthenticationFlow(it) })
    }
}
