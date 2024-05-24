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
import org.junit.Test
import kotlin.test.assertFailsWith

internal class OAuth2ClientResultTest {
    @Test fun testGetOrThrowReturnsEncapsulatedValue() {
        val result = OAuth2ClientResult.Success("Hi")
        assertThat(result.getOrThrow()).isEqualTo("Hi")
    }

    @Test fun testGetOrThrowThrowsEncapsulatedException() {
        val result = OAuth2ClientResult.Error<String>(IllegalStateException("Expected Error"))
        val exception = assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }
        assertThat(exception).hasMessageThat().isEqualTo("Expected Error")
    }
}
