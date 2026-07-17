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

import com.okta.authfoundation.events.CredentialEvent
import com.okta.authfoundation.events.EventHandler

/**
 * Emitted via [EventHandler.onEvent] when a storage call causes an exception.
 *
 * In the KMP credential system, [shouldClearStorageAndTryAgain] reflects the decision made by the
 * `onStorageError` callback configured on [com.okta.authfoundation.credential.kmp.TokenCredentialManager].
 * This event is notification-only on KMP; mutating it does not affect retry behavior.
 * On Android (legacy path), use [withShouldClearStorageAndTryAgain] to request a retry.
 */
class TokenStorageAccessErrorEvent internal constructor(
    /**
     * The [Exception] that caused the event.
     */
    val exception: Exception,
    shouldClearStorageAndTryAgain: Boolean,
) : CredentialEvent {
    internal var shouldClearStorageAndTryAgainValue: Boolean = shouldClearStorageAndTryAgain

    /**
     * If true, the operation will be (or was) retried.
     *
     * On Android (legacy path), setting this to `true` via [withShouldClearStorageAndTryAgain]
     * requests a retry. On KMP, this reflects the decision already made by the `onStorageError`
     * callback and mutating it has no effect.
     *
     * Prefer [withShouldClearStorageAndTryAgain] over direct assignment.
     */
    @set:Deprecated(
        message = "Use withShouldClearStorageAndTryAgain() instead.",
        replaceWith = ReplaceWith("withShouldClearStorageAndTryAgain(value)")
    )
    var shouldClearStorageAndTryAgain: Boolean
        get() = shouldClearStorageAndTryAgainValue
        set(value) {
            shouldClearStorageAndTryAgainValue = value
        }

    /**
     * Sets [shouldClearStorageAndTryAgain] and returns this event for chaining.
     */
    fun withShouldClearStorageAndTryAgain(value: Boolean): TokenStorageAccessErrorEvent {
        shouldClearStorageAndTryAgainValue = value
        return this
    }
}
