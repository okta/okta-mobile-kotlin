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
package com.okta.authfoundation.client.kmp.events

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.events.TokenEvent

/**
 * Emitted when a new token is obtained from a token endpoint call,
 * including both initial token creation and token refresh.
 *
 * This matches the Android `TokenCreatedEvent` behavior where the event
 * fires on every `tokenRequest` call.
 *
 * @param tokenInfo the newly created token information.
 */
class TokenCreatedEvent internal constructor(
    val tokenInfo: TokenInfo,
) : TokenEvent
