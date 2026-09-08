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
package com.okta.idx.kotlin.kmp

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * The InteractionCodeFlow class is used to define and initiate an authentication workflow utilizing the Okta Identity Engine.
 *
 * Usage: call [start] to begin the workflow, then [resume] to retrieve the initial [IdxResponse]. From there, repeatedly set the
 * desired form field values on a remediation and call [proceed] to advance through the workflow until [IdxResponse.isLoginSuccessful]
 * is `true`, then call [exchangeInteractionCodeForTokens] to obtain tokens. All of these are `suspend` functions and must be called
 * from a coroutine.
 *
 * This is the cross-platform (`android` + `jvm`) counterpart to the frozen, Android-only
 * `com.okta.idx.kotlin.client.InteractionCodeFlow` — see `research.md`/`data-model.md` for why both
 * exist side by side. Construct an instance via [InteractionCodeFlow.invoke]; the interface is never
 * implemented outside this module.
 */
interface InteractionCodeFlow {
    /** The [OAuth2Client] used to make authorization-server requests for this flow. */
    val client: OAuth2Client

    /** Thrown by [resume], [proceed], or [exchangeInteractionCodeForTokens] when called before [start] completes. */
    class FlowNotStartedException : Exception("InteractionCodeFlow not started.")

    /** Thrown by [exchangeInteractionCodeForTokens] when the given remediation is not [IdxRemediation.Type.ISSUE]. */
    class InvalidRemediationException : Exception("Invalid remediation.")

    /** Thrown by [evaluateRedirectUri] when the redirect URI does not match the configured redirect URL. */
    class RedirectUriMismatchException : Exception("IDP redirect failed due not matching the configured redirect uri.")

    /** Thrown by [evaluateRedirectUri] when the redirect's `state` parameter doesn't match the value captured at [start]. */
    class StateMismatchException : Exception("IDP redirect failed due to state mismatch.")

    /** Thrown by [evaluateRedirectUri] when the redirect carries an IdP/server error (`error`/`error_description`). */
    class RedirectErrorException(
        val errorId: String,
        message: String,
    ) : Exception(message)

    /** Thrown by [evaluateRedirectUri] when the redirect URI matches no recognized outcome (no error, no interaction_code). */
    class UnhandledRedirectException : Exception("Unable to handle redirect url.")

    /**
     * Starts the authentication session.
     *
     * @param redirectUri The redirect uri.
     * @param extraStartRequestParameters Extra URL parameters to include in start request.
     */
    suspend fun start(
        redirectUri: String,
        extraStartRequestParameters: Map<String, String> = emptyMap(),
    ): Result<Unit>

    /**
     * Resumes the authentication state to identify the available remediation steps.
     *
     * Must be called after [start]; otherwise returns `Result.failure(FlowNotStartedException())`.
     */
    suspend fun resume(): Result<IdxResponse>

    /**
     * Executes the given remediation and advances the workflow using the values currently set on its form fields.
     *
     * @param remediation the remediation to submit, obtained from [IdxResponse.remediations].
     */
    suspend fun proceed(remediation: IdxRemediation): Result<IdxResponse>

    /**
     * Exchanges the [IdxRemediation.Type.ISSUE] remediation for tokens once [IdxResponse.isLoginSuccessful] is `true`.
     *
     * @param remediation the [IdxRemediation.Type.ISSUE] remediation from the successful [IdxResponse].
     */
    suspend fun exchangeInteractionCodeForTokens(remediation: IdxRemediation): Result<TokenInfo>

    /**
     * Evaluates the given redirect url to determine what next steps can be performed. This is usually used when receiving a redirection from an IDP authentication flow.
     *
     * @param uri the redirect URI received from the browser or IDP redirection.
     */
    suspend fun evaluateRedirectUri(uri: String): Result<IdxRedirectOutcome>

    companion object {
        /** Creates an [InteractionCodeFlow] backed by the given [OAuth2Client]. */
        operator fun invoke(client: OAuth2Client): InteractionCodeFlow = InteractionCodeFlowImpl(client)
    }
}
