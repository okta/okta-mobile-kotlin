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
 * Abstracts browser launch and redirect capture for redirect-based OAuth2 flows.
 *
 * Implementations are platform-specific:
 * - **JVM**: [LocalhostBrowserRedirectHandler] starts a localhost HTTP server and opens the system browser.
 * - **Android**: `web-authentication-ui` provides an implementation using Custom Tabs and deep link intent filters.
 *
 * @see LocalhostBrowserRedirectHandler
 */
interface BrowserRedirectHandler {
    /**
     * Opens the given URL in a browser and suspends until the redirect callback is captured.
     *
     * @param url the authorization or logout URL to open in the browser.
     * @return the full redirect callback URI string captured after the user completes the flow.
     * @throws Exception if the redirect cannot be captured (timeout, port conflict, user cancellation).
     */
    suspend fun handleRedirect(url: String): String
}
