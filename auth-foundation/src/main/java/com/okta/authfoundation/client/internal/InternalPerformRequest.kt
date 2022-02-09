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
package com.okta.authfoundation.client.internal

import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.events.TokenCreatedEvent
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.jwt.JwtParser
import okhttp3.Request

suspend fun OidcClient.internalTokenRequest(
    request: Request,
): OidcClientResult<Token> {
    val result = configuration.performRequest<Token>(request)
    if (result is OidcClientResult.Success) {
        val token = result.result

        validateIdToken(token)?.let { return it }

        val event = TokenCreatedEvent(token)
        configuration.eventCoordinator.sendEvent(event)
        event.runFollowUpTasks()

        credential?.storeToken(token)
    }
    return result
}

private suspend fun OidcClient.validateIdToken(token: Token): OidcClientResult<Token>? {
    try {
        if (token.idToken != null) {
            val parser = JwtParser(configuration.json, configuration.computeDispatcher)
            val jwt = parser.parse(token.idToken)
            configuration.idTokenValidator.validate(oidcClient = this, idToken = jwt)
        }
    } catch (e: Exception) {
        return OidcClientResult.Error(e)
    }
    return null
}
