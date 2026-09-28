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
package com.okta.authfoundation.crypto

import com.okta.authfoundation.InternalAuthFoundationApi
import kotlin.io.encoding.Base64

/**
 * Generates PKCE (RFC 7636) code verifiers and code challenges.
 *
 * Shared by [com.okta.oauth2] and [com.okta.idx.kotlin] flow implementations so the PKCE
 * algorithm has a single source of truth.
 */
@InternalAuthFoundationApi
object PkceGenerator {
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
