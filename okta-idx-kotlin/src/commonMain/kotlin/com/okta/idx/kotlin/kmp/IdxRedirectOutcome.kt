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
package com.okta.idx.kotlin.kmp

import com.okta.authfoundation.client.TokenInfo

/**
 * Describes the successful outcome of [InteractionCodeFlow.evaluateRedirectUri].
 *
 * Unlike the legacy `com.okta.idx.kotlin.client.IdxRedirectResult`, there is no `Error` case here —
 * every error path (redirect `state` mismatch, IdP error, unrecognized redirect) is instead one of
 * [InteractionCodeFlow]'s nested exception types, delivered via `Result.failure(...)`. See
 * `contracts/kmp-interaction-code-flow.md` for the rationale.
 */
sealed class IdxRedirectOutcome {
    /**
     * The redirect resulted in a successful authentication, which concluded with tokens.
     */
    class Tokens internal constructor(
        /** The token response. */
        val response: TokenInfo,
    ) : IdxRedirectOutcome()

    /**
     * The redirect resulted in a partially successful authentication, which still needs more
     * interaction from the user.
     */
    class InteractionRequired internal constructor(
        /** The response representing the current state for the authentication transaction. */
        val response: IdxResponse,
    ) : IdxRedirectOutcome()
}
