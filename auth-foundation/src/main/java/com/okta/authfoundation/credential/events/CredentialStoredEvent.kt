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

import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.events.Event
import com.okta.authfoundation.events.EventHandler

/**
 * Emitted via [EventHandler.onEvent] after a [Credential] is updated due to a [Credential.replaceToken] invocation.
 */
class CredentialStoredEvent internal constructor(
    /**
     * The [Credential] associated with the event.
     */
    val credential: Credential,

    /**
     * The [Token] associated with the event.
     */
    val token: Token?,

    /**
     * The tags associated with the event.
     */
    val tags: Map<String, String>,
) : Event
