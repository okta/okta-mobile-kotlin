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

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

typealias RequestMatcher = (request: OktaRecordedRequest) -> Boolean

object RequestMatchers {
    fun path(path: String): RequestMatcher {
        return { request ->
            var requestPath = request.path
            val queryIndex = requestPath?.indexOf("?") ?: -1
            if (queryIndex > -1) {
                // Remove the query params.
                requestPath = requestPath?.substring(0, queryIndex)
            }
            requestPath?.endsWith(path) ?: false
        }
    }

    fun query(query: String): RequestMatcher {
        return { request ->
            val requestPath = request.path
            val queryIndex = requestPath?.indexOf("?") ?: -1
            if (queryIndex > -1) {
                requestPath?.substring(queryIndex + 1) == query
            } else {
                false
            }
        }
    }

    fun method(method: String): RequestMatcher {
        return { request -> request.method == method }
    }

    fun body(body: String): RequestMatcher {
        return { request ->
            val actual = request.bodyText
            actual == body
        }
    }

    private val objectMapper: ObjectMapper = ObjectMapper()

    fun bodyWithJsonPath(path: String, matcher: (JsonNode) -> Boolean): RequestMatcher {
        return { request ->
            try {
                val rootNode = objectMapper.readTree(request.bodyText)
                matcher(rootNode.at(path))
            } catch (e: JsonProcessingException) {
                false
            } catch (e: JsonMappingException) {
                false
            }
        }
    }

    fun composite(vararg matchers: RequestMatcher): RequestMatcher {
        return { request ->
            matchers.all { it(request) }
        }
    }
}
