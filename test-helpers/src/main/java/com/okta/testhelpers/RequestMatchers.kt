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
package com.okta.testhelpers

typealias RequestMatcher = (request: OktaRecordedRequest) -> Boolean

object RequestMatchers {
    fun header(
        key: String,
        value: String,
    ): RequestMatcher =
        { request ->
            request.headers[key] == value
        }

    fun doesNotContainHeaderWithValue(
        key: String,
        value: String,
    ): RequestMatcher {
        return matcher@{ request ->
            for (v in request.headers.values(key)) {
                if (v == value) {
                    return@matcher false // Fail the check, since it does contain the header.
                }
            }
            true // Pass the check, since we didn't find the header.
        }
    }

    fun not(requestMatcher: RequestMatcher): RequestMatcher =
        { request ->
            !requestMatcher.invoke(request)
        }

    fun doesNotContainHeader(key: String): RequestMatcher =
        { request ->
            !request.headers.names().contains(key)
        }

    fun path(path: String): RequestMatcher =
        { request ->
            var requestPath = request.path
            val queryIndex = requestPath.indexOf("?")
            if (queryIndex > -1) {
                // Remove the query params.
                requestPath = requestPath.substring(0, queryIndex)
            }
            requestPath.endsWith(path)
        }

    fun query(query: String): RequestMatcher =
        { request ->
            val requestPath = request.path
            val queryIndex = requestPath.indexOf("?")
            if (queryIndex > -1) {
                requestPath.substring(queryIndex + 1) == query
            } else {
                false
            }
        }

    fun query(
        name: String,
        value: String,
    ): RequestMatcher =
        { request ->
            request.path
                .substringAfter("?")
                .split("&")
                .associate { Pair(it.substringBefore("="), it.substringAfter("=")) }[name] == value
        }

    fun method(method: String): RequestMatcher = { request -> request.method == method }

    fun body(body: String): RequestMatcher =
        { request ->
            val actual = request.bodyText
            actual == body
        }

    fun bodyPart(
        name: String,
        value: String,
    ): RequestMatcher =
        { request ->
            request.bodyText
                .substringAfter("?")
                .split("&")
                .associate { Pair(it.substringBefore("="), it.substringAfter("=")) }[name] == value
        }

    fun composite(vararg matchers: RequestMatcher): RequestMatcher =
        { request ->
            matchers.all { it(request) }
        }
}
