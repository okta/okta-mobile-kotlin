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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.jvm.AuthFoundationResult
import com.okta.oauth2.kmp.crossAppAccessSubject
import com.okta.oauth2.kmp.crossAppAccessToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import com.okta.authfoundation.client.kmp.OAuth2Client as KmpOAuth2Client
import com.okta.authfoundation.credential.kmp.Credential as KmpCredential
import com.okta.oauth2.kmp.CrossAppAccessFlow as KotlinCrossAppAccessFlow
import com.okta.oauth2.kmp.CrossAppAccessTarget as KotlinCrossAppAccessTarget
import com.okta.oauth2.kmp.IdJagAssertion as KotlinIdJagAssertion
import com.okta.oauth2.kmp.SubjectAssertion as KotlinSubjectAssertion

/**
 * A Java-friendly wrapper around the Kotlin [KotlinCrossAppAccessFlow].
 *
 * This class exposes async methods returning [CompletableFuture] so Java consumers can perform a
 * Cross App Access exchange without dealing with Kotlin coroutines.
 *
 * Typical Java usage:
 * ```java
 * CrossAppAccessTarget target = CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
 *     .setClientSecret("target-secret")
 *     .build();
 * AuthFoundationResult<CrossAppAccessFlow> result = CrossAppAccessFlow.create(idpClient, target);
 * CrossAppAccessFlow flow = result.getOrThrow();
 * TokenInfo resourceToken = flow.exchange(SubjectAssertion.idToken(idToken)).get();
 * flow.close();
 * ```
 *
 * Must be [closed][close] when no longer needed to release coroutine resources.
 *
 * @param delegate the underlying Kotlin [KotlinCrossAppAccessFlow] instance.
 */
class CrossAppAccessFlow(
    private val delegate: KotlinCrossAppAccessFlow,
) : Closeable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The **IdP authorization server** client — used for [start]. */
    fun getIdpClient(): KmpOAuth2Client = delegate.idpClient

    /** The **resource authorization server** client — used for [redeem]. */
    fun getTargetClient(): KmpOAuth2Client = delegate.targetClient

    /**
     * Exchanges [subjectAssertion] for an ID-JAG at the IdP authorization server.
     *
     * @param subjectAssertion the signed-in user's assertion to present.
     * @param scope requested scopes at the target, taking precedence over any configured on the
     *   target itself. Pass `null` to rely on the target's configured scopes.
     * @return a [CompletableFuture] that completes with the [KotlinIdJagAssertion] on success, or
     *   completes exceptionally with a `CrossAppAccessException` on failure.
     */
    @JvmOverloads
    fun start(
        subjectAssertion: KotlinSubjectAssertion,
        scope: List<String>? = null,
    ): CompletableFuture<KotlinIdJagAssertion> = coroutineScope.future { delegate.start(subjectAssertion, scope).getOrThrow() }

    /**
     * Redeems [idJag] for a resource access token at the resource authorization server.
     *
     * @param idJag a previously obtained (or restored) assertion.
     * @return a [CompletableFuture] that completes with the resource access [TokenInfo] on
     *   success, or completes exceptionally with a `CrossAppAccessException` on failure.
     */
    fun redeem(idJag: KotlinIdJagAssertion): CompletableFuture<TokenInfo> = coroutineScope.future { delegate.redeem(idJag).getOrThrow() }

    /**
     * Convenience that runs [start] then [redeem] and returns the resulting resource access
     * token.
     *
     * @param subjectAssertion the signed-in user's assertion to present.
     * @param scope requested scopes at the target; see [start].
     * @return a [CompletableFuture] that completes with the resource access [TokenInfo] on
     *   success, or completes exceptionally on failure.
     */
    @JvmOverloads
    fun exchange(
        subjectAssertion: KotlinSubjectAssertion,
        scope: List<String>? = null,
    ): CompletableFuture<TokenInfo> = coroutineScope.future { delegate.exchange(subjectAssertion, scope).getOrThrow() }

    override fun toString(): String = delegate.toString()

    override fun close() {
        coroutineScope.cancel()
    }

    companion object {
        /**
         * Builds the target client from [target] (or adopts one already built), validates that
         * the resulting flow can actually authenticate at both servers, and returns the flow.
         *
         * @param idpClient the primary client, already configured against the IdP authorization
         *   server.
         * @param target the resource authorization server to redeem ID-JAGs at.
         * @return an [AuthFoundationResult] containing the flow on success, or the configuration
         *   failure on failure. Never throws.
         */
        @JvmStatic
        fun create(
            idpClient: KmpOAuth2Client,
            target: KotlinCrossAppAccessTarget,
        ): AuthFoundationResult<CrossAppAccessFlow> =
            KotlinCrossAppAccessFlow.create(idpClient, target).fold(
                onSuccess = { AuthFoundationResult.success(CrossAppAccessFlow(it)) },
                onFailure = { AuthFoundationResult.failure(it) }
            )

        /**
         * Derives a [KotlinSubjectAssertion] from [credential]'s stored token.
         *
         * @param credential the stored credential to derive a subject assertion from.
         * @param type which of the credential's stored tokens to use. Defaults to the ID token.
         * @return a [CompletableFuture] that completes with the [KotlinSubjectAssertion] on
         *   success, or completes exceptionally with an `IllegalStateException` naming the
         *   missing token on failure.
         */
        @JvmStatic
        @JvmOverloads
        fun crossAppAccessSubject(
            credential: KmpCredential,
            type: KotlinSubjectAssertion.Type = KotlinSubjectAssertion.Type.ID_TOKEN,
        ): CompletableFuture<KotlinSubjectAssertion> = CoroutineScope(Dispatchers.Default).future { credential.crossAppAccessSubject(type).getOrThrow() }

        /**
         * One-call convenience that derives a subject assertion from [credential] and runs the
         * complete Cross App Access exchange against [target], without mutating, replacing, or
         * invalidating [credential].
         *
         * @param credential the stored credential to derive a subject assertion from.
         * @param idpClient the primary client, already configured against the IdP authorization
         *   server.
         * @param target the resource authorization server to redeem an ID-JAG at.
         * @param subjectType which of the credential's stored tokens to derive the subject
         *   assertion from; see [crossAppAccessSubject]. Defaults to the ID token.
         * @param scope requested scopes at the target; see [start].
         * @return a [CompletableFuture] that completes with the resource access [TokenInfo] on
         *   success, or completes exceptionally on failure.
         */
        @JvmStatic
        @JvmOverloads
        fun crossAppAccessToken(
            credential: KmpCredential,
            idpClient: KmpOAuth2Client,
            target: KotlinCrossAppAccessTarget,
            subjectType: KotlinSubjectAssertion.Type = KotlinSubjectAssertion.Type.ID_TOKEN,
            scope: List<String>? = null,
        ): CompletableFuture<TokenInfo> = CoroutineScope(Dispatchers.Default).future { credential.crossAppAccessToken(idpClient, target, subjectType, scope).getOrThrow() }
    }
}
