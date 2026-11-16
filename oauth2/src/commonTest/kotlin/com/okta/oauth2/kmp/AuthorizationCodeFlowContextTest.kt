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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizationCodeFlowContextTest {
    @Test
    fun fromJson_RestoresEveryFieldIncludingInternalSecrets() {
        val original =
            AuthorizationCodeFlowContext(
                url = "https://example.com/authorize?state=s",
                redirectUrl = "app:/callback",
                codeVerifier = "verifier",
                state = "s",
                nonce = "n",
                maxAge = 300,
                usedPushedAuthorizationRequest = true,
                pushedAuthorizationRequestUri = "urn:par:1"
            )

        val restored = AuthorizationCodeFlowContext.fromJson(original.toJson()).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun fromJson_NullOptionalFields_RoundTrips() {
        val original = AuthorizationCodeFlowContext("u", "r", "v", "s", "n", null)

        assertEquals(original, AuthorizationCodeFlowContext.fromJson(original.toJson()).getOrThrow())
    }

    @Test
    fun fromJson_MalformedJson_ReturnsFailure() {
        assertTrue(AuthorizationCodeFlowContext.fromJson("{not json").isFailure)
    }

    @Test
    fun fromJson_UnknownFieldAndMissingOptionalFields_StillRestores() {
        val json = """{"url":"u","redirectUrl":"r","codeVerifier":"v","state":"s","nonce":"n","addedLater":1}"""

        assertEquals(
            AuthorizationCodeFlowContext("u", "r", "v", "s", "n", null),
            AuthorizationCodeFlowContext.fromJson(json).getOrThrow()
        )
    }
}
