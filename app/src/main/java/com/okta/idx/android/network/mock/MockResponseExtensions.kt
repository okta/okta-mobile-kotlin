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
package com.okta.idx.android.network.mock

import okhttp3.mockwebserver.MockResponse
import okio.Buffer

fun MockResponse.mockBodyFromFile(filename: String): MockResponse {
    val classLoader = MockResponse::class.java.classLoader!!
    val inputStream = classLoader.getResourceAsStream("okta_mock_responses/$filename")
    val buffer = Buffer()
    buffer.readFrom(inputStream)
    setBody(buffer)
    return this
}
