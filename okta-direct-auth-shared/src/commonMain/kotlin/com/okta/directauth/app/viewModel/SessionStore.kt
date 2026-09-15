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
package com.okta.directauth.app.viewModel

import com.okta.authfoundation.client.TokenInfo
import com.okta.directauth.app.model.SessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Retains the most recently signed-in session across navigation, so it survives returning to the
 * home menu and remains available as a Cross App Access subject.
 *
 * Hoisted once, alongside every flow's own view model, at the top of the composable tree — never
 * created inside an individual flow's screen — so that no flow's own `reset()` can reach it. A
 * flow's `reset()` means "abandon this attempt and return to the menu"; that is a different
 * lifetime from "the user has a session", and conflating the two is what previously made Cross App
 * Access unreachable immediately after returning from a successful sign-in.
 */
class SessionStore {
    private val _latestSession = MutableStateFlow<SessionSnapshot?>(null)

    /** The most recent successful sign-in, or `null` if none yet (or after [clear]). */
    val latestSession: StateFlow<SessionSnapshot?> = _latestSession.asStateFlow()

    /** Replaces the retained session wholesale. Called on every successful sign-in. */
    fun publish(
        token: TokenInfo,
        originLabel: String,
    ) {
        _latestSession.update { SessionSnapshot(token, originLabel) }
    }

    /** Clears the retained session. Reserved for an explicit sign-out — never called by a flow's `reset()`. */
    fun clear() {
        _latestSession.update { null }
    }
}
