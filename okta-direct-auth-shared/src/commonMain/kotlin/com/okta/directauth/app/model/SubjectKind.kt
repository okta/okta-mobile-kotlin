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
package com.okta.directauth.app.model

import com.okta.authfoundation.client.TokenInfo
import com.okta.oauth2.kmp.SubjectAssertion

/**
 * The three kinds of token the sample can present as a Cross App Access subject, mirroring
 * [SubjectAssertion.Type].
 *
 * [IDENTITY] is the only kind universally accepted by an identity provider; [ACCESS] and
 * [REFRESH] are Okta deployment extensions that depend on the org's own configuration. The sample
 * always pairs a token value with one of these three kinds through [toSubjectAssertion], so a
 * value can never be sent under the wrong kind.
 */
enum class SubjectKind {
    IDENTITY,
    ACCESS,
    REFRESH,
    ;

    /**
     * Whether [token] carries the value this kind needs.
     *
     * [ACCESS] is always available, since every [TokenInfo] carries an access token. [IDENTITY]
     * and [REFRESH] depend on whether the session that produced [token] requested the
     * corresponding token.
     */
    fun availableIn(token: TokenInfo): Boolean =
        when (this) {
            IDENTITY -> !token.idToken.isNullOrBlank()
            ACCESS -> true
            REFRESH -> !token.refreshToken.isNullOrBlank()
        }

    /**
     * Builds a [SubjectAssertion] of this kind from [value].
     *
     * Only meaningful when the value actually corresponds to this kind — callers deriving from a
     * [TokenInfo] must check [availableIn] first.
     */
    fun toSubjectAssertion(value: String): SubjectAssertion =
        when (this) {
            IDENTITY -> SubjectAssertion.idToken(value)
            ACCESS -> SubjectAssertion.accessToken(value)
            REFRESH -> SubjectAssertion.refreshToken(value)
        }

    /**
     * The raw token value this kind needs from [token], or `null` if [token] doesn't carry it —
     * mirrors [availableIn], for callers that want the value itself rather than a yes/no (e.g.
     * previewing the token's contents before it's ever sent as a subject).
     */
    fun valueIn(token: TokenInfo): String? =
        when (this) {
            IDENTITY -> token.idToken
            ACCESS -> token.accessToken
            REFRESH -> token.refreshToken
        }
}
