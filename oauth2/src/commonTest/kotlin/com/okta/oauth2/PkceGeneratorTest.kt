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

/**
 * [PkceGenerator] delegates to auth-foundation's shared `com.okta.authfoundation.crypto.PkceGenerator`
 * (see [PkceGeneratorTest][com.okta.authfoundation.crypto.PkceGeneratorTest] there for full coverage of
 * the algorithm itself) — this just confirms the delegation is wired correctly.
 */
class PkceGeneratorTest {
    @Test
    fun codeChallenge_KnownVector() {
        // RFC 7636 Appendix B test vector
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = PkceGenerator.codeChallenge(verifier)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun codeChallengeMethod_IsS256() {
        assertEquals("S256", PkceGenerator.CODE_CHALLENGE_METHOD)
    }
}
