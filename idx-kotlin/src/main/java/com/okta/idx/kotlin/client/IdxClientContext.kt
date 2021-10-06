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

/**
 * Object that defines the context for the current authentication session, which is required when a session needs to be resumed.
 */
class IdxClientContext internal constructor(
    /** The PKCE code verifier value used when initiating the session using the `interact` method. */
    internal val codeVerifier: String,

    /** The interaction handle returned from the `interact` response from the server. */
    internal val interactionHandle: String,

    /**
     * The state value used when the `interact` call was initially made.
     *
     * This value can be used to associate a redirect URI to the associated Context that can be used to resume an authentication session.
     */
    internal val state: String,
)
