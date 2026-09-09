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

import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.kmp.OAuth2Client
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossAppAccessFlowTest {
    private val idpIssuer = "https://example.okta.com"
    private val targetIssuer = "https://resource.example.com"

    private fun buildIdpClient(executor: RoutingApiExecutor): OAuth2Client =
        OAuth2ClientBuilder
            .create(idpIssuer, "idp-client-id", listOf("openid")) {
                apiExecutor = executor
            }.getOrThrow()

    @Test
    fun redeem_WithBlankAssertion_FailsBeforeAnyNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forIssuer(targetIssuer) {
                    clientSecret = "target-secret"
                    clientBuildAction = { apiExecutor = executor }
                }
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            val result = flow.redeem(IdJagAssertion.restore(value = "   ", audience = targetIssuer, expiresIn = 300, issuedAt = 0L))

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithBlankIssuer_FailsBeforeAnyNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forIssuer(" ") {
                    clientSecret = "target-secret"
                    clientBuildAction = { apiExecutor = executor }
                }

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithBlankAuthorizationServerId_FailsBeforeAnyNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forAuthorizationServerId(" ") {
                    clientSecret = "target-secret"
                    clientBuildAction = { apiExecutor = executor }
                }

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithTargetIssuerMatchingIdpIssuer_FailsBeforeAnyNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forIssuer(idpIssuer) {
                    clientSecret = "target-secret"
                    clientBuildAction = { apiExecutor = executor }
                }

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithTargetIssuerMatchingIdpIssuerModuloTrailingSlash_Fails() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forIssuer("$idpIssuer/") {
                    clientSecret = "target-secret"
                    clientBuildAction = { apiExecutor = executor }
                }

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithNoTargetCredentialConfigured_Fails() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forIssuer(targetIssuer) {
                    clientBuildAction = { apiExecutor = executor }
                }

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithClientBuildActionClearingConfiguredCredential_Fails() =
        runTest {
            val executor = RoutingApiExecutor()
            val target =
                CrossAppAccessTarget.forIssuer(targetIssuer) {
                    clientSecret = "target-secret"
                    clientBuildAction = {
                        apiExecutor = executor
                        clientSecret = ""
                    }
                }

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun create_WithWrappedPublicClient_Fails() =
        runTest {
            val executor = RoutingApiExecutor()
            val publicTargetClient =
                OAuth2ClientBuilder
                    .create(targetIssuer, "target-client-id", listOf("chat.read")) {
                        apiExecutor = executor
                    }.getOrThrow()
            val target = CrossAppAccessTarget.wrapping(publicTargetClient)

            val result = CrossAppAccessFlow.create(buildIdpClient(executor), target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun isExpired_AfterLifetimeElapses_ReturnsTrue() {
        var now = 1_000L
        val clock = OidcClock { now }
        val idJag = IdJagAssertion.restore(value = "assertion-value", audience = targetIssuer, expiresIn = 300, issuedAt = 1_000L)

        assertFalse(idJag.isExpired(clock))
        now = 1_301L
        assertTrue(idJag.isExpired(clock))
    }

    @Test
    fun toString_OfTargetAndAssertions_ContainsNoCredentialMaterial() {
        val target =
            CrossAppAccessTarget.forIssuer(targetIssuer) {
                clientSecret = "super-secret-value"
            }
        val idJag = IdJagAssertion.restore(value = "assertion-jwt-value", audience = targetIssuer, expiresIn = 300, issuedAt = 0L)
        val subject = SubjectAssertion.idToken("the-id-token-value")

        assertFalse(target.toString().contains("super-secret-value"))
        assertFalse(idJag.toString().contains("assertion-jwt-value"))
        assertFalse(subject.toString().contains("the-id-token-value"))
    }

    @Test
    fun failureMessages_ContainNoCredentialMaterial() =
        runTest {
            val executor = RoutingApiExecutor()
            executor.stub("$idpIssuer/.well-known/openid-configuration", 200, discoveryDocument(idpIssuer))
            executor.stub("$targetIssuer/.well-known/openid-configuration", 200, discoveryDocument(targetIssuer))
            executor.enqueue("$idpIssuer/v1/token", 400, errorResponse("access_denied", "denied for subject-token-secret-value"))
            val target =
                CrossAppAccessTarget.forIssuer(targetIssuer) {
                    clientSecret = "target-secret-value"
                    clientBuildAction = { apiExecutor = executor }
                }
            val flow = CrossAppAccessFlow.create(buildIdpClient(executor), target).getOrThrow()

            val result = flow.start(SubjectAssertion.idToken("subject-token-secret-value"))

            val message = result.exceptionOrNull()?.message.orEmpty()
            assertFalse(message.contains("target-secret-value"))
        }
}
