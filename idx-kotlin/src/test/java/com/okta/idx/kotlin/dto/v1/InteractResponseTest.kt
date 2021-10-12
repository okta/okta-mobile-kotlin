/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.dto.v1

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class InteractResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun testDeserialization() {
        val interactJson = """
        {
          "interaction_handle": "NYwq2ADoLvH32XnnQXn-r0d4RhsFORIscEgo-26NyAM"
        }
        """.trimIndent()
        val token = json.decodeFromString<InteractResponse>(interactJson)
        assertThat(token.interactionHandle).isEqualTo("NYwq2ADoLvH32XnnQXn-r0d4RhsFORIscEgo-26NyAM")
    }
}
