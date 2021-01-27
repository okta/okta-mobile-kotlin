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

import com.okta.idx.android.network.mock.RequestMatchers.composite
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

class NetworkDispatcher : Dispatcher() {
    private val enqueuedResponses: Queue<Entry>

    /** When true, [consumeResponses] will remove responses after they're returned. */
    @Volatile var consumeResponses = false

    init {
        enqueuedResponses = ConcurrentLinkedQueue()
    }

    fun enqueue(vararg requestMatcher: RequestMatcher, responseFactory: (MockResponse) -> Unit) {
        val response = MockResponse()
        response.setResponseCode(200)
        responseFactory(response)
        enqueue(composite(*requestMatcher), response)
    }

    fun enqueue(requestMatcher: RequestMatcher, response: MockResponse) {
        enqueuedResponses.add(Entry(requestMatcher, response))
    }

    fun clear() {
        enqueuedResponses.clear()
    }

    fun numberRemainingInQueue(): Int {
        return enqueuedResponses.size
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        var matchedEntry: Entry? = null
        val oktaRequest = OktaRecordedRequest(request)
        enqueuedResponses.forEach { entry ->
            if (entry.requestMatcher(oktaRequest)) {
                matchedEntry = entry
                return@forEach
            }
        }

        matchedEntry?.let { capturedEntry ->
            if (consumeResponses) {
                enqueuedResponses.remove(capturedEntry)
            }
            return capturedEntry.response
        }

        throw RequestNotFoundException("$request not mocked\n${oktaRequest.bodyText}")
    }
}

private class Entry(val requestMatcher: RequestMatcher, val response: MockResponse)

internal class RequestNotFoundException(message: String) : Exception(message)
