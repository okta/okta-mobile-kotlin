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
package sample.okta.android.test

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

object EndToEndCredentials {
    private val jsonNode: JsonNode

    init {
        val inputStream = EndToEndCredentials::class.java.classLoader!!.getResourceAsStream("e2eCredentials.yaml")
        val mapper = ObjectMapper(YAMLFactory())
        jsonNode = mapper.readTree(inputStream)
    }

    operator fun get(key: String): String = jsonNode.at(key).asText()
}
