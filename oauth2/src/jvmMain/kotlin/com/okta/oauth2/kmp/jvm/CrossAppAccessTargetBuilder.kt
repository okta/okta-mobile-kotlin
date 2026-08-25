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
package com.okta.oauth2.kmp.jvm

import com.okta.authfoundation.client.ClientAssertionProvider
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2EndpointOverrides
import java.util.function.Consumer
import com.okta.oauth2.kmp.CrossAppAccessTarget as KotlinCrossAppAccessTarget
import com.okta.oauth2.kmp.CrossAppAccessTargetBuilder as KotlinCrossAppAccessTargetBuilder

/**
 * A Java-idiomatic builder for creating instances of [KotlinCrossAppAccessTarget].
 *
 * This builder provides method-chaining setters and delegates to the Kotlin
 * `CrossAppAccessTarget` factories for the actual construction. Reach it through [forIssuer] or
 * [forAuthorizationServerId] — exactly one of the two, since a target is named by exactly one
 * identifier form.
 *
 * ```java
 * CrossAppAccessTarget target = CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
 *     .setClientSecret("target-secret")
 *     .setScope(List.of("chat.read"))
 *     .build();
 * ```
 */
class CrossAppAccessTargetBuilder private constructor(
    private val issuer: String?,
    private val authorizationServerId: String?,
) {
    private var clientId: String? = null
    private var scope: List<String>? = null
    private var clientSecret: String? = null
    private var clientAssertionProvider: ClientAssertionProvider? = null
    private var endpointOverrides: OAuth2EndpointOverrides? = null
    private var resource: String? = null
    private var clientBuildAction: Consumer<OAuth2ClientBuilder>? = null

    /**
     * Sets the client identifier to authenticate with at the target only.
     *
     * @param clientId the target-side client identifier.
     * @return this builder, for chaining.
     */
    fun setClientId(clientId: String): CrossAppAccessTargetBuilder = apply { this.clientId = clientId }

    /**
     * Sets the scopes requested at the target.
     *
     * @param scope the requested scopes.
     * @return this builder, for chaining.
     */
    fun setScope(scope: List<String>): CrossAppAccessTargetBuilder = apply { this.scope = scope }

    /**
     * Sets the client secret to authenticate with at the target. Mutually exclusive with
     * [setClientAssertionProvider].
     *
     * @param clientSecret the target-side client secret.
     * @return this builder, for chaining.
     */
    fun setClientSecret(clientSecret: String): CrossAppAccessTargetBuilder = apply { this.clientSecret = clientSecret }

    /**
     * Sets the client-assertion provider to authenticate with at the target. Mutually exclusive
     * with [setClientSecret].
     *
     * @param clientAssertionProvider the target-side client-assertion provider.
     * @return this builder, for chaining.
     */
    fun setClientAssertionProvider(clientAssertionProvider: ClientAssertionProvider): CrossAppAccessTargetBuilder = apply { this.clientAssertionProvider = clientAssertionProvider }

    /**
     * Sets explicit endpoint overrides for the target, taking precedence over discovered values.
     *
     * @param endpointOverrides the endpoint overrides.
     * @return this builder, for chaining.
     */
    fun setEndpointOverrides(endpointOverrides: OAuth2EndpointOverrides): CrossAppAccessTargetBuilder = apply { this.endpointOverrides = endpointOverrides }

    /**
     * Sets the RFC 8707 resource indicator to send with the first step.
     *
     * @param resource the resource indicator.
     * @return this builder, for chaining.
     */
    fun setResource(resource: String): CrossAppAccessTargetBuilder = apply { this.resource = resource }

    /**
     * Applied to the target client's [OAuth2ClientBuilder] last, after every setting above, so it
     * wins on conflict. Use this for target-client settings this builder does not name directly.
     *
     * Because it is applied last, it can overwrite the credential and identity settings above —
     * including with the primary client's own credential. Configure the target's credential
     * through [setClientSecret] or [setClientAssertionProvider]; reserve this for settings those
     * do not cover.
     *
     * @param action a consumer that configures the target client's builder.
     * @return this builder, for chaining.
     */
    fun setClientBuildAction(action: Consumer<OAuth2ClientBuilder>): CrossAppAccessTargetBuilder = apply { this.clientBuildAction = action }

    /**
     * Builds the [KotlinCrossAppAccessTarget]. Naming a target has no failure mode, so this
     * returns the target directly rather than an `AuthFoundationResult`.
     *
     * @return the built target.
     */
    fun build(): KotlinCrossAppAccessTarget {
        val capturedClientId = clientId
        val capturedScope = scope
        val capturedClientSecret = clientSecret
        val capturedClientAssertionProvider = clientAssertionProvider
        val capturedEndpointOverrides = endpointOverrides
        val capturedResource = resource
        val capturedClientBuildAction: (OAuth2ClientBuilder.() -> Unit)? = clientBuildAction?.let { consumer -> { consumer.accept(this) } }

        val configure: KotlinCrossAppAccessTargetBuilder.() -> Unit = {
            clientId = capturedClientId
            scope = capturedScope
            clientSecret = capturedClientSecret
            clientAssertionProvider = capturedClientAssertionProvider
            endpointOverrides = capturedEndpointOverrides
            resource = capturedResource
            clientBuildAction = capturedClientBuildAction
        }

        return if (issuer != null) {
            KotlinCrossAppAccessTarget.forIssuer(issuer, configure)
        } else {
            KotlinCrossAppAccessTarget.forAuthorizationServerId(authorizationServerId!!, configure)
        }
    }

    companion object {
        /**
         * Starts a builder for a target named by its issuer origin.
         *
         * @param issuer the target's issuer origin.
         */
        @JvmStatic
        fun forIssuer(issuer: String): CrossAppAccessTargetBuilder = CrossAppAccessTargetBuilder(issuer = issuer, authorizationServerId = null)

        /**
         * Starts a builder for a target named by an Okta custom authorization server identifier,
         * resolved against the primary client's own org.
         *
         * @param authorizationServerId the custom authorization server identifier.
         */
        @JvmStatic
        fun forAuthorizationServerId(authorizationServerId: String): CrossAppAccessTargetBuilder = CrossAppAccessTargetBuilder(issuer = null, authorizationServerId = authorizationServerId)
    }
}
