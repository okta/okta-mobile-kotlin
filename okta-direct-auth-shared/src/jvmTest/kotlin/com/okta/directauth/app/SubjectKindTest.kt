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
package com.okta.directauth.app

import com.okta.directauth.app.model.SubjectKind
import com.okta.oauth2.kmp.SubjectAssertion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectKindTest {
    @Test
    fun availableIn_TokenWithIdAndRefreshTokens_AllThreeKindsAvailable() {
        val token = FakeTokenInfo(idToken = "id-token-value", refreshToken = "refresh-token-value")

        assertTrue(SubjectKind.IDENTITY.availableIn(token))
        assertTrue(SubjectKind.ACCESS.availableIn(token))
        assertTrue(SubjectKind.REFRESH.availableIn(token))
    }

    @Test
    fun availableIn_TokenWithNoIdToken_IdentityUnavailable() {
        val token = FakeTokenInfo(idToken = null)

        assertFalse(SubjectKind.IDENTITY.availableIn(token))
    }

    @Test
    fun availableIn_TokenWithBlankIdToken_IdentityUnavailable() {
        val token = FakeTokenInfo(idToken = "   ")

        assertFalse(SubjectKind.IDENTITY.availableIn(token))
    }

    @Test
    fun availableIn_TokenWithNoRefreshToken_RefreshUnavailable() {
        val token = FakeTokenInfo(refreshToken = null)

        assertFalse(SubjectKind.REFRESH.availableIn(token))
    }

    @Test
    fun availableIn_AccessAlwaysAvailable_RegardlessOfOtherTokens() {
        val token = FakeTokenInfo(idToken = null, refreshToken = null)

        assertTrue(SubjectKind.ACCESS.availableIn(token))
    }

    @Test
    fun toSubjectAssertion_EachKind_PairsValueWithMatchingType() {
        val identity = SubjectKind.IDENTITY.toSubjectAssertion("id-value")
        val access = SubjectKind.ACCESS.toSubjectAssertion("access-value")
        val refresh = SubjectKind.REFRESH.toSubjectAssertion("refresh-value")

        assertEquals(SubjectAssertion.Type.ID_TOKEN, identity.type)
        assertEquals(SubjectAssertion.Type.ACCESS_TOKEN, access.type)
        assertEquals(SubjectAssertion.Type.REFRESH_TOKEN, refresh.type)
        assertEquals("id-value", identity.value)
        assertEquals("access-value", access.value)
        assertEquals("refresh-value", refresh.value)
    }
}
