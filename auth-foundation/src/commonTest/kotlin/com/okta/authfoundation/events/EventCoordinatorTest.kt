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
package com.okta.authfoundation.events

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.credential.RecordingEventHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalAuthFoundationApi::class)
class EventCoordinatorTest {
    private class TestEvent(
        val data: String,
    ) : Event

    @Test
    fun sendEvent_DeliversToSingleHandler() {
        val handler = RecordingEventHandler()
        val coordinator = EventCoordinator(handler)

        coordinator.sendEvent(TestEvent("test-id"))

        assertEquals(1, handler.events.size)
        assertIs<TestEvent>(handler.events[0])
    }

    @Test
    fun sendEvent_DeliversToMultipleHandlers() {
        val handler1 = RecordingEventHandler()
        val handler2 = RecordingEventHandler()
        val coordinator = EventCoordinator(listOf(handler1, handler2))

        coordinator.sendEvent(TestEvent("test-id"))

        assertEquals(1, handler1.events.size)
        assertEquals(1, handler2.events.size)
    }

    @Test
    fun sendEvent_NoHandlers_DoesNotThrow() {
        val coordinator = EventCoordinator(emptyList<EventHandler>())
        coordinator.sendEvent(TestEvent("test-id"))
    }

    @Test
    fun sendEvent_DefensiveCopy_OriginalListModificationIgnored() {
        val handlers = mutableListOf<EventHandler>()
        val handler = RecordingEventHandler()
        handlers.add(handler)
        val coordinator = EventCoordinator(handlers)

        handlers.clear()

        coordinator.sendEvent(TestEvent("test-id"))
        assertEquals(1, handler.events.size)
    }

    @Test
    fun sendEvent_PreservesEventData() {
        val handler = RecordingEventHandler()
        val coordinator = EventCoordinator(handler)

        coordinator.sendEvent(TestEvent("specific-id"))

        val event = assertIs<TestEvent>(handler.events[0])
        assertEquals("specific-id", event.data)
    }

    @Test
    fun sendEvent_MultipleEvents_DeliveredInOrder() {
        val handler = RecordingEventHandler()
        val coordinator = EventCoordinator(handler)

        coordinator.sendEvent(TestEvent("first"))
        coordinator.sendEvent(TestEvent("second"))
        coordinator.sendEvent(TestEvent("third"))

        assertEquals(3, handler.events.size)
        val ids = handler.events.map { (it as TestEvent).data }
        assertEquals(listOf("first", "second", "third"), ids)
    }
}
