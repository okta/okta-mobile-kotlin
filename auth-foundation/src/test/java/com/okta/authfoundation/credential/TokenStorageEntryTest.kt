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
package com.okta.authfoundation.credential

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokenStorageEntryTest {
    @Test fun testEqualsExactInstance() {
        val entry = TokenStorage.Entry("example", createToken(), mapOf("foo" to "bar"))
        assertThat(entry).isEqualTo(entry)
    }

    @Test fun testEqualsNonEntry() {
        val entry = TokenStorage.Entry("example", createToken(), mapOf("foo" to "bar"))
        assertThat(entry).isNotEqualTo("example")
    }

    @Test fun testEqualsSame() {
        val entry1 = TokenStorage.Entry("example", createToken(), mapOf("foo" to "bar"))
        val entry2 = TokenStorage.Entry("example", createToken(), mapOf("foo" to "bar"))
        assertThat(entry1).isEqualTo(entry2)
    }

    @Test fun testEqualsNotSame() {
        val entry1 = TokenStorage.Entry("example", createToken(accessToken = "Foo"), mapOf("foo" to "bar"))
        val entry2 = TokenStorage.Entry("example", createToken(), mapOf("foo" to "bar"))
        assertThat(entry1).isNotEqualTo(entry2)
    }

    @Test fun testHashCode() {
        val entry = TokenStorage.Entry("example", createToken(), mapOf("foo" to "bar"))
        assertThat(entry.hashCode()).isEqualTo(329675619)
    }
}
