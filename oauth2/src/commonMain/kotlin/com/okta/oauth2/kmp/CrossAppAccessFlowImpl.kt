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
package com.okta.oauth2.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * Default implementation of [CrossAppAccessFlow].
 *
 * @param idpClient the primary client, used for [start].
 * @param targetClient the resource authorization server client, used for [redeem].
 * @param defaultScope the target's configured default scopes, used when [start] receives none.
 * @param resource the target's configured RFC 8707 resource indicator, sent with every [start] call.
 */
internal class CrossAppAccessFlowImpl(
    override val idpClient: OAuth2Client,
    override val targetClient: OAuth2Client,
    private val defaultScope: List<String>?,
    private val resource: String?,
) : CrossAppAccessFlow {
    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun start(
        subjectAssertion: SubjectAssertion,
        scope: List<String>?,
    ): Result<IdJagAssertion> =
        runCatching {
            // Client-side narrowing, not a spec requirement: draft §4.3 marks `scope` OPTIONAL,
            // but Okta's org authorization server returns invalid_scope for a scope-less
            // exchange.
            val resolvedScope = scope ?: defaultScope
            require(!resolvedScope.isNullOrEmpty()) {
                "No scope was requested for this Cross App Access exchange. Supply scope on " +
                    "CrossAppAccessTarget or on start() — the IdP authorization server rejects a " +
                    "scope-less request for this grant."
            }
            val formParams =
                buildMap {
                    put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                    put("requested_token_type", "urn:ietf:params:oauth:token-type:id-jag")
                    put("audience", targetClient.configuration.issuerUrl)
                    put("subject_token", subjectAssertion.value)
                    put("subject_token_type", subjectAssertion.type.subjectTokenTypeUrn())
                    put("client_id", idpClient.configuration.clientId)
                    put("scope", resolvedScope.joinToString(" "))
                    resource?.let { put("resource", it) }
                }
            val tokenInfo =
                idpClient.tokenRequest(formParams).getOrElse {
                    throw CrossAppAccessException.IdpExchangeFailed(
                        "Cross App Access failed at the IdP authorization server (${idpClient.configuration.issuerUrl}): ${it.message}",
                        it
                    )
                }
            IdJagAssertion(
                value = tokenInfo.accessToken,
                audience = targetClient.configuration.issuerUrl,
                expiresIn = tokenInfo.expiresIn,
                issuedAt = idpClient.configuration.clock.currentTimeEpochSecond(),
                scope = tokenInfo.scope,
                issuedTokenType = tokenInfo.issuedTokenType
            )
        }

    @OptIn(InternalAuthFoundationApi::class)
    override suspend fun redeem(idJag: IdJagAssertion): Result<TokenInfo> =
        runCatching {
            require(idJag.value.isNotBlank()) { "The ID-JAG assertion value is blank; it cannot be redeemed." }
            val formParams =
                buildMap {
                    put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    put("assertion", idJag.value)
                    put("client_id", targetClient.configuration.clientId)
                }
            targetClient.tokenRequest(formParams).getOrElse {
                throw CrossAppAccessException.TargetRedemptionFailed(
                    "Cross App Access failed at the resource authorization server (${targetClient.configuration.issuerUrl}): ${it.message}",
                    it
                )
            }
        }

    override suspend fun exchange(
        subjectAssertion: SubjectAssertion,
        scope: List<String>?,
    ): Result<TokenInfo> =
        start(subjectAssertion, scope).fold(
            onSuccess = { redeem(it) },
            onFailure = { Result.failure(it) }
        )

    override fun toString(): String = "CrossAppAccessFlow(idp=${idpClient.configuration.issuerUrl}, target=${targetClient.configuration.issuerUrl})"

    companion object {
        /**
         * A single, never-transmitted scope value used only to satisfy [OAuth2ClientBuilder]'s
         * non-empty-scope requirement when a target names no scopes of its own. [start] resolves
         * its outgoing `scope` from the [CrossAppAccessTarget] descriptor directly and never reads
         * the built target client's configured default scope, so this value never reaches the wire.
         */
        private const val INERT_PLACEHOLDER_SCOPE = "com.okta.oauth2.cross-app-access.inert-scope"

        fun create(
            idpClient: OAuth2Client,
            target: CrossAppAccessTarget,
        ): Result<CrossAppAccessFlow> =
            runCatching {
                val targetClient = buildTargetClient(idpClient, target)
                val configuration = targetClient.configuration
                require(configuration.clientSecret.isNotBlank() || configuration.clientAssertionProvider != null) {
                    "The target resource authorization server has no client secret or client assertion " +
                        "provider configured. Cross App Access requires a confidential client at both steps."
                }
                CrossAppAccessFlowImpl(idpClient, targetClient, target.scope, target.resource)
            }

        private fun buildTargetClient(
            idpClient: OAuth2Client,
            target: CrossAppAccessTarget,
        ): OAuth2Client =
            when (target) {
                is CrossAppAccessTarget.Prebuilt -> {
                    target.client
                }

                is CrossAppAccessTarget.Issuer -> {
                    require(target.issuer.isNotBlank()) { "CrossAppAccessTarget.Issuer.issuer must not be blank." }
                    OAuth2ClientBuilder
                        .create(
                            issuerUrl = target.issuer,
                            clientId = target.clientId ?: idpClient.configuration.clientId,
                            scope = target.scope ?: listOf(INERT_PLACEHOLDER_SCOPE)
                        ) {
                            clientSecret = target.clientSecret ?: ""
                            clientAssertionProvider = target.clientAssertionProvider
                            endpointOverrides = target.endpointOverrides
                            target.clientBuildAction?.invoke(this)
                        }.getOrThrow()
                }

                is CrossAppAccessTarget.AuthorizationServerId -> {
                    require(target.authorizationServerId.isNotBlank()) {
                        "CrossAppAccessTarget.AuthorizationServerId.authorizationServerId must not be blank."
                    }
                    OAuth2ClientBuilder
                        .create(
                            issuerUrl = idpClient.configuration.issuerUrl,
                            clientId = target.clientId ?: idpClient.configuration.clientId,
                            scope = target.scope ?: listOf(INERT_PLACEHOLDER_SCOPE)
                        ) {
                            authorizationServerId = target.authorizationServerId
                            clientSecret = target.clientSecret ?: ""
                            clientAssertionProvider = target.clientAssertionProvider
                            endpointOverrides = target.endpointOverrides
                            target.clientBuildAction?.invoke(this)
                        }.getOrThrow()
                }
            }

        private fun SubjectAssertion.Type.subjectTokenTypeUrn(): String =
            when (this) {
                SubjectAssertion.Type.ID_TOKEN -> "urn:ietf:params:oauth:token-type:id_token"
                SubjectAssertion.Type.ACCESS_TOKEN -> "urn:ietf:params:oauth:token-type:access_token"
                SubjectAssertion.Type.REFRESH_TOKEN -> "urn:ietf:params:oauth:token-type:refresh_token"
            }
    }
}
