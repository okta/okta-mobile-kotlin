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
package com.okta.idx.android.dynamic

import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.network.mock.RequestMatchers.bodyWithJsonPath
import com.okta.idx.android.network.mock.RequestMatchers.path
import com.okta.idx.android.network.mock.mockBodyFromFile
import okhttp3.mockwebserver.SocketPolicy
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

internal object MfaEmailMockScenario {
    fun prepare() {
        // TODO: Handle poll link clicked scenario.
        val hasEnteredEmailCode = AtomicBoolean(false)
        OktaMockWebServer.dispatcher.clear()

        OktaMockWebServer.dispatcher.enqueue(path("/v1/interact")) { response ->
            response.setBody("""{"interaction_handle": "02X-_R0Y"}""")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/introspect")) { response ->
            response.mockBodyFromFile("mfa_email/introspect-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/identify")) { response ->
            response.mockBodyFromFile("mfa_email/identify-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(
            path("/idp/idx/identify"),
            bodyWithJsonPath("/identifier") { it.asText() == "force-error" }
        ) { response ->
            response.mockBodyFromFile("mfa_email/error-response.json")
            response.setResponseCode(403)
            response.addHeader("content-type: application/ion+json; okta-version=1.0.0")
        }
        OktaMockWebServer.dispatcher.enqueue(
            path("/idp/idx/identify"),
            bodyWithJsonPath("/identifier") { it.asText() == "force-network-error" }
        ) { response ->
            response.socketPolicy = SocketPolicy.DISCONNECT_AT_START
        }
        OktaMockWebServer.dispatcher.enqueue(
            path("/idp/idx/challenge"),
            bodyWithJsonPath("/authenticator/id") { it.asText() == "autzvyg0zO60FDyup2o4" }
        ) { response ->
            response.mockBodyFromFile("mfa_email/challenge-email-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(
            path("/idp/idx/challenge/answer"),
            bodyWithJsonPath("/credentials/passcode") {
                Timber.d("Password was ${hasEnteredEmailCode.get()}")
                it.asText()?.length ?: 0 > 0 && hasEnteredEmailCode.get()
            }
        ) { response ->
            response.mockBodyFromFile("mfa_email/answer-password-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(
                path("/idp/idx/challenge/answer"),
        bodyWithJsonPath("/credentials/passcode") {
            Timber.d("Passcode was ${hasEnteredEmailCode.get()}")
            it.asText()?.length ?: 0 > 0 && !hasEnteredEmailCode.getAndSet(true)
        }
        ) { response ->
            response.mockBodyFromFile("mfa_email/answer-passcode-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/oauth2/v1/token")) { response ->
            response.mockBodyFromFile("mfa_email/token-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/cancel")) { response ->
            response.mockBodyFromFile("mfa_email/cancel-response.json")
        }
    }
}
