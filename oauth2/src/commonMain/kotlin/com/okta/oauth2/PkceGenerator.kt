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

import com.okta.authfoundation.crypto.secureRandomBytes
import com.okta.authfoundation.crypto.sha256Digest
import kotlin.io.encoding.Base64

internal object PkceGenerator {
    const val CODE_CHALLENGE_METHOD = "S256"

    fun codeChallenge(codeVerifier: String): String {
        val bytes: ByteArray = codeVerifier.toByteArray(Charsets.US_ASCII)
        val digest: ByteArray = sha256Digest(bytes)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(digest)
    }

    fun codeVerifier(): String {
        val codeVerifier = secureRandomBytes(32)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(codeVerifier)
    }
}
