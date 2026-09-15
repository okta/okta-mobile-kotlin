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
package com.okta.directauth.app.model

import com.okta.authfoundation.client.TokenInfo

/**
 * The most recently signed-in session, retained across navigation so it survives returning to the
 * home menu.
 *
 * Held by [com.okta.directauth.app.viewModel.SessionStore], which is hoisted above every flow's own
 * view model so that no individual flow's `reset()` can clear it — a flow's `reset()` means
 * "abandon this attempt and return to the menu", which is a different lifetime from "the user has
 * a session".
 *
 * @param token the session's tokens, as issued.
 * @param originLabel which flow produced this session, e.g. "Browser Sign-In" or
 *   "Direct Authentication" — shown so the developer knows what they are about to present as a
 *   Cross App Access subject.
 */
class SessionSnapshot(
    val token: TokenInfo,
    val originLabel: String,
)
