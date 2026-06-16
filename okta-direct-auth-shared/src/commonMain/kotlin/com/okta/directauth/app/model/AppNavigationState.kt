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

/**
 * Top-level navigation state for the app.
 *
 * Drives the root Crossfade in [com.okta.directauth.app.App], allowing the user to choose
 * between the existing Direct Authentication flow and the new OAuth2 flows from the home menu.
 */
sealed class AppNavigationState {
    data object HomeMenu : AppNavigationState()

    data object DirectAuth : AppNavigationState()

    data object ResourceOwner : AppNavigationState()

    data object DeviceAuthorization : AppNavigationState()

    data object BrowserAuth : AppNavigationState()

    data object TokenExchange : AppNavigationState()

    data object SessionToken : AppNavigationState()
}
