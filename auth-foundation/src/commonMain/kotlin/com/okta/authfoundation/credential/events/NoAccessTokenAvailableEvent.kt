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

import com.okta.authfoundation.credential.CredentialIdentifier
import com.okta.authfoundation.events.CredentialEvent
import com.okta.authfoundation.events.EventHandler

/**
 * Emitted via [EventHandler.onEvent] when no valid access token exists.
 *
 * **Migration note (Android):** The [credential] property was previously typed as
 * `Credential`. It is now [CredentialIdentifier] for cross-platform compatibility.
 * On Android, the underlying instance is still a `Credential` and can be cast:
 * ```kotlin
 * val cred = event.credential as Credential
 * ```
 */
class NoAccessTokenAvailableEvent internal constructor(
    /** The credential associated with the event. */
    val credential: CredentialIdentifier,
) : CredentialEvent
