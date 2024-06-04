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
package com.okta.authfoundation.events

import com.okta.authfoundation.InternalAuthFoundationApi

/**
 * A centralized coordinator of events emitted throughout Auth Foundation and other Okta SDKs.
 */
class EventCoordinator(
    /**
     * A list of [EventHandler]s that should be invoked when an event is emitted.
     *
     * > Note: this list cannot be modified once it's passed in, we make a defensive copy to ensure our list isn't changed.
     */
    eventHandlers: List<EventHandler>
) {
    private val eventHandlers = ArrayList(eventHandlers) // Make a defensive copy.

    /**
     * A convenience constructor for passing in a single [EventHandler] to invoke when events are emitted.
     */
    constructor(
        /**
         * The [EventHandler] that should be invoked when an event is emitted.
         */
        eventHandler: EventHandler
    ) : this(listOf(eventHandler))

    @InternalAuthFoundationApi
    fun sendEvent(event: Event) {
        for (eventHandler in eventHandlers) {
            eventHandler.onEvent(event)
        }
    }
}
