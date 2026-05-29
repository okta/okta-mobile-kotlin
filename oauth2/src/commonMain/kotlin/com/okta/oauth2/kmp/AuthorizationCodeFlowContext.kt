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
package com.okta.oauth2.kmp

/**
 * A model representing the context and current state for an authorization code session.
 *
 * @property url the authorization URL to open in a browser.
 * @property redirectUrl the redirect URL configured for the client.
 * @property codeVerifier the PKCE code verifier (internal).
 * @property state the random state parameter for CSRF protection (internal).
 * @property nonce the random nonce for replay protection (internal).
 * @property maxAge the max_age value from the request if provided (internal).
 */
data class AuthorizationCodeFlowContext(
    val url: String,
    val redirectUrl: String,
    internal val codeVerifier: String,
    internal val state: String,
    internal val nonce: String,
    internal val maxAge: Int?,
)
