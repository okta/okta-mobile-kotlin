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

/**
 * A centralized handler of events originated from Auth Foundation and other Okta SDKs.
 */
interface EventHandler {
    /**
     * A callback that is invoked when an event is emitted.
     *
     * > Note: events are emitted on the thread they're created on, and no threading guarantees are given.
     *
     * @param event the event being emitted.
     */
    fun onEvent(event: Event)
}
