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
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.idx.kotlin.dto.TokenResponse
import com.okta.idx.kotlin.dto.copyValuesFromPrevious
import com.okta.idx.kotlin.dto.v1.InteractContext
import com.okta.idx.kotlin.dto.v1.InteractResponse
import com.okta.idx.kotlin.dto.v1.Token
import com.okta.idx.kotlin.dto.v1.asFormRequest
import com.okta.idx.kotlin.dto.v1.asJsonRequest
import com.okta.idx.kotlin.dto.v1.introspectRequest
import com.okta.idx.kotlin.dto.v1.toIdxResponse
import com.okta.idx.kotlin.dto.v1.tokenRequestFromInteractionCode
import kotlinx.coroutines.withContext
import com.okta.idx.kotlin.dto.v1.Response as V1Response

/**
 * The IdxClient class is used to define and initiate an authentication workflow utilizing the Okta Identity Engine.
 */
class IdxClient internal constructor(
    private val configuration: IdxClientConfiguration,
    val clientContext: IdxClientContext,
) {
    companion object {
        /**
         * Used to create an IdxClient, and to start an authorization flow.
         */
        suspend fun start(configuration: IdxClientConfiguration): IdxClientResult<IdxClient> {
            val interactContext = withContext(configuration.computationDispatcher) {
                InteractContext.create(configuration)
            }

            return configuration.performRequest<InteractResponse, IdxClient>(interactContext.request) {
                val clientContext = IdxClientContext(
                    codeVerifier = interactContext.codeVerifier,
                    interactionHandle = it.interactionHandle,
                    state = interactContext.state,
                )
                IdxClient(
                    configuration = configuration,
                    clientContext = clientContext,
                )
            }
        }
    }

    /**
     * Resumes the authentication state to identify the available remediation steps.
     *
     * This method is usually performed after an IdxClient is created, but can also be called at any time to identify what next remediation steps are available to the user.
     */
    suspend fun resume(): IdxClientResult<IdxResponse> {
        val request = withContext(configuration.computationDispatcher) {
            introspectRequest(configuration, clientContext)
        }

        return configuration.performRequest<V1Response, IdxResponse>(request) {
            it.toIdxResponse(configuration.json)
        }
    }

    /**
     * Executes the remediation option and proceeds through the workflow using the supplied form parameters.
     *
     * This method is used to proceed through the authentication flow, using the data assigned to the nested fields' `value` to make selections.
     */
    suspend fun proceed(remediation: IdxRemediation): IdxClientResult<IdxResponse> {
        val request = withContext(configuration.computationDispatcher) {
            remediation.asJsonRequest(configuration)
        }

        return configuration.performRequest<V1Response, IdxResponse>(request) {
            it.toIdxResponse(configuration.json).also { response ->
                response.remediations.firstOrNull()?.copyValuesFromPrevious(remediation)
            }
        }
    }

    /**
     * Exchange the IdxRemediation.Type.ISSUE remediation type for tokens.
     */
    suspend fun exchangeCodes(remediation: IdxRemediation): IdxClientResult<TokenResponse> {
        if (remediation.type != IdxRemediation.Type.ISSUE) {
            return IdxClientResult.Error(IllegalStateException("Invalid remediation."))
        }

        val request = withContext(configuration.computationDispatcher) {
            remediation["code_verifier"]?.value = clientContext.codeVerifier

            remediation.asFormRequest()
        }

        return configuration.performRequest(request, Token::toIdxResponse)
    }

    /**
     * Evaluates the given redirect url to determine what next steps can be performed. This is usually used when receiving a redirection from an IDP authentication flow.
     */
    suspend fun redirectResult(uri: Uri): IdxRedirectResult {
        if (!uri.toString().startsWith(configuration.redirectUri)) {
            val error = "IDP redirect failed due not matching the configured redirect uri."
            return IdxRedirectResult.Error(error)
        }
        val errorQueryParameter = uri.getQueryParameter("error")
        val stateQueryParameter = uri.getQueryParameter("state")
        if (errorQueryParameter == "interaction_required") {
            // Validate the state matches. This is a security assurance.
            if (clientContext.state != stateQueryParameter) {
                val error = "IDP redirect failed due to state mismatch."
                return IdxRedirectResult.Error(error)
            }
            return when (val resumeResult = resume()) {
                is IdxClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to resume.", resumeResult.exception)
                }
                is IdxClientResult.Success -> {
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
            if (clientContext.state != stateQueryParameter) {
                val error = "IDP redirect failed due to state mismatch."
                return IdxRedirectResult.Error(error)
            }
            return when (val result = exchangeCodes(interactionCodeQueryParameter)) {
                is IdxClientResult.Error -> {
                    IdxRedirectResult.Error("Failed to exchangeCodes.", result.exception)
                }
                is IdxClientResult.Success -> {
                    IdxRedirectResult.Tokens(result.result)
                }
            }
        }
        return IdxRedirectResult.Error("Unable to handle redirect url.")
    }

    private suspend fun exchangeCodes(interactionCode: String): IdxClientResult<TokenResponse> {
        val request = withContext(configuration.computationDispatcher) {
            tokenRequestFromInteractionCode(configuration, clientContext, interactionCode)
        }

        return configuration.performRequest(request, Token::toIdxResponse)
    }
}
