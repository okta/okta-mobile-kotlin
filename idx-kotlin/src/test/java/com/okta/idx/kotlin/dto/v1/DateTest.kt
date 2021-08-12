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
import kotlinx.serialization.json.Json
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

internal class DateTest {
    val json = Json {
        ignoreUnknownKeys = true
    }

    @Test fun testDecode() {
        val date = json.decodeFromString(DateSerializer, "\"2021-05-21T16:41:22.000Z\"")
        assertThat(date).isNotNull()
    }

    @Test fun testEncode() {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val date = simpleDateFormat.parse("2021-05-21T16:41:22.000Z")!!
        val json = json.encodeToString(DateSerializer, date)
        assertThat(json).isEqualTo("\"2021-05-21T16:41:22.000Z\"")
    }
}
