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
import com.okta.authfoundation.api.http.ApiRequest
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.dto.IntrospectInfo
import com.okta.authfoundation.client.dto.OidcUserInfo
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.authfoundation.jwt.Jwt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeSubjectTokenInfo(
    override val idToken: String? = null,
    override val accessToken: String = "fake-access-token",
    override val refreshToken: String? = null,
    override val clientId: String = "idp-client-id",
    override val issuerUrl: String = "https://example.okta.com",
) : TokenInfo {
    override val id: String = "fake-token-id"
    override val tokenType: String = "Bearer"
    override val expiresIn: Int = 3600
    override val scope: String? = "openid"
    override val deviceSecret: String? = null
    override val issuedTokenType: String? = null
}

/** A fake [Credential] — it is an interface, so no storage backing is needed for this test. */
private class FakeCredential(
    override val token: TokenInfo,
) : Credential {
    override val id: String = "fake-credential-id"
    override val tags: Map<String, String> = emptyMap()

    override suspend fun deleteAsync(): Result<Unit> = notImplemented()

    override fun getTokenFlow(): Flow<TokenInfo> = notImplemented()

    override suspend fun getUserInfo(): Result<OidcUserInfo> = notImplemented()

    override suspend fun revokeToken(tokenType: RevokeTokenType): Result<Unit> = notImplemented()

    override suspend fun revokeAllTokens(): Result<Unit> = notImplemented()

    override suspend fun refreshIfExpired(): Result<Credential> = notImplemented()

    override fun accessTokenIfNotExpired(): String? = notImplemented()

    override suspend fun setTagsAsync(tags: Map<String, String>): Result<Credential> = notImplemented()

    override suspend fun introspectToken(tokenType: TokenType): Result<IntrospectInfo> = notImplemented()

    override suspend fun refreshToken(): Result<Credential> = notImplemented()

    override fun idToken(): Result<Jwt> = notImplemented()

    override fun scope(): List<String> = notImplemented()

    private fun notImplemented(): Nothing = throw NotImplementedError("not used by this test")
}

class CrossAppAccessCredentialExtensionsTest {
    private val idpIssuer = "https://example.okta.com"
    private val targetIssuer = "https://resource.example.com"

    private fun formParams(request: ApiRequest): Map<String, String> = assertIs<ApiFormRequest>(request).formParameters().mapValues { (_, v) -> v.first() }

    private fun buildIdpClient(executor: RoutingApiExecutor) =
        OAuth2ClientBuilder
            .create(idpIssuer, "idp-client-id", listOf("openid")) {
                apiExecutor = executor
            }.getOrThrow()

    private fun buildTarget(executor: RoutingApiExecutor) =
        CrossAppAccessTarget.forIssuer(targetIssuer) {
            scope = listOf("chat.read")
            clientSecret = "target-secret"
            clientBuildAction = { apiExecutor = executor }
        }

    @Test
    fun crossAppAccessSubject_WithIdToken_DerivesIdTokenSubjectAssertion() =
        runTest {
            val credential = FakeCredential(FakeSubjectTokenInfo(idToken = "the-id-token"))

            val subject = credential.crossAppAccessSubject(SubjectAssertion.Type.ID_TOKEN).getOrThrow()

            assertEquals(SubjectAssertion.Type.ID_TOKEN, subject.type)
            assertEquals("the-id-token", subject.value)
        }

    @Test
    fun crossAppAccessSubject_WithAccessToken_DerivesAccessTokenSubjectAssertion() =
        runTest {
            val credential = FakeCredential(FakeSubjectTokenInfo(accessToken = "the-access-token"))

            val subject = credential.crossAppAccessSubject(SubjectAssertion.Type.ACCESS_TOKEN).getOrThrow()

            assertEquals(SubjectAssertion.Type.ACCESS_TOKEN, subject.type)
            assertEquals("the-access-token", subject.value)
        }

    @Test
    fun crossAppAccessSubject_WithRefreshToken_DerivesRefreshTokenSubjectAssertion() =
        runTest {
            val credential = FakeCredential(FakeSubjectTokenInfo(refreshToken = "the-refresh-token"))

            val subject = credential.crossAppAccessSubject(SubjectAssertion.Type.REFRESH_TOKEN).getOrThrow()

            assertEquals(SubjectAssertion.Type.REFRESH_TOKEN, subject.type)
            assertEquals("the-refresh-token", subject.value)
        }

