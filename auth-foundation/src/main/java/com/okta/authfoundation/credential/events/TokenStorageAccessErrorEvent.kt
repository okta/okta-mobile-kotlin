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
package com.okta.authfoundation.credential.events

import com.okta.authfoundation.credential.TokenStorage
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler

/**
 * Emitted via [EventHandler.onEvent] when a [TokenStorage] call causes an exception.
 *
 * The default implementation automatically clears storage so it can try again, see [shouldClearStorageAndTryAgain].
 */
class TokenStorageAccessErrorEvent internal constructor(
    /**
     * The [Exception] that caused the event.
     */
    val exception: Exception,
    /**
     * Allows the app developer to change the behavior of attempted remediation.
     * If true, the storage implementation will attempt to clear all existing items in storage.
     */
    var shouldClearStorageAndTryAgain: Boolean,
) : Event
