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

import com.okta.directauth.model.DirectAuthenticationError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import com.okta.directauth.model.DirectAuthenticationState as KotlinDirectAuthenticationState

/** Tests verifying that [toJvm] maps each Kotlin [KotlinDirectAuthenticationState] to the correct JVM wrapper. */
class StateMappingKotlinTest {
    @Test
    fun toJvm_Idle_ReturnsIdleWrapper() {
        val kotlinState = KotlinDirectAuthenticationState.Idle

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.Idle>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_Canceled_ReturnsCanceledWrapper() {
        val kotlinState = KotlinDirectAuthenticationState.Canceled

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.Canceled>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_AuthorizationPending_ReturnsAuthorizationPendingWrapper() {
        val kotlinState = KotlinDirectAuthenticationState.AuthorizationPending(1654041600L)

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.AuthorizationPending>(result)
        assertSame(kotlinState, result.state)
        kotlin.test.assertEquals(1654041600L, result.timestamp)
    }

    @Test
    fun toJvm_Authenticated_ReturnsAuthenticatedWrapper() {
        val kotlinState = TestStateFactory.createAuthenticated()

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.Authenticated>(result)
        assertSame(kotlinState, result.state)
        assertSame(kotlinState.token, result.token)
    }

    @Test
    fun toJvm_MfaRequired_ReturnsMfaRequiredWrapper() {
        val kotlinState = TestStateFactory.createMfaRequired()

        val result = kotlinState.toJvm()

        assertIs<MfaRequired>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_WebAuthn_ReturnsWebAuthnContinuationWrapper() {
        val kotlinState = TestStateFactory.createWebAuthn(null)

        val result = kotlinState.toJvm()

        assertIs<WebAuthnContinuation>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_Prompt_ReturnsPromptContinuationWrapper() {
        val kotlinState = TestStateFactory.createPrompt()

        val result = kotlinState.toJvm()

        assertIs<PromptContinuation>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_Transfer_ReturnsTransferContinuationWrapper() {
        val kotlinState = TestStateFactory.createTransfer()

        val result = kotlinState.toJvm()

        assertIs<TransferContinuation>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_OobPending_ReturnsOobPendingContinuationWrapper() {
        val kotlinState = TestStateFactory.createOobPending()

        val result = kotlinState.toJvm()

        assertIs<OobPendingContinuation>(result)
        assertSame(kotlinState, result.state)
    }

    @Test
    fun toJvm_InternalError_ReturnsInternalErrorWrapper() {
        val cause = RuntimeException("test failure")
        val kotlinState = DirectAuthenticationError.InternalError("E001", "something broke", cause)

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.Error.InternalError>(result)
        assertSame(kotlinState, result.state)
        kotlin.test.assertEquals("E001", result.errorCode)
        kotlin.test.assertEquals("something broke", result.description)
        assertSame(cause, result.throwable)
    }

    @Test
    fun toJvm_ApiError_ReturnsApiErrorWrapper() {
        val kotlinState =
            DirectAuthenticationError.HttpError.ApiError(
                errorCode = "E0000004",
                errorSummary = "Authentication failed",
                errorLink = "E0000004",
                errorId = "abc123",
                errorCauses = listOf("Invalid credentials"),
                httpStatusCode = HttpStatusCode.Unauthorized
            )

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.Error.HttpError.ApiError>(result)
        assertSame(kotlinState, result.state)
        kotlin.test.assertEquals("E0000004", result.errorCode)
        kotlin.test.assertEquals("Authentication failed", result.errorSummary)
        kotlin.test.assertEquals("E0000004", result.errorLink)
        kotlin.test.assertEquals("abc123", result.errorId)
        kotlin.test.assertEquals(listOf("Invalid credentials"), result.errorCauses)
        kotlin.test.assertEquals(HttpStatusCode.Unauthorized, result.httpStatusCode)
    }

    @Test
    fun toJvm_Oauth2Error_ReturnsOauth2ErrorWrapper() {
        val kotlinState =
            DirectAuthenticationError.HttpError.Oauth2Error(
                error = "invalid_grant",
                httpStatusCode = HttpStatusCode.BadRequest,
                errorDescription = "The provided grant is invalid"
            )

        val result = kotlinState.toJvm()

        assertIs<DirectAuthenticationState.Error.HttpError.Oauth2Error>(result)
        assertSame(kotlinState, result.state)
        kotlin.test.assertEquals("invalid_grant", result.error)
        kotlin.test.assertEquals("The provided grant is invalid", result.errorDescription)
        kotlin.test.assertEquals(HttpStatusCode.BadRequest, result.httpStatusCode)
    }
}
