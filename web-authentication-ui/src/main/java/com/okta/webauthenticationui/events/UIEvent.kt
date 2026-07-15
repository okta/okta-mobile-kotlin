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
package com.okta.webauthenticationui.events

import com.okta.authfoundation.events.Event

/**
 * Marker interface for events related to the browser authentication UI, such as browser
 * customization and activity lifecycle events. Model-layer handlers that do not need to
 * respond to UI events can exclude this type:
 *
 * ```kotlin
 * EventCoordinator(object : EventHandler {
 *     override fun onEvent(event: Event) {
 *         if (event is UIEvent) return  // skip UI events in model layer
 *         // handle other events
 *     }
 * })
 * ```
 */
interface UIEvent : Event
