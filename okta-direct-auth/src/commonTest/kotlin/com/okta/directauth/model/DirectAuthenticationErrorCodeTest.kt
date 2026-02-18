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
package com.okta.directauth.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DirectAuthenticationErrorCodeTest {
    @Test
    fun `fromString returns correct enum for all valid codes`() {
        DirectAuthenticationErrorCode.entries.forEach { expectedEnum ->
            val actualEnum = DirectAuthenticationErrorCode.fromString(expectedEnum.code)
            assertEquals(expectedEnum, actualEnum, "fromString should return the correct enum for code '${expectedEnum.code}'")
        }
    }

    @Test
    fun `fromString throws IllegalArgumentException for unknown code`() {
        val unknownCode = "this_is_not_a_valid_code"
        try {
            DirectAuthenticationErrorCode.fromString(unknownCode)
            fail("Expected IllegalArgumentException was not thrown for code '$unknownCode'")
        } catch (e: IllegalArgumentException) {
            // This is the expected outcome.
            assertEquals("Unknown DirectAuthenticationErrorCode: $unknownCode", e.message)
        }
    }

    @Test
    fun `enum codes have correct string values`() {
        assertEquals("mfa_required", DirectAuthenticationErrorCode.MFA_REQUIRED.code)
        assertEquals("authorization_pending", DirectAuthenticationErrorCode.AUTHORIZATION_PENDING.code)
        assertEquals("access_denied", DirectAuthenticationErrorCode.ACCESS_DENIED.code)
        assertEquals("invalid_grant", DirectAuthenticationErrorCode.INVALID_GRANT.code)
        assertEquals("invalid_request", DirectAuthenticationErrorCode.INVALID_REQUEST.code)
        assertEquals("invalid_scope", DirectAuthenticationErrorCode.INVALID_SCOPE.code)
        assertEquals("invalid_client", DirectAuthenticationErrorCode.INVALID_CLIENT.code)
        assertEquals("unauthorized_client", DirectAuthenticationErrorCode.UNAUTHORIZED_CLIENT.code)
        assertEquals("unsupported_grant_type", DirectAuthenticationErrorCode.UNSUPPORTED_GRANT_TYPE.code)
        assertEquals("unsupported_response_type", DirectAuthenticationErrorCode.UNSUPPORTED_RESPONSE_TYPE.code)
        assertEquals("temporarily_unavailable", DirectAuthenticationErrorCode.TEMPORARILY_UNAVAILABLE.code)
        assertEquals("server_error", DirectAuthenticationErrorCode.SERVER_ERROR.code)
    }
}
