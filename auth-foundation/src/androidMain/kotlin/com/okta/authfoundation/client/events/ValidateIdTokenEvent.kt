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
package com.okta.authfoundation.client.events

import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.events.EventHandler
import com.okta.authfoundation.events.TokenEvent
import com.okta.authfoundation.jwt.Jwt

/**
 * Emitted via [EventHandler.onEvent] when a [Token] is being validated.
 */
@Deprecated(
    message =
        "Configuring the grace period via event mutation is deprecated. " +
            "Android consumers: replace with AuthFoundationDefaults.idTokenValidator = object : IdTokenValidator { ... } and apply the " +
            "desired grace period inside your implementation. " +
            "KMP/JVM consumers: use OAuth2ClientBuilder { idTokenValidator = DefaultIdTokenValidator(issuedAtGracePeriodInSeconds = N) }."
)
class ValidateIdTokenEvent internal constructor(
    /**
     * The grace period in seconds that will be permitted when verifying the ID Token [Jwt] `iss` field.
     *
     * *Default:* 10 minutes.
     */
    var issuedAtGracePeriodInSeconds: Int,
) : TokenEvent