    @Test
    fun crossAppAccessSubject_DefaultsToIdToken() =
        runTest {
            val credential = FakeCredential(FakeSubjectTokenInfo(idToken = "the-default-id-token"))

            val subject = credential.crossAppAccessSubject().getOrThrow()

            assertEquals(SubjectAssertion.Type.ID_TOKEN, subject.type)
        }

    @Test
    fun crossAppAccessSubject_WhenRequestedTokenAbsent_FailsDescriptively() =
        runTest {
            val credential = FakeCredential(FakeSubjectTokenInfo(idToken = null))

            val result = credential.crossAppAccessSubject(SubjectAssertion.Type.ID_TOKEN)

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message
            assertTrue(message != null && message.contains("id", ignoreCase = true))
        }

    @Test
    fun crossAppAccessToken_ReturnsResourceAccessTokenWithoutMutatingCredential() =
        runTest {
            val executor = RoutingApiExecutor()
            executor.stub("$idpIssuer/.well-known/openid-configuration", 200, discoveryDocument(idpIssuer))
            executor.stub("$targetIssuer/.well-known/openid-configuration", 200, discoveryDocument(targetIssuer))
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val idpClient = buildIdpClient(executor)
            val target = buildTarget(executor)
            val credential = FakeCredential(FakeSubjectTokenInfo(idToken = "the-id-token"))

            val result = credential.crossAppAccessToken(idpClient, target)

            assertTrue(result.isSuccess)
            assertEquals("resource-access-token", result.getOrThrow().accessToken)
            // The source credential's snapshot is untouched — same token, not replaced or invalidated.
            assertEquals("the-id-token", credential.token.idToken)
        }

    @Test
    fun crossAppAccessToken_WithAccessTokenSubjectType_SendsAccessTokenAsSubject() =
        runTest {
            val executor = RoutingApiExecutor()
            executor.stub("$idpIssuer/.well-known/openid-configuration", 200, discoveryDocument(idpIssuer))
            executor.stub("$targetIssuer/.well-known/openid-configuration", 200, discoveryDocument(targetIssuer))
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponse())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponse())
            val idpClient = buildIdpClient(executor)
            val target = buildTarget(executor)
            val credential =
                FakeCredential(FakeSubjectTokenInfo(idToken = "the-id-token", accessToken = "the-access-token"))

            val result = credential.crossAppAccessToken(idpClient, target, subjectType = SubjectAssertion.Type.ACCESS_TOKEN)

            assertTrue(result.isSuccess)
            val params = formParams(executor.requestsTo("$idpIssuer/v1/token").single())
            assertEquals("the-access-token", params["subject_token"])
            assertEquals("urn:ietf:params:oauth:token-type:access_token", params["subject_token_type"])
        }

    @Test
    fun crossAppAccessToken_WhenIdpClientIssuerMismatchesCredential_FailsWithoutNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val mismatchedIdpClient =
                OAuth2ClientBuilder
                    .create("https://other.okta.com", "idp-client-id", listOf("openid")) {
                        apiExecutor = executor
                    }.getOrThrow()
            val target = buildTarget(executor)
            val credential = FakeCredential(FakeSubjectTokenInfo(idToken = "the-id-token"))

            val result = credential.crossAppAccessToken(mismatchedIdpClient, target)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            val message = result.exceptionOrNull()?.message
            assertTrue(message != null && message.contains("issuer", ignoreCase = true))
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun crossAppAccessToken_WhenIdpClientIdMismatchesCredential_FailsWithoutNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val mismatchedIdpClient =
                OAuth2ClientBuilder
                    .create(idpIssuer, "other-client-id", listOf("openid")) {
                        apiExecutor = executor
                    }.getOrThrow()
            val target = buildTarget(executor)
            val credential = FakeCredential(FakeSubjectTokenInfo(idToken = "the-id-token"))

            val result = credential.crossAppAccessToken(mismatchedIdpClient, target)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            val message = result.exceptionOrNull()?.message
            assertTrue(message != null && message.contains("client ID", ignoreCase = true))
            assertEquals(emptyList(), executor.capturedRequests)
        }

    @Test
    fun crossAppAccessToken_WhenCredentialLacksRequestedToken_MakesNoNetworkRequest() =
        runTest {
            val executor = RoutingApiExecutor()
            val idpClient = buildIdpClient(executor)
            val target = buildTarget(executor)
            val credential = FakeCredential(FakeSubjectTokenInfo(refreshToken = null))

            val result = credential.crossAppAccessToken(idpClient, target)

            assertTrue(result.isFailure)
            assertEquals(emptyList(), executor.capturedRequests)
        }
}
