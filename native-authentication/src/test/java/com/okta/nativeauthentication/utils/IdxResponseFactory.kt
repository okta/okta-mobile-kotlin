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
package com.okta.nativeauthentication.utils

import com.okta.authfoundation.client.OidcClientResult
import com.okta.idx.kotlin.client.InteractionCodeFlow
import com.okta.idx.kotlin.client.InteractionCodeFlow.Companion.createInteractionCodeFlow
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.testing.network.NetworkRule
import com.okta.testing.network.RequestMatchers.path
import com.okta.testing.testBodyFromFile
import kotlinx.coroutines.runBlocking

internal class IdxResponseFactory(private val networkRule: NetworkRule) {
    fun fromJson(json: String): IdxResponse = runBlocking {
        networkRule.enqueue(path("/oauth2/default/v1/interact")) { response ->
            response.testBodyFromFile("SuccessInteractResponse.json")
        }
        networkRule.enqueue(path("/idp/idx/introspect")) { response ->
            response.setBody(json)
        }
        val oidcClient = networkRule.createOidcClient()
        val interactionCodeFlowResult = oidcClient.createInteractionCodeFlow("test.okta.com/login")
        val interactionCodeFlow = (interactionCodeFlowResult as OidcClientResult.Success<InteractionCodeFlow>).result
        (interactionCodeFlow.resume() as OidcClientResult.Success<IdxResponse>).result
    }
}
