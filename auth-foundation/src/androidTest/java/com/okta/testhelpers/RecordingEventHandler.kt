package com.okta.testhelpers

import com.okta.authfoundation.events.EventHandler
import java.util.Collections

class RecordingEventHandler(
    private val list: MutableList<Any> = Collections.synchronizedList(mutableListOf<Any>()),
    private val nestedEventHandlers: MutableList<EventHandler> = Collections.synchronizedList(mutableListOf()),
) : EventHandler, List<Any> by list {
    override fun onEvent(event: Any) {
        for (eventHandler in nestedEventHandlers) {
            eventHandler.onEvent(event)
        }
        list += event
    }

    fun registerEventHandler(eventHandler: EventHandler) {
        nestedEventHandlers += eventHandler
    }
}
