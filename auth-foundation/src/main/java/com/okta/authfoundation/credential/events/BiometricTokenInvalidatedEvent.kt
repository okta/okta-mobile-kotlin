/*
 * Copyright 2024-Present Okta, Inc.
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

import android.media.session.MediaSession.Token
import com.okta.authfoundation.events.Event

/**
 * Emitted when the key of a biometric secured [Token] is invalidated.
 */
class BiometricTokenInvalidatedEvent internal constructor(
    /**
     * The id of the invalidated [Token].
     */
    val tokenId: String,
    /**
     * Whether the invalidated [Token] should be deleted. [true] by default.
     */
    var deleteInvalidatedToken: Boolean = true
) : Event
