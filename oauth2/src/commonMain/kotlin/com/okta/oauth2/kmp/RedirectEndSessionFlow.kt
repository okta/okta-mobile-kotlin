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

import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * [RedirectEndSessionFlow] encapsulates the behavior required to log out using an OIDC browser
 * redirect flow.
 *
 * Call [start] to build the logout URL, then open it in a browser. Once the user is redirected
 * back to your app, call [resume] with the redirect URI to complete the flow.
 */
interface RedirectEndSessionFlow {
    /**
     * Initiates the redirect end-session flow.
     *
     * @param idToken the ID token hint identifying the session to log out.
     * @param redirectUrl the post-logout redirect URI registered with the authorization server.
     * @param extraRequestParameters additional key-value pairs appended to the end-session URL.
     * @return a [Result] containing a [RedirectEndSessionFlowContext] with the logout URL and state.
     */
    suspend fun start(
        idToken: String,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
    ): Result<RedirectEndSessionFlowContext>

    /**
     * Resumes the redirect end-session flow after the browser redirect.
     *
     * @param uri the redirect URI received from the browser after logout.
     * @param flowContext the [RedirectEndSessionFlowContext] returned by [start].
     * @return a [Result] containing [Unit] on success, or a failure wrapping a [ResumeException].
     */
    fun resume(
        uri: String,
        flowContext: RedirectEndSessionFlowContext,
    ): Result<Unit>

    /**
     * Thrown from [resume] when the logout redirect cannot be completed successfully.
     *
     * @param message a human-readable description of the failure.
     */
    class ResumeException internal constructor(
        message: String,
    ) : Exception(message)

    companion object {
        /**
         * Creates a [RedirectEndSessionFlow] backed by the given [OAuth2Client].
         *
         * @param client the KMP [OAuth2Client] to use for this flow.
         */
        operator fun invoke(client: OAuth2Client): RedirectEndSessionFlow = RedirectEndSessionFlowImpl(client)
    }
}
