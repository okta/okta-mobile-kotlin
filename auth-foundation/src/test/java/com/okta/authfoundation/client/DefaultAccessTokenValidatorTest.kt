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
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.jwt.JwtParser
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class DefaultAccessTokenValidatorTest {
    private val accessTokenValidator: AccessTokenValidator = DefaultAccessTokenValidator()
    @get:Rule val oktaRule = OktaRule(accessTokenValidator = accessTokenValidator)

    private fun createToken(
        accessToken: String,
        idToken: String?,
    ): Token {
        return Token(
            tokenType = "Bearer",
            expiresIn = 3600,
            accessToken = accessToken,
            scope = "openid email profile offline_access",
            idToken = idToken,
        )
    }

    private suspend fun validateToken(token: Token) {
        val parser = JwtParser(oktaRule.configuration.json, oktaRule.configuration.computeDispatcher)
        val idToken = parser.parse(token.idToken!!)

        oktaRule.configuration.accessTokenValidator.validate(
            oidcClient = oktaRule.createOidcClient(),
            accessToken = token.accessToken,
            idToken = idToken,
        )
    }

    @Test fun `validate a valid token`(): Unit = runBlocking {
        val accessToken = "eyJraWQiOiJLM1NzTXRuOTFzVTkwY2E4UGc2dURWaVBoaWZVVXVodWtObFZtMWNPNHhBIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULnZ5WE1zSXZ3TFFnTVFiZHM2V0xSTkF6N25MSUJQSkczMWs0NEEwVjQ3X1Eub2FyMXduMGxqek8xclJNVFg2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2NDYzMjQ1NjIsImV4cCI6MTY0NjMyNDg2MiwiY2lkIjoiMG9hOGZ1cDBsQVBZRkM0STI2OTYiLCJ1aWQiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsInNjcCI6WyJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiLCJvcGVuaWQiLCJkZXZpY2Vfc3NvIiwiZW1haWwiXSwic3ViIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20ifQ.LgRMbhvJj1BX_lYrZlewfAS6m0dib3RerX8N64pCnSRQ51bXhHVV_HwzQFUFn80cmLmRGU07j-bI3g-HmbfaobHGL0hTjdjBDgQ_iZ2vUNtiQOakqRPRnetvsrXIjI1gzgTRsg_Di1eUSjK8vYf-IpDqvqLDOZTqcujALNo7ChgsHO5jghau6CGHAjo1tNFGhh4gyvE4dL0LgI2y06Y1_pFNUGRWIqPaLJ5yqxzPkdyqiybPrvDYnxJfDTKg5NTJFJ7mfbw_GqBEDMN6SMhrWonui__xI4asg2aVw6QbD7Nsh8CkT4xSOSH8R8D4JcBoiEZY2Bb0yT7no4HaRIiB5w"
        val idToken = "eyJraWQiOiJLM1NzTXRuOTFzVTkwY2E4UGc2dURWaVBoaWZVVXVodWtObFZtMWNPNHhBIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDYzMjQ1NjIsImV4cCI6MTY0NjMyODE2MiwianRpIjoiSUQuR2xfa3hNUFR1WC05UDFKSk5ONGc1TkUyZ3hKLWtOSnBGdXFKZ0pfQWJmTSIsImFtciI6WyJtZmEiLCJtY2EiLCJzbXMiLCJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHg2VUlabEdNb1NMNnJ1RC1BaXZ4bmJBIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDYyNDM4OTUsImF0X2hhc2giOiJJUXNXUmkyQzhIeUhsWFVreEp0ZFBnIiwiZHNfaGFzaCI6Im9NTzB4bHZUY3RHSnNJOFVzOUhVREEifQ.BNvX_jhjeCM_F6WZyNNwppnM9JnDttXPPq-m1z3PZmciEIheKuhX9lgS3eS_pvrJdRZZF8QpaHC0REyUY-II7b82IccULW3U-jwh0Sv-vtPqkD8QvcJMveee7I_j7ERlCYrLFRAvmIFNiS1RW_V2M6uHMUTdixeJRhl8uI1K4OwT4Z4SwGrczaCvGmdCjaefb3n1kvwTtrVq0HJkbO29RbEynOhl5jBq4XPCFw6V3qCqHON_IrZaRZBNKr_lSJKCie4U_vB50EmPIrlS8AFg9pvKxmOmpfQpzyhzjcg6cXtltMQkMJaHq8Ssxpm_7PtX0lds6oz46XQ8Ugwl8LKcrg"
        val token = createToken(accessToken, idToken)
        validateToken(token)
    }

    @Test fun `validate an invalid token`(): Unit = runBlocking {
        val accessToken = "eyJraWQiOiJLM1NzTXRuOTFzVTkwY2E4UGc2dURWaVBoaWZVVXVodWtObFZtMWNPNHhBIiwiYWxnIjoiUlMyNTYifQ.eyJ2ZXIiOjEsImp0aSI6IkFULnZ5WE1zSXZ3TFFnTVFiZHM2V0xSTkF6N25MSUJQSkczMWs0NEEwVjQ3X1Eub2FyMXduMGxqek8xclJNVFg2OTYiLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6ImFwaTovL2RlZmF1bHQiLCJpYXQiOjE2NDYzMjQ1NjIsImV4cCI6MTY0NjMyNDg2MiwiY2lkIjoiMG9hOGZ1cDBsQVBZRkM0STI2OTYiLCJ1aWQiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsInNjcCI6WyJwcm9maWxlIiwib2ZmbGluZV9hY2Nlc3MiLCJvcGVuaWQiLCJkZXZpY2Vfc3NvIiwiZW1haWwiXSwic3ViIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20ifQ.LgRMbhvJj1BX_lYrZlewfAS6m0dib3RerX8N64pCnSRQ51bXhHVV_HwzQFUFn80cmLmRGU07j-bI3g-HmbfaobHGL0hTjdjBDgQ_iZ2vUNtiQOakqRPRnetvsrXIjI1gzgTRsg_Di1eUSjK8vYf-IpDqvqLDOZTqcujALNo7ChgsHO5jghau6CGHAjo1tNFGhh4gyvE4dL0LgI2y06Y1_pFNUGRWIqPaLJ5yqxzPkdyqiybPrvDYnxJfDTKg5NTJFJ7mfbw_GqBEDMN6SMhrWonui__xI4asg2aVw6QbD7Nsh8CkT4xSOSH8R8D4JcBoiEZY2Bb0yT7no4HaRIiB5w"
        val idToken = "eyJraWQiOiJLM1NzTXRuOTFzVTkwY2E4UGc2dURWaVBoaWZVVXVodWtObFZtMWNPNHhBIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIwMHViNDF6N21nek5xcnlNdjY5NiIsIm5hbWUiOiJKYXkgTmV3c3Ryb20iLCJlbWFpbCI6ImpheW5ld3N0cm9tQGV4YW1wbGUuY29tIiwidmVyIjoxLCJpc3MiOiJodHRwczovL2V4YW1wbGUtdGVzdC5va3RhLmNvbS9vYXV0aDIvZGVmYXVsdCIsImF1ZCI6InVuaXRfdGVzdF9jbGllbnRfaWQiLCJpYXQiOjE2NDYzMjQ1NjIsImV4cCI6MTY0NjMyODE2MiwianRpIjoiSUQuR2xfa3hNUFR1WC05UDFKSk5ONGc1TkUyZ3hKLWtOSnBGdXFKZ0pfQWJmTSIsImFtciI6WyJtZmEiLCJtY2EiLCJzbXMiLCJwd2QiXSwiaWRwIjoiMDBvOGZvdTdzUmFHR3dkbjQ2OTYiLCJzaWQiOiJpZHg2VUlabEdNb1NMNnJ1RC1BaXZ4bmJBIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiamF5bmV3c3Ryb21AZXhhbXBsZS5jb20iLCJhdXRoX3RpbWUiOjE2NDYyNDM4OTUsImF0X2hhc2giOiJpbnZhbGlkaGFzaCIsImRzX2hhc2giOiJvTU8weGx2VGN0R0pzSThVczlIVURBIn0.0FFT8wG2emdS78is3FexpRkNukjGYjufVOFYY64TyFzNwmlgkZCxXfgngByTdMK5cfTgFP3INunf7xPiXi1VL47BQbRweyedTKN2MrUkxhmpo5v3ep9Qr4IzzcLMjnO5d763jK8gqausewb-FajMrrcJPMDiTAv6hfLQoiLbr4Swlf3LeHDYxyFhR7-mHyhqDcLseUwWTFml2QWYStSUl2Kn3khPgpfVpfEo001cBlYpeGRvixDOkLt0kixHvaTRsmQoPnV3Qy8EUCZWyjGVrDlH5SLpswpu5Dg1u1LGbn0_24p_N0XV6lJD7Wl6w-6EXFFSimvxly5A-1n7VWLdJQ"
        val token = createToken(accessToken, idToken)
        val exception = assertFailsWith<AccessTokenValidator.Error> {
            validateToken(token)
        }
        assertThat(exception).hasMessageThat().isEqualTo("ID Token at_hash didn't match the access token.")
    }
}
