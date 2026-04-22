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
package com.okta.oauth2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceGeneratorTest {
    @Test
    fun codeVerifier_MeetsRfc7636LengthRequirement() {
        val verifier = PkceGenerator.codeVerifier()
        // RFC 7636: code_verifier length must be between 43 and 128 characters
        assertTrue(verifier.length in 43..128, "Verifier length ${verifier.length} not in [43, 128]")
    }

    @Test
    fun codeVerifier_ContainsOnlyValidCharacters() {
        val verifier = PkceGenerator.codeVerifier()
        // RFC 7636: code_verifier uses unreserved characters [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
        // Base64url without padding uses [A-Za-z0-9_-]
        val validChars = Regex("^[A-Za-z0-9_-]+$")
        assertTrue(validChars.matches(verifier), "Verifier contains invalid characters: $verifier")
    }

    @Test
    fun codeVerifier_GeneratesUniqueValues() {
        val first = PkceGenerator.codeVerifier()
        val second = PkceGenerator.codeVerifier()
        assertNotEquals(first, second)
    }

    @Test
    fun codeChallenge_ProducesValidBase64Url() {
        val verifier = PkceGenerator.codeVerifier()
        val challenge = PkceGenerator.codeChallenge(verifier)
        val validChars = Regex("^[A-Za-z0-9_-]+$")
        assertTrue(validChars.matches(challenge), "Challenge contains invalid characters: $challenge")
    }

    @Test
    fun codeChallenge_ConsistentForSameVerifier() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val first = PkceGenerator.codeChallenge(verifier)
        val second = PkceGenerator.codeChallenge(verifier)
        assertEquals(first, second)
    }

    @Test
    fun codeChallenge_DifferentVerifiers_ProduceDifferentChallenges() {
        val verifier1 = PkceGenerator.codeVerifier()
        val verifier2 = PkceGenerator.codeVerifier()
        val challenge1 = PkceGenerator.codeChallenge(verifier1)
        val challenge2 = PkceGenerator.codeChallenge(verifier2)
        assertNotEquals(challenge1, challenge2)
    }

    @Test
    fun codeChallengeMethod_IsS256() {
        assertEquals("S256", PkceGenerator.CODE_CHALLENGE_METHOD)
    }

    @Test
    fun codeChallenge_KnownVector() {
        // RFC 7636 Appendix B test vector
        // code_verifier = dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
        // code_challenge = E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = PkceGenerator.codeChallenge(verifier)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }
}
