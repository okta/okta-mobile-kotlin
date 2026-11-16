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
package com.okta.webauthenticationui

/**
 * Controls whether [WebAuthentication.login] persists the in-progress login state (including the PKCE
 * `code_verifier`) so that [WebAuthentication.resumeLogin] can finish it after the app process is killed
 * while the user is in the browser.
 */
sealed interface PendingLoginPersistence {
    /**
     * Nothing is written to disk. This is the default. A login interrupted by process death cannot be resumed, and
     * [WebAuthentication.resumeLogin] always returns `null`.
     */
    data object Disabled : PendingLoginPersistence

    /**
     * The login state is stored in app-private storage for a short time (see [WebAuthentication.resumeLogin]).
     * This is opt-in; [Disabled] is the default. Prefer supplying an [encryptor], and exclude the
     * `com.okta.webauthenticationui.pending_login` DataStore file from Android backup and device transfer.
     *
     * @param encryptor encrypts the stored state. When `null` the state is stored unencrypted.
     */
    class Enabled(
        val encryptor: PendingLoginEncryptor? = null,
    ) : PendingLoginPersistence
}
