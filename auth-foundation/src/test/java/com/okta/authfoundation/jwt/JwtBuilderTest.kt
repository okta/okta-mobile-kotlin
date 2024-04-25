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
package com.okta.authfoundation.jwt

import com.google.common.truth.Truth.assertThat
import com.okta.authfoundation.jwt.JwtBuilder.Companion.createJwtBuilder
import com.okta.testhelpers.OktaRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class JwtBuilderTest {
    @get:Rule val oktaRule = OktaRule()

    @Test fun testCreateJwt(): Unit = runBlocking {
        val idTokenClaims = IdTokenClaims()
        val idToken = oktaRule.createOAuth2Client().createJwtBuilder().createJwt(claims = idTokenClaims)
        assertThat(idToken.algorithm).isEqualTo("RS256")
        assertThat(idToken.keyId).isEqualTo(JwtBuilder.KEY_ID)
        assertThat(idToken.signature).isEqualTo("Py4hkTtY4dnBTzlZuS-oMLuPa-08SnBKHqQEB7PPLxtKak6RXRiYcEFSoqLJlflYrloWt5iHbbWVNsUpx9EoCt8hfnbinFjRq99A__Zky1pW6WF6l6hCxGK60tI_kOLFIBc3Wzu4w-Kj6z1B7RXl0R_W739cxkYHrlpqiCuJyZAwX5Qwf1SmZZDmrqWlUdHefaO6L2TlNSgJGLrsyb2vZ8JYdvIANCbkl5gCF5gx6ebd9QkXf2EZdyxbIlBI2weSLtlGwTtLZi3k4Q2pk8pz_zDqyfOtVhOb4OxAiu0YuPikvJ8iddAxM94-f_n8SUZTSlQ2n39UWPrQw0mcUsmWzA")
    }
}
