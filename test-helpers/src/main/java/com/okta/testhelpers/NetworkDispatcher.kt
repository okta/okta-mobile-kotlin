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
package com.okta.testhelpers

import com.okta.testhelpers.RequestMatchers.composite
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class NetworkDispatcher : Dispatcher() {
    private val enqueuedResponses: Queue<Entry>

    init {
        enqueuedResponses = ConcurrentLinkedQueue()
    }

    fun enqueue(vararg requestMatcher: RequestMatcher, responseFactory: (MockResponse) -> Unit) {
        enqueuedResponses.add(
            Entry(composite(*requestMatcher)) {
                val response = MockResponse()
                response.setResponseCode(200)
                responseFactory(response)
                response
            }
        )
    }

    fun enqueue(vararg requestMatcher: RequestMatcher, responseFactory: (OktaRecordedRequest, MockResponse) -> Unit) {
        enqueuedResponses.add(
            Entry(composite(*requestMatcher)) {
                val response = MockResponse()
                response.setResponseCode(200)
                responseFactory(it, response)
                response
            }
        )
    }

    fun clear() {
        enqueuedResponses.clear()
    }

    fun numberRemainingInQueue(): Int {
        return enqueuedResponses.size
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val oktaRequest = OktaRecordedRequest(request)
        val matchedEntry = enqueuedResponses.first { entry ->
            entry.requestMatcher(oktaRequest)
        }

        matchedEntry?.let { capturedEntry ->
            enqueuedResponses.remove(capturedEntry)
            return capturedEntry.responseFactory(oktaRequest)
        }

        throw RequestNotFoundException("$request not mocked\n${oktaRequest.bodyText}")
    }
}

private class Entry(val requestMatcher: RequestMatcher, val responseFactory: (OktaRecordedRequest) -> MockResponse)

internal class RequestNotFoundException(message: String) : Exception(message)
