/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.client

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.client.internal.performRequest
import com.okta.authfoundation.credential.Token
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.idx.kotlin.dto.copyValuesFromPrevious
import com.okta.idx.kotlin.dto.v1.InteractContext
import com.okta.idx.kotlin.dto.v1.InteractResponse
import com.okta.idx.kotlin.dto.v1.asFormRequest
import com.okta.idx.kotlin.dto.v1.asJsonRequest
import com.okta.idx.kotlin.dto.v1.introspectRequest
import com.okta.idx.kotlin.dto.v1.toIdxResponse
import com.okta.idx.kotlin.dto.v1.tokenRequestFromInteractionCode
import kotlinx.coroutines.withContext
import okhttp3.Response
import com.okta.idx.kotlin.dto.v1.Response as V1Response

/**
 * The InteractionCodeFlow class is used to define and initiate an authentication workflow utilizing the Okta Identity Engine.
 */
class InteractionCodeFlow constructor(private val client: OAuth2Client) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }
    }

    @VisibleForTesting
    internal lateinit var flowContext: InteractionCodeFlowContext

    /**
     * Initializes a new [InteractionCodeFlow].
     */
    constructor() : this(OAuth2Client.default)

    /**
     * Initializes a new [InteractionCodeFlow].
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     */
    constructor(oidcConfiguration: OidcConfiguration) : this(
        OAuth2Client.createFromConfiguration(oidcConfiguration)
    )

    /**
     * Starts the authentication session. Returns an empty [OAuth2ClientResult.Success] on success, and
     * a [OAuth2ClientResult.Error] with the corresponding exception on failure.
     *
     * @param redirectUri The redirect uri.
     * @param extraStartRequestParameters Extra URL parameters to include in start request.
     */
    suspend fun start(
        redirectUri: Uri,
        extraStartRequestParameters: Map<String, String> = emptyMap()
    ): OAuth2ClientResult<Unit> {
        val redirectUriString = redirectUri.toString()
        val interactContext = withContext(AuthFoundationDefaults.computeDispatcher) {
            InteractContext.create(
                client = client,
                redirectUrl = redirectUriString,
                extraParameters = extraStartRequestParameters,
            )
        } ?: return client.endpointNotAvailableError()

        return client.performRequest(
            InteractResponse.serializer(),
            interactContext.request
        ) {
            flowContext = InteractionCodeFlowContext(
                codeVerifier = interactContext.codeVerifier,
                interactionHandle = it.interactionHandle,
                state = interactContext.state,
                redirectUrl = redirectUriString,
                nonce = interactContext.nonce,
                maxAge = interactContext.maxAge,
            )
        }
    }

    /**
     * Resumes the authentication state to identify the available remediation steps.
     *
     * This method is usually performed after an InteractionCodeFlow is created, but can also be called at any time to identify what next remediation steps are available to the user.
     */
    suspend fun resume(): OAuth2ClientResult<IdxResponse> {
        val request = withContext(client.configuration.computeDispatcher) {
            introspectRequest(client, flowContext)
        }

        return client.performRequest(
            deserializationStrategy = V1Response.serializer(),
            request = request,
            shouldAttemptJsonDeserialization = ::idxShouldAttemptJsonDeserialization,
        ) {
            it.toIdxResponse(client.configuration.json)
        }
    }

    /**
     * Executes the remediation option and proceeds through the workflow using the supplied form parameters.
     *
     * This method is used to proceed through the authentication flow, using the data assigned to the nested fields' `value` to make selections.
     */
    suspend fun proceed(remediation: IdxRemediation): OAuth2ClientResult<IdxResponse> {
        val request = withContext(client.configuration.computeDispatcher) {
            remediation.asJsonRequest(client)
        }

        return client.performRequest(
            deserializationStrategy = V1Response.serializer(),
            request = request,
            shouldAttemptJsonDeserialization = ::idxShouldAttemptJsonDeserialization,
        ) {
            it.toIdxResponse(client.configuration.json).also { response ->
                response.remediations.firstOrNull()?.copyValuesFromPrevious(remediation)
            }
        }
    }

    /**
     * Exchange the IdxRemediation.Type.ISSUE remediation type for tokens.
     */
    suspend fun exchangeInteractionCodeForTokens(
        remediation: IdxRemediation
    ): OAuth2ClientResult<Token> {
        if (remediation.type != IdxRemediation.Type.ISSUE) {
            return OAuth2ClientResult.Error(IllegalStateException("Invalid remediation."))
        }

        val request = withContext(client.configuration.computeDispatcher) {
            remediation["code_verifier"]?.value = flowContext.codeVerifier

            remediation.asFormRequest()
        }

        return client.tokenRequest(request, flowContext.nonce, flowContext.maxAge)
    }

    /**
     * Evaluates the given redirect url to determine what next steps can be performed. This is usually used when receiving a redirection from an IDP authentication flow.
     */
    suspend fun evaluateRedirectUri(uri: Uri): IdxRedirectResult {
        if (!uri.toString().startsWith(flowContext.redirectUrl)) {
            val error = "IDP redirect failed due not matching the configured redirect uri."
            return IdxRedirectResult.Error(error)
        }
        val errorQueryParameter = uri.getQueryParameter("error")
        val stateQueryParameter = uri.getQueryParameter("state")
        if (errorQueryParameter == "interaction_required") {
            // Validate the state matches. This is a security assurance.
            if (flowContext.state != stateQueryParameter) {
                val error = "IDP redirect failed due to state mismatch."
                return IdxRedirectResult.Error(error)
            }
            return when (val resumeResult = resume()) {
                is OAuth2ClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to resume.", resumeResult.exception)
                }

                is OAuth2ClientResult.Success -> {
                    IdxRedirectResult.InteractionRequired(resumeResult.result)
                }
            }
        }
        if (errorQueryParameter != null) {
            val errorDescription =
                uri.getQueryParameter("error_description") ?: "An error occurred."
            return IdxRedirectResult.Error(errorDescription)
        }
        val interactionCodeQueryParameter = uri.getQueryParameter("interaction_code")
        if (interactionCodeQueryParameter != null) {
            // Validate the state matches. This is a security assurance.
            if (flowContext.state != stateQueryParameter) {
                val error = "IDP redirect failed due to state mismatch."
                return IdxRedirectResult.Error(error)
            }
            return when (
                val result = exchangeInteractionCodeForTokens(interactionCodeQueryParameter)
            ) {
                is OAuth2ClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to exchangeCodes.", result.exception)
                }

                is OAuth2ClientResult.Success -> {
                    IdxRedirectResult.Tokens(result.result)
                }
            }
        }
        return IdxRedirectResult.Error("Unable to handle redirect url.")
    }

    private suspend fun exchangeInteractionCodeForTokens(interactionCode: String): OAuth2ClientResult<Token> {
        val request = withContext(client.configuration.computeDispatcher) {
            tokenRequestFromInteractionCode(client, flowContext, interactionCode)
        }

        return client.tokenRequest(request, flowContext.nonce, flowContext.maxAge)
    }

    /** IdxResponse can come back in both HTTP 200 as well as others such as 400s. */
    private fun idxShouldAttemptJsonDeserialization(response: Response): Boolean {
        return response.code < 500
    }
}
