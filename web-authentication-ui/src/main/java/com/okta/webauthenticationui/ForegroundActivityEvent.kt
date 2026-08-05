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
package com.okta.webauthenticationui

import com.okta.webauthenticationui.events.UIEvent

/**
 * Lifecycle events emitted through the SDK's [com.okta.authfoundation.events.EventCoordinator]
 * while the browser redirect flow is in the foreground. Observe these via an
 * [com.okta.authfoundation.events.EventHandler] to react to the redirect activity's lifecycle
 * (for example, to detect a user-cancelled flow). This is a [UIEvent].
 */
sealed interface ForegroundActivityEvent : UIEvent {
    /** Emitted when the redirect activity is created and the browser flow is about to launch. */
    data object OnCreate : ForegroundActivityEvent

    /** Emitted when a redirect Intent is delivered back to the activity (the browser returned a result). */
    data object OnNewIntent : ForegroundActivityEvent

    /**
     * Emitted when the user returns to the app without completing the flow (e.g. back-navigation),
     * typically preceding a [WebAuthentication.FlowCancelledException].
     */
    data object OnResume : ForegroundActivityEvent

    /** Emitted when the activity is paused (the browser or another activity has taken focus). */
    data object OnPause : ForegroundActivityEvent

    /** Emitted when the redirect activity is destroyed. */
    data object OnDestroy : ForegroundActivityEvent

    /** Emitted when the user presses the back button while the redirect activity is in the foreground. */
    data object OnBackPressed : ForegroundActivityEvent
}
