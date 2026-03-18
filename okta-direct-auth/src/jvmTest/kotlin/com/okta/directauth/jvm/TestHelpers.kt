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

import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.directauth.api.WebAuthnCeremonyHandler
import com.okta.directauth.model.WebAuthnAssertionResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode

/**
 * Kotlin test helpers providing objects that are difficult to construct from Java
 * due to Kotlin-specific language features (suspend functions, DSL builders, etc.).
 */
object TestHelpers {
    /** Creates a [KtorHttpExecutor] backed by a mock engine that always returns HTTP 200. */
    @JvmStatic
    fun createMockApiExecutor(): KtorHttpExecutor {
        val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
        return KtorHttpExecutor(HttpClient(mockEngine))
    }

    /**
     * Creates a [WebAuthnCeremonyHandler] that returns a successful assertion response.
     *
     * Java cannot implement [WebAuthnCeremonyHandler] directly because it contains a
     * `suspend` function, so this helper provides a pre-built instance.
     *  TODO replace with jvm implementation of PlatformWebAuthn.rememberWebAuthnCeremonyHandler
     */
    @JvmStatic
    fun createSuccessHandler(): WebAuthnCeremonyHandler =
        object : WebAuthnCeremonyHandler {
            override suspend fun performAssertion(challengeData: String): Result<WebAuthnAssertionResponse> = Result.success(WebAuthnAssertionResponse("clientData", "authData", "sig", "user"))
        }
}
