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
package com.okta.oauth2

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.crypto.PkceGenerator as SharedPkceGenerator

/**
 * Delegates to the shared [SharedPkceGenerator] in `auth-foundation` — kept as a same-package
 * object (rather than having every caller import the `auth-foundation` type directly) so the
 * frozen, Android-only [AuthorizationCodeFlow] can keep resolving `PkceGenerator` implicitly,
 * exactly as it did before the algorithm itself moved to `auth-foundation` to de-duplicate it
 * against `okta-idx-kotlin`'s copy.
 */
@OptIn(InternalAuthFoundationApi::class)
internal object PkceGenerator {
    const val CODE_CHALLENGE_METHOD = SharedPkceGenerator.CODE_CHALLENGE_METHOD

    fun codeChallenge(codeVerifier: String): String = SharedPkceGenerator.codeChallenge(codeVerifier)

    fun codeVerifier(): String = SharedPkceGenerator.codeVerifier()
}
