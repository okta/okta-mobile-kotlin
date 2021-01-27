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
import com.okta.idx.android.network.mock.RequestMatchers.path
import com.okta.idx.android.network.mock.mockBodyFromFile

internal object EnrollMockScenario {
    fun prepare() {
        OktaMockWebServer.dispatcher.clear()

        OktaMockWebServer.dispatcher.enqueue(path("/v1/interact")) { response ->
            response.setBody("""{"interaction_handle": "02X-_R0Y"}""")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/introspect")) { response ->
            response.mockBodyFromFile("enroll/introspect-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/credential/enroll")) { response ->
            response.mockBodyFromFile("enroll/enroll-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/challenge/answer")) { response ->
            response.mockBodyFromFile("enroll/answer-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/oauth2/v1/token")) { response ->
            response.mockBodyFromFile("enroll/token-response.json")
        }
        OktaMockWebServer.dispatcher.enqueue(path("/idp/idx/cancel")) { response ->
            response.mockBodyFromFile("enroll/cancel-response.json")
        }
    }
}
