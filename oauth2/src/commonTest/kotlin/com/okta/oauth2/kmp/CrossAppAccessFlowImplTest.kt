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

import com.okta.authfoundation.api.http.ApiFormRequest
import com.okta.authfoundation.client.ClientAssertion
import com.okta.authfoundation.client.ClientAssertionProvider
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2EndpointOverrides
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.client.kmp.events.TokenCreatedEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class CrossAppAccessFlowImplTest {
    private val idpIssuer = "https://example.okta.com"
    private val targetIssuer = "https://resource.example.com"

    private fun buildIdpClient(
        executor: RoutingApiExecutor,
        clientId: String = "idp-client-id",
        issuer: String = idpIssuer,
    ): OAuth2Client =
        OAuth2ClientBuilder
            .create(issuer, clientId, listOf("openid")) {
                apiExecutor = executor
            }.getOrThrow()

    private fun buildTarget(
        executor: RoutingApiExecutor,
        issuer: String = targetIssuer,
        clientSecret: String? = "target-secret",
        clientAssertionProvider: ClientAssertionProvider? = null,
        scope: List<String>? = listOf("chat.read"),
        resource: String? = null,
        clientId: String? = null,
        endpointOverrides: OAuth2EndpointOverrides? = null,
    ): CrossAppAccessTarget.Issuer =
        CrossAppAccessTarget.forIssuer(issuer) {
            this.scope = scope
            this.resource = resource
            this.clientId = clientId
            this.clientSecret = clientSecret
            this.clientAssertionProvider = clientAssertionProvider
            this.endpointOverrides = endpointOverrides
            clientBuildAction = { apiExecutor = executor }
        }

    private fun stubIdpDiscovery(executor: RoutingApiExecutor) {
        executor.stub("$idpIssuer/.well-known/openid-configuration", 200, discoveryDocument(idpIssuer))
    }

    private fun stubTargetDiscovery(
        executor: RoutingApiExecutor,
        issuer: String = targetIssuer,
        grantTypesSupported: List<String>? = null,
    ) {
        executor.stub(
            "$issuer/.well-known/openid-configuration",
            200,
            discoveryDocument(issuer, grantTypesSupported = grantTypesSupported)
        )
    }

    private fun formParams(request: com.okta.authfoundation.api.http.ApiRequest): Map<String, String> = assertIs<ApiFormRequest>(request).formParameters().mapValues { (_, v) -> v.first() }

    @Test
    fun start_WithIdTokenSubject_SendsCorrectFormParams() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val idpClient = buildIdpClient(executor)
            val target = buildTarget(executor)
            val flow = CrossAppAccessFlow.create(idpClient, target).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("the-id-token"))

            assertTrue(result.isSuccess)
            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", params["grant_type"])
            assertEquals("urn:ietf:params:oauth:token-type:id-jag", params["requested_token_type"])
            assertEquals(targetIssuer, params["audience"])
            assertEquals("the-id-token", params["subject_token"])
            assertEquals("urn:ietf:params:oauth:token-type:id_token", params["subject_token_type"])
            assertEquals("idp-client-id", params["client_id"])
            assertEquals("chat.read", params["scope"])
        }

    @Test
    fun start_WithAccessTokenSubject_SendsMatchingSubjectTokenType() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()

            flow.start(SubjectAssertion.accessToken("the-access-token"))

            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("urn:ietf:params:oauth:token-type:access_token", params["subject_token_type"])
            assertEquals("the-access-token", params["subject_token"])
        }

    @Test
    fun start_WithRefreshTokenSubject_SendsMatchingSubjectTokenType() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()

            flow.start(SubjectAssertion.refreshToken("the-refresh-token"))

            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("urn:ietf:params:oauth:token-type:refresh_token", params["subject_token_type"])
            assertEquals("the-refresh-token", params["subject_token"])
        }

    @Test
    fun start_WithNoScopeInResponse_Succeeds() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse(scope = null))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("id-token"))

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow().scope)
        }

    @Test
    fun redeem_SendsOnlyGrantTypeAssertionAndClientId() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val result = flow.redeem(idJag)

            assertTrue(result.isSuccess)
            val tokenInfo = result.getOrThrow()
            assertEquals("resource-access-token", tokenInfo.accessToken)
            assertEquals("Bearer", tokenInfo.tokenType)
            assertEquals(86400, tokenInfo.expiresIn)
            assertEquals("chat.read chat.history", tokenInfo.scope)

            val request = executor.requestsTo("$targetIssuer/v1/token").single()
            val params = formParams(request)
            assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", params["grant_type"])
            assertEquals("id-jag-assertion-value", params["assertion"])
            assertEquals(setOf("grant_type", "assertion", "client_id", "client_secret"), params.keys)
        }

    @Test
    fun redeem_NeverSendsScope() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            flow.redeem(idJag)

            val params = formParams(executor.requestsTo("$targetIssuer/v1/token").single())
            assertNull(params["scope"])
        }

    @Test
    fun redeem_CalledTwice_MakesTwoTargetRequestsAndNoAdditionalIdpRequests() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse(accessToken = "resource-token-1"))
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse(accessToken = "resource-token-2"))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val first = flow.redeem(idJag).getOrThrow()
            val second = flow.redeem(idJag).getOrThrow()

            assertEquals(2, executor.countTo("$targetIssuer/v1/token"))
            assertEquals(1, executor.countTo("$idpIssuer/v1/token"))
            assertNotEquals(first.accessToken, second.accessToken)
        }

    @Test
    fun redeem_WithNoRefreshTokenInResponse_Succeeds() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val result = flow.redeem(idJag)

            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow().refreshToken)
        }

    @Test
    fun create_WithAllEightEndpointOverrides_SkipsTargetDiscovery() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("https://override.example.com/token", 200, resourceTokenResponse())
            val overrides =
                OAuth2EndpointOverrides(
                    authorizationEndpoint = "https://override.example.com/authorize",
                    tokenEndpoint = "https://override.example.com/token",
                    userInfoEndpoint = "https://override.example.com/userinfo",
                    jwksUri = "https://override.example.com/keys",
                    introspectionEndpoint = "https://override.example.com/introspect",
                    revocationEndpoint = "https://override.example.com/revoke",
                    endSessionEndpoint = "https://override.example.com/logout",
                    deviceAuthorizationEndpoint = "https://override.example.com/device"
                )
            val target = buildTarget(executor, endpointOverrides = overrides)
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()
            val result = flow.redeem(idJag)

            assertTrue(result.isSuccess)
            assertEquals(0, executor.countTo("$targetIssuer/.well-known/openid-configuration"))
        }

    @Test
    fun create_WithPartialEndpointOverride_StillDiscoversButOverrideGoverns() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("https://override.example.com/token", 200, resourceTokenResponse())
            val target = buildTarget(executor, endpointOverrides = OAuth2EndpointOverrides(tokenEndpoint = "https://override.example.com/token"))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()
            val result = flow.redeem(idJag)

            assertTrue(result.isSuccess)
            assertEquals(1, executor.countTo("$targetIssuer/.well-known/openid-configuration"))
            assertEquals(1, executor.countTo("https://override.example.com/token"))
            assertEquals(0, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun redeem_WhenTargetDoesNotAdvertiseJwtBearerGrant_StillSucceeds() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor, grantTypesSupported = listOf("authorization_code"))
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val result = flow.redeem(idJag)

            assertTrue(result.isSuccess)
        }

    @Test
    fun exchange_EmitsDiscriminableEventsOnEachClientsStream() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val idpClient = buildIdpClient(executor)
            val target = buildTarget(executor)
            val flow = CrossAppAccessFlow.create(idpClient, target).getOrThrow()

            val idpEventDeferred = async(start = CoroutineStart.UNDISPATCHED) { idpClient.events.first { it is TokenCreatedEvent } as TokenCreatedEvent }
            val targetEventDeferred =
                async(start = CoroutineStart.UNDISPATCHED) { flow.targetClient.events.first { it is TokenCreatedEvent } as TokenCreatedEvent }

            flow.exchange(SubjectAssertion.idToken("id-token")).getOrThrow()

            val idpEvent = idpEventDeferred.await()
            val targetEvent = targetEventDeferred.await()
            assertEquals("urn:ietf:params:oauth:token-type:id-jag", idpEvent.tokenInfo.issuedTokenType)
            assertNotEquals("urn:ietf:params:oauth:token-type:id-jag", targetEvent.tokenInfo.issuedTokenType)
        }

    @Test
    fun exchange_WithDistinctTargetClientId_SendsPrimaryIdAtIdpAndTargetIdAtTarget() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val idpClient = buildIdpClient(executor, clientId = "idp-client-id")
            val target = buildTarget(executor, clientId = "distinct-target-client-id")
            val flow = CrossAppAccessFlow.create(idpClient, target).getOrThrow()

            flow.exchange(SubjectAssertion.idToken("id-token")).getOrThrow()

            val idpParams = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("idp-client-id", idpParams["client_id"])
            val targetParams = formParams(executor.requestsTo("$targetIssuer/v1/token").single())
            assertEquals("distinct-target-client-id", targetParams["client_id"])
        }

    @Test
    fun start_WhenIdpDeniesPolicy_FailsWithIdpExchangeFailedCarryingCause() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 400, errorResponse("access_denied", "No trusted connection configured."))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("id-token"))

            assertTrue(result.isFailure)
            val exception = assertIs<CrossAppAccessException.IdpExchangeFailed>(result.exceptionOrNull())
            val cause = assertIs<com.okta.authfoundation.client.OAuth2ClientResult.Error.HttpResponseException>(exception.cause)
            assertEquals("access_denied", cause.error)
            assertEquals("No trusted connection configured.", cause.errorDescription)
        }

    @Test
    fun redeem_WhenTargetRejectsAssertion_FailsWithTargetRedemptionFailedCarryingCause() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 400, errorResponse("invalid_grant", "The ID-JAG has expired."))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val result = flow.redeem(idJag)

            assertTrue(result.isFailure)
            val exception = assertIs<CrossAppAccessException.TargetRedemptionFailed>(result.exceptionOrNull())
            val cause = assertIs<com.okta.authfoundation.client.OAuth2ClientResult.Error.HttpResponseException>(exception.cause)
            assertEquals("invalid_grant", cause.error)
        }

    @Test
    fun redeem_WhenTargetDiscoveryUnreachable_FailsWithTargetRedemptionFailed() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            // No stub for the target's discovery document — the executor returns a failure.
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val result = flow.redeem(idJag)

            assertTrue(result.isFailure)
            assertIs<CrossAppAccessException.TargetRedemptionFailed>(result.exceptionOrNull())
        }

    @Test
    fun redeem_InvokesClientAssertionProviderWithTargetTokenEndpointAsAudience() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val audiences = mutableListOf<String>()
            val provider =
                ClientAssertionProvider { audience ->
                    audiences.add(audience)
                    ClientAssertion(type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer", assertion = "signed-jwt")
                }
            val target = buildTarget(executor, clientSecret = null, clientAssertionProvider = provider)
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            flow.redeem(idJag)

            assertEquals(listOf("$targetIssuer/v1/token"), audiences)
        }

    @Test
    fun redeem_WhenClientAssertionProviderFails_SurfacesTargetAttributedFailure() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val failure = IllegalStateException("signing key unavailable")
            val provider = ClientAssertionProvider { throw failure }
            val target = buildTarget(executor, clientSecret = null, clientAssertionProvider = provider)
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()
            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            val result = flow.redeem(idJag)

            assertTrue(result.isFailure)
            val exception = assertIs<CrossAppAccessException.TargetRedemptionFailed>(result.exceptionOrNull())
            // Coroutines may clone the exception for stack trace recovery across a suspension
            // boundary, so compare type and message rather than reference identity.
            val cause = assertIs<IllegalStateException>(exception.cause)
            assertEquals(failure.message, cause.message)
        }

    @Test
    fun exchange_TwoFlowsAgainstDifferentTargetsConcurrently_DoNotInterfere() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            val secondTargetIssuer = "https://other-resource.example.com"
            stubTargetDiscovery(executor, issuer = targetIssuer)
            stubTargetDiscovery(executor, issuer = secondTargetIssuer)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse(accessToken = "token-for-first"))
            executor.enqueue("$secondTargetIssuer/v1/token", 200, resourceTokenResponse(accessToken = "token-for-second"))
            val idpClient = buildIdpClient(executor)
            val firstFlow = CrossAppAccessFlow.create(idpClient, buildTarget(executor, issuer = targetIssuer)).getOrThrow()
            val secondFlow = CrossAppAccessFlow.create(idpClient, buildTarget(executor, issuer = secondTargetIssuer)).getOrThrow()

            val results =
                listOf(
                    async { firstFlow.exchange(SubjectAssertion.idToken("id-token")) },
                    async { secondFlow.exchange(SubjectAssertion.idToken("id-token")) }
                ).awaitAll()

            val accessTokens = results.map { it.getOrThrow().accessToken }.toSet()
            assertEquals(setOf("token-for-first", "token-for-second"), accessTokens)
            val idpRequests = executor.requestsTo("$idpIssuer/v1/token")
            val audiencesSent = idpRequests.map { formParams(it)["audience"] }.toSet()
            assertEquals(setOf(targetIssuer, secondTargetIssuer), audiencesSent)
        }

    @Test
    fun start_WithNoResource_OmitsResourceParam() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor, resource = null)).getOrThrow()

            flow.start(SubjectAssertion.idToken("id-token"))

            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertNull(params["resource"])
        }

    @Test
    fun start_WithResourceConfigured_SendsResourceParam() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow =
                CrossAppAccessFlow
                    .create(buildIdpClient(executor), buildTarget(executor, resource = "https://resource.example.com/api"))
                    .getOrThrow()

            flow.start(SubjectAssertion.idToken("id-token"))

            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("https://resource.example.com/api", params["resource"])
        }

    @Test
    fun start_WithNoScopeAnywhere_FailsBeforeAnyNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val target = buildTarget(executor, scope = null)
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("id-token"))

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message
            assertTrue(message != null && message.contains("scope", ignoreCase = true))
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun start_WithNoTargetScopeButPerCallScope_SendsPerCallScope() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor, scope = null)).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("id-token"), scope = listOf("per-call.scope"))

            assertTrue(result.isSuccess)
            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("per-call.scope", params["scope"])
        }

    @Test
    fun start_PerCallScope_TakesPrecedenceOverTargetScope() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor, scope = listOf("target.scope"))).getOrThrow()

            flow.start(SubjectAssertion.idToken("id-token"), scope = listOf("per-call.scope"))

            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("per-call.scope", params["scope"])
        }

    @Test
    fun start_GrantedScopeIsCarriedVerbatimAsAString() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            stubTargetDiscovery(executor)
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse(scope = "a b c"))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), buildTarget(executor)).getOrThrow()

            val idJag = flow.start(SubjectAssertion.idToken("id-token")).getOrThrow()

            assertEquals("a b c", idJag.scope)
        }

    @Test
    fun start_WithAuthorizationServerIdTarget_DefaultAudienceIsResolvedIssuer() =
        runTest {
            val executor = RoutingApiExecutor()
            executor.stub("$idpIssuer/.well-known/openid-configuration", 200, discoveryDocument(idpIssuer))
            val resolvedTargetIssuer = "$idpIssuer/oauth2/default"
            executor.stub("$resolvedTargetIssuer/.well-known/openid-configuration", 200, discoveryDocument(resolvedTargetIssuer))
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val target =
                CrossAppAccessTarget.forAuthorizationServerId("default") {
                    scope = listOf("chat.read")
                    clientSecret = "target-secret"
                    clientBuildAction = { apiExecutor = executor }
                }
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            flow.start(SubjectAssertion.idToken("id-token"))

            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals(resolvedTargetIssuer, params["audience"])
        }

    @Test
    fun start_WithWrappedNonStandardIssuer_SendsResolvedValueVerbatimAsAudience() =
        runTest {
            val executor = RoutingApiExecutor()
            stubIdpDiscovery(executor)
            // A caller-supplied target client's resolved issuer, deliberately not shaped like
            // either the primary's origin or an Okta custom-AS path — CrossAppAccessFlow must
            // send it exactly as configured rather than deriving or normalizing it.
            val nonStandardIssuer = "https://custom-target.internal:8443"
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            val targetClient =
                OAuth2ClientBuilder
                    .create(nonStandardIssuer, "target-client-id", listOf("chat.read")) {
                        apiExecutor = executor
                        clientSecret = "target-secret"
                        endpointOverrides =
                            OAuth2EndpointOverrides(
                                authorizationEndpoint = "https://resource.example.com/authorize",
                                tokenEndpoint = "https://resource.example.com/token",
                                userInfoEndpoint = "https://resource.example.com/userinfo",
                                jwksUri = "https://resource.example.com/keys",
                                introspectionEndpoint = "https://resource.example.com/introspect",
                                revocationEndpoint = "https://resource.example.com/revoke",
                                endSessionEndpoint = "https://resource.example.com/logout",
                                deviceAuthorizationEndpoint = "https://resource.example.com/device"
                            )
                    }.getOrThrow()
            val target = CrossAppAccessTarget.wrapping(targetClient, scope = listOf("chat.read"))
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("id-token"))

            assertTrue(result.isSuccess)
            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals(nonStandardIssuer, params["audience"])
        }
}
