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
package com.okta.authfoundation.client.kmp

import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.credential.TestConfiguration
import com.okta.authfoundation.jwt.JwtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultIdTokenValidatorTest {
    // Valid JWT from test fixtures — sub=00ub41z7mgzNqryMv696, iss=https://example-test.okta.com/oauth2/default
    @Suppress("ktlint:standard:max-line-length")
    private val validJwt =
        "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILYpPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8gFbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9NvRdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"

    private val parser = JwtParser(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }, Dispatchers.Default)
    private val validator = DefaultIdTokenValidator()

    // iat=1644347069, exp=1644350669
    private val clockAtTokenTime = OidcClock { 1644347100L }

    @Test
    fun validate_ValidToken_Passes() =
        runTest {
            val jwt = parser.parse(validJwt)
            validator.validate(
                issuerUrl = "https://example-test.okta.com/oauth2/default",
                clientId = "unit_test_client_id",
                idToken = jwt,
                clock = clockAtTokenTime
            )
        }

    @Test
    fun validate_InvalidIssuer_Throws() =
        runTest {
            val jwt = parser.parse(validJwt)
            val error =
                assertFailsWith<IdTokenValidator.Error> {
                    validator.validate(
                        issuerUrl = "https://wrong-issuer.okta.com",
                        clientId = "unit_test_client_id",
                        idToken = jwt,
                        clock = clockAtTokenTime
                    )
                }
            assertEquals(IdTokenValidator.Error.INVALID_ISSUER, error.identifier)
        }

    @Test
    fun validate_InvalidAudience_Throws() =
        runTest {
            val jwt = parser.parse(validJwt)
            val error =
                assertFailsWith<IdTokenValidator.Error> {
                    validator.validate(
                        issuerUrl = "https://example-test.okta.com/oauth2/default",
                        clientId = "wrong_client_id",
                        idToken = jwt,
                        clock = clockAtTokenTime
                    )
                }
            assertEquals(IdTokenValidator.Error.INVALID_AUDIENCE, error.identifier)
        }

    @Test
    fun validate_ExpiredToken_Throws() =
        runTest {
            val jwt = parser.parse(validJwt)
            val futureClockPastExp = OidcClock { 1644350670L } // 1s past exp
            val error =
                assertFailsWith<IdTokenValidator.Error> {
                    validator.validate(
                        issuerUrl = "https://example-test.okta.com/oauth2/default",
                        clientId = "unit_test_client_id",
                        idToken = jwt,
                        clock = futureClockPastExp
                    )
                }
            assertEquals(IdTokenValidator.Error.EXPIRED, error.identifier)
        }

    @Test
    fun validate_IatOutsideGracePeriod_Throws() =
        runTest {
            val jwt = parser.parse(validJwt)
            // iat=1644347069, set clock far from iat (>600s)
            val farClock = OidcClock { 1644347069L + 700 }
            val error =
                assertFailsWith<IdTokenValidator.Error> {
                    validator.validate(
                        issuerUrl = "https://example-test.okta.com/oauth2/default",
                        clientId = "unit_test_client_id",
                        idToken = jwt,
                        clock = farClock
                    )
                }
            assertEquals(IdTokenValidator.Error.ISSUED_AT_THRESHOLD_NOT_SATISFIED, error.identifier)
        }

    @Test
    fun validate_CustomGracePeriod_AllowsIatBeyondDefault() =
        runTest {
            // iat=1644347069, clock is 700s away — outside the default 600s window.
            // A validator configured with a wider grace period must accept this.
            val farClock = OidcClock { 1644347069L + 700 }
            val wideValidator = DefaultIdTokenValidator(issuedAtGracePeriodInSeconds = 9999)
            val jwt = parser.parse(validJwt)
            wideValidator.validate(
                issuerUrl = "https://example-test.okta.com/oauth2/default",
                clientId = "unit_test_client_id",
                idToken = jwt,
                clock = farClock
            )
        }

    @Test
    fun validate_IssuerWithTrailingSlash_Matches() =
        runTest {
            val jwt = parser.parse(validJwt)
            validator.validate(
                issuerUrl = "https://example-test.okta.com/oauth2/default/",
                clientId = "unit_test_client_id",
                idToken = jwt,
                clock = clockAtTokenTime
            )
        }
}
