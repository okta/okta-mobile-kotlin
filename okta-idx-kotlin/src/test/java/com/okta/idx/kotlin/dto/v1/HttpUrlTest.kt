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
import okhttp3.HttpUrl
import org.junit.Test

internal class HttpUrlTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test fun testDecode() {
        val httpUrl = json.decodeFromString(HttpUrlSerializer, "\"https://foo.oktapreview.com/idp/idx/identify\"")
        assertThat(httpUrl.host).isEqualTo("foo.oktapreview.com")
        assertThat(httpUrl.toString()).isEqualTo("https://foo.oktapreview.com/idp/idx/identify")
    }

    @Test fun testEncode() {
        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("foo.oktapreview.com")
            .addPathSegments("idp/idx/identify")
            .build()
        val json = json.encodeToString(HttpUrlSerializer, httpUrl)
        assertThat(json).isEqualTo("\"https://foo.oktapreview.com/idp/idx/identify\"")
    }
}
