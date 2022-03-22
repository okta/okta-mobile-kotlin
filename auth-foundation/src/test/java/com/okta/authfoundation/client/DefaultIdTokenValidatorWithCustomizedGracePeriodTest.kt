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
package com.okta.authfoundation.client

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.jwt.Jwt
import com.okta.authfoundation.jwt.JwtParser
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class DefaultIdTokenValidatorWithCustomizedGracePeriodTest {
    private val idTokenValidator: IdTokenValidator = DefaultIdTokenValidator()
    @get:Rule val oktaRule = OktaRule(idTokenValidator = idTokenValidator)

    init {
        idTokenValidator.issuedAtGracePeriodInSeconds = 30 * 60
    }

    private suspend fun createJwt(idToken: String): Jwt {
        val parser = JwtParser(oktaRule.configuration.json, Dispatchers.Unconfined)
        return parser.parse(idToken)
    }

    @Test fun testValidIdToken(): Unit = runBlocking {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDcwNjksImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.tT8aKK4r8yFcW9KgVtZxvjXRJVzz-_rve14CVtpUlyvCTE1yj20wmPS0z3-JirI9xXgt5KeNPYqo3Wbv8c9XY_HY3hsPQdILYpPsUkf-sctmzSoKC_dTbs5xe8uKSgmpMrggfUAWrNPiJt9Ek2p7GgP64Wx79Pq5vSHk0yWlonFfXut5ahpSfqWilmYlvLr8gFbqoLnAJfl4ZbTY8pPw_aQgCdcQ-ImHRu-8bCSCtbFRzZB-SMJFLfRF2kmx0H-QF855wUODTuUSydkff-BKb-8wnbqWg0R9NvRdoXhEybv8TXXZY3cQqgolWLAyiPMrz07n0q_UEjAilUiCjn1f4Q"
        val client = oktaRule.createOidcClient(oktaRule.createEndpoints("https://example-test.okta.com".toHttpUrl().newBuilder()))
        idTokenValidator.validate(client, createJwt(idToken), null, null)
    }

    @Test fun testIssuedAtTooFarBeforeNow() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDUyNjgsImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.oFDnyGfoge0pSrcpOTxEV2TiC7UlQaOyYpz9Iy8vBEBq1bflbEMwsCEdf20g_V_Jt5wqNUV2LhPvpEKCBsVOCVcT0B_G_AKo9cM7vDerKlY-Aw_Ix-jYV6-V5iu6lOTm4OU8icOEyvs_6MYqAVxrGJXIDdCduu8SO5U8EECCT5sK3d3JM2OqjFbwhjTwY4PLDAy91P-hqjKg3K-Hg6LnGxjcLndtcqyOCBHbGbp_HKGoBmQIWZsuzF3jhs-bFuwHmQctn-6EUhG3gWkdBOGa7Wzf0f90gon8YXBmqn3yRsaVSNXtysS5D9JJO-Jv-w6MW8H0tj-_FXUNJp-i3DlIrQ"
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idToken)
    }

    @Test fun testIssuedAtTooFarAfterNow() {
        val idToken =
            "eyJraWQiOiJGSkEwSEdOdHN1dWRhX1BsNDVKNDJrdlFxY3N1XzBDNEZnN3BiSkxYVEhZIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDQzNDg4NzAsImV4cCI6MTY0NDM1MDY2OSwianRpIjoiSUQuNTVjeEJ0ZFlsOGw2YXJLSVNQQndkMHlPVC05VUNUYVhhUVRYdDJsYVJMcyIsImFtciI6WyJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHhXeGtscF80a1N4dUNfblUxcFhELW5BIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDQzNDcwNjgsImF0X2hhc2giOiJnTWNHVGJoR1QxR19sZHNIb0pzUHpRIiwiZHNfaGFzaCI6IkRBZUxPRlJxaWZ5c2Jnc3JiT2dib2cifQ.RKfiZZjHR4rTFQgNrfFNxnPICgfp1lEhifi2AWCDW8lyZSW0UFX6ekm9EQYGaYk0l4zW9lUzeBF_9xc4gjMXer3cv7KMtMBQb5d2qjU9LiRlBrLLzZBZetDXtTvrNXLhXkNz9ZSWqrxZDkETZakmE6xtCCWec-FRNRbzGVa6ohHu_u5nXlIm57Zx_rUd2FOP5dSZZc0LW5GHtupHcARn2bQlmEIGwwQRMX4ksv4mBGhmN_xRCTjg_HDQ7GBk5OY5s1mEO_tyHSqmbcmPPKiflLKSr2jTNluWQdDirdxYE97cO0gPVczjS65TEBUpQ38MgxGwPby8Ms0mV_4xb_ixAQ"
        assertFailsWithMessage("Issued at time is not within the allowed threshold of now.", idToken)
    }

    private fun assertFailsWithMessage(message: String, idToken: String, issuerPrefix: String = "https", nonce: String? = null) {
        try {
            val client =
                oktaRule.createOidcClient(oktaRule.createEndpoints("$issuerPrefix://example-test.okta.com".toHttpUrl().newBuilder()))
            runBlocking { idTokenValidator.validate(client, createJwt(idToken), nonce, null) }
            fail()
        } catch (e: IdTokenValidator.Error) {
            assertThat(e).hasMessageThat().isEqualTo(message)
        } catch (e: SerializationException) {
            assertThat(e).hasMessageThat().isEqualTo(message)
        }
    }
}
