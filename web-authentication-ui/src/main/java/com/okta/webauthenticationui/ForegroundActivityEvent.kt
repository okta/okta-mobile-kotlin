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
package com.okta.webauthenticationui

import com.okta.authfoundation.events.Event

/**
 * Events emitted during different Android activity lifecycle stages of [ForegroundActivity].
 */
sealed interface ForegroundActivityEvent : Event {
    data object OnCreate : ForegroundActivityEvent
    data object OnNewIntent : ForegroundActivityEvent
    data object OnResume : ForegroundActivityEvent
    data object OnPause : ForegroundActivityEvent
    data object OnDestroy : ForegroundActivityEvent
    data object OnBackPressed : ForegroundActivityEvent
}
