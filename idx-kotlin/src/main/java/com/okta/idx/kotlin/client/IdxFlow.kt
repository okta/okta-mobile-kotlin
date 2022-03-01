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
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
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
 * The IdxFlow class is used to define and initiate an authentication workflow utilizing the Okta Identity Engine.
 */
class IdxFlow internal constructor(
    private val oidcClient: OidcClient,
    val flowContext: IdxFlowContext,
) {
    companion object {
        /**
         * Used to create an IdxFlow, and to start an authorization flow.
         */
        suspend fun OidcClient.idxFlow(
            extraStartRequestParameters: Map<String, String> = emptyMap(),
        ): OidcClientResult<IdxFlow> {
            val interactContext = withContext(configuration.computeDispatcher) {
                InteractContext.create(this@idxFlow, extraStartRequestParameters)
            } ?: return endpointNotAvailableError()

            return configuration.performRequest<InteractResponse, IdxFlow>(
                InteractResponse.serializer(),
                interactContext.request
            ) {
                val clientContext = IdxFlowContext(
                    codeVerifier = interactContext.codeVerifier,
                    interactionHandle = it.interactionHandle,
                    state = interactContext.state,
                )
                IdxFlow(
                    oidcClient = this,
                    flowContext = clientContext,
                )
            }
        }
    }

    /**
     * Resumes the authentication state to identify the available remediation steps.
     *
     * This method is usually performed after an IdxFlow is created, but can also be called at any time to identify what next remediation steps are available to the user.
     */
    suspend fun resume(): OidcClientResult<IdxResponse> {
        val request = withContext(oidcClient.configuration.computeDispatcher) {
            introspectRequest(oidcClient, flowContext)
        }

        return oidcClient.configuration.performRequest(
            deserializationStrategy = V1Response.serializer(),
            request = request,
            shouldAttemptJsonDeserialization = ::idxShouldAttemptJsonDeserialization,
        ) {
            it.toIdxResponse(oidcClient.configuration.json)
        }
    }

    /**
     * Executes the remediation option and proceeds through the workflow using the supplied form parameters.
     *
     * This method is used to proceed through the authentication flow, using the data assigned to the nested fields' `value` to make selections.
     */
    suspend fun proceed(remediation: IdxRemediation): OidcClientResult<IdxResponse> {
        val request = withContext(oidcClient.configuration.computeDispatcher) {
            remediation.asJsonRequest(oidcClient)
        }

        return oidcClient.configuration.performRequest(
            deserializationStrategy = V1Response.serializer(),
            request = request,
            shouldAttemptJsonDeserialization = ::idxShouldAttemptJsonDeserialization,
        ) {
            it.toIdxResponse(oidcClient.configuration.json).also { response ->
                response.remediations.firstOrNull()?.copyValuesFromPrevious(remediation)
            }
        }
    }

    /**
     * Exchange the IdxRemediation.Type.ISSUE remediation type for tokens.
     */
    suspend fun exchangeInteractionCodeForTokens(
        remediation: IdxRemediation
    ): OidcClientResult<Token> {
        if (remediation.type != IdxRemediation.Type.ISSUE) {
            return OidcClientResult.Error(IllegalStateException("Invalid remediation."))
        }

        val request = withContext(oidcClient.configuration.computeDispatcher) {
            remediation["code_verifier"]?.value = flowContext.codeVerifier

            remediation.asFormRequest()
        }

        return oidcClient.tokenRequest(request)
    }

    /**
     * Evaluates the given redirect url to determine what next steps can be performed. This is usually used when receiving a redirection from an IDP authentication flow.
     */
    suspend fun evaluateRedirectUri(uri: Uri): IdxRedirectResult {
        if (!uri.toString().startsWith(oidcClient.configuration.signInRedirectUri)) {
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
                is OidcClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to resume.", resumeResult.exception)
                }
                is OidcClientResult.Success -> {
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
            return when (val result =
                exchangeInteractionCodeForTokens(interactionCodeQueryParameter)) {
                is OidcClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to exchangeCodes.", result.exception)
                }
                is OidcClientResult.Success -> {
                    IdxRedirectResult.Tokens(result.result)
                }
            }
        }
        return IdxRedirectResult.Error("Unable to handle redirect url.")
    }

    private suspend fun exchangeInteractionCodeForTokens(interactionCode: String): OidcClientResult<Token> {
        val request = withContext(oidcClient.configuration.computeDispatcher) {
            tokenRequestFromInteractionCode(oidcClient, flowContext, interactionCode)
        }

        return oidcClient.tokenRequest(request)
    }

    /** IdxResponse can come back in both HTTP 200 as well as others such as 400s. */
    private fun idxShouldAttemptJsonDeserialization(response: Response): Boolean {
        return true
    }
}
