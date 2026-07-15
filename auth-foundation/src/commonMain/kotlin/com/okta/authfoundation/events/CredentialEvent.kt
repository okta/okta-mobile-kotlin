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
package com.okta.authfoundation.events

/**
 * Marker interface for events related to credential lifecycle operations such as creation,
 * deletion, token storage, and biometric key state. Handlers that operate at the model layer
 * and are only interested in credential state changes can filter by this type:
 *
 * ```kotlin
 * EventCoordinator(object : EventHandler {
 *     override fun onEvent(event: Event) {
 *         if (event !is CredentialEvent) return
 *         // handle credential events only
 *     }
 * })
 * ```
 */
interface CredentialEvent : Event
