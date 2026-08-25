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

import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * Default implementation of [CrossAppAccessFlow].
 *
 * @param idpClient the primary client, used for [start].
 * @param targetClient the resource authorization server client, used for [redeem].
 */
internal class CrossAppAccessFlowImpl(
    override val idpClient: OAuth2Client,
    override val targetClient: OAuth2Client,
) : CrossAppAccessFlow {
    override suspend fun start(
        subjectAssertion: SubjectAssertion,
        scope: List<String>?,
    ): Result<IdJagAssertion> = TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258536")

    override suspend fun redeem(idJag: IdJagAssertion): Result<TokenInfo> = TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258536")

    override suspend fun exchange(
        subjectAssertion: SubjectAssertion,
        scope: List<String>?,
    ): Result<TokenInfo> = TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258536")

    override fun toString(): String = TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258536")

    companion object {
        fun create(
            idpClient: OAuth2Client,
            target: CrossAppAccessTarget,
        ): Result<CrossAppAccessFlow> = TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258536")
    }
}
