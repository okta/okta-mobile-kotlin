package com.okta.directauth.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DirectAuthenticationErrorCodeTest {
    @Test
    fun `fromString returns correct enum for all valid codes`() {
        DirectAuthenticationErrorCode.entries.forEach { expectedEnum ->
            val actualEnum = DirectAuthenticationErrorCode.fromString(expectedEnum.code)
            assertEquals(
                "fromString should return the correct enum for code '${expectedEnum.code}'",
                expectedEnum,
                actualEnum
            )
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

