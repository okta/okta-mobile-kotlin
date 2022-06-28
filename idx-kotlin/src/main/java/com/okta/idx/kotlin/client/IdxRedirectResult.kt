/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.client

import com.okta.authfoundation.credential.Token
import com.okta.idx.kotlin.dto.IdxResponse

/**
 * Describes the result when using [InteractionCodeFlow.evaluateRedirectUri].
 */
sealed class IdxRedirectResult {
    /**
     * The redirect resulted in a successful authentication, which concluded with tokens.
     */
    class Tokens internal constructor(
        /** The token response. */
        val response: Token,
    ) : IdxRedirectResult()

    /**
     * The redirect resulted in a partially successful authentication, which still needs more
     * interaction from the user.
     */
    class InteractionRequired internal constructor(
        /** The response representing the current state for the authentication transaction. */
        val response: IdxResponse,
    ) : IdxRedirectResult()

    /**
     * There was an error when attempting to handle the redirect.
     */
    class Error internal constructor(
        /** The error message associated with the error. */
        val errorMessage: String,
        /** The optional exception associated with the error. */
        val exception: Exception? = null,
    ) : IdxRedirectResult()
}
