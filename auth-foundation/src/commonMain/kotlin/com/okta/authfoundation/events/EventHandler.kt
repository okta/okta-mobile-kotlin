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
     * This method is called **synchronously** on the SDK's calling coroutine. The SDK may read
     * mutable event properties immediately after this method returns, so any customizations must
     * be applied before returning.
     *
     * > **Implementations must be fast and non-blocking.** Because this is not a `suspend` function,
     * > any blocking I/O (network, disk, locks) will block the calling coroutine's thread. Handlers
     * > are intended for lightweight configuration — setting a flag or changing a value.
     *
     * > Note: the specific thread or dispatcher this runs on is not guaranteed.
     *
     * @param event the event being emitted.
     */
    fun onEvent(event: Event)
}
