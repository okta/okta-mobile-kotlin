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
 * A model representing the context and current state for a redirect end-session flow.
 *
 * @property url the end-session URL to open in a browser.
 * @property redirectUrl the post-logout redirect URI configured for the client.
 * @property state the random state parameter for CSRF protection (internal).
 */
data class RedirectEndSessionFlowContext(
    val url: String,
    val redirectUrl: String,
    internal val state: String,
)
