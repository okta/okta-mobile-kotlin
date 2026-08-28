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
import com.okta.authfoundation.credential.kmp.Credential

/**
 * Derives a [SubjectAssertion] from this credential's stored token, so a caller does not have to
 * pull the raw token string out by hand.
 *
 * This is the commonMain `Credential` interface, not the deprecated Android-only `Credential`
 * class — a multiplatform capability cannot extend the latter. Existing sample code using the
 * deprecated type must migrate to this interface first.
 *
 * @param type which of the credential's stored tokens to use. Defaults to the ID token.
 * @return [Result.success] with the [SubjectAssertion], or [Result.failure] with an
 *   [IllegalStateException] naming the missing token, produced before any network request, if
 *   [type]'s corresponding token is absent from this credential.
 */
suspend fun Credential.crossAppAccessSubject(type: SubjectAssertion.Type = SubjectAssertion.Type.ID_TOKEN): Result<SubjectAssertion> =
    TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258537")

/**
 * One-call convenience that derives a subject assertion from this credential and runs the
 * complete Cross App Access exchange against [target], without mutating, replacing, or
 * invalidating this credential.
 *
 * @param idpClient the primary client, already configured against the IdP authorization server.
 * @param target the resource authorization server to redeem an ID-JAG at.
 * @param scope requested scopes at the target; see [CrossAppAccessFlow.start].
 * @return [Result.success] with the resource access [TokenInfo], or [Result.failure] from
 *   deriving the subject assertion, from [CrossAppAccessFlow.create], or from the exchange itself.
 */
suspend fun Credential.crossAppAccessToken(
    idpClient: OAuth2Client,
    target: CrossAppAccessTarget,
    scope: List<String>? = null,
): Result<TokenInfo> = TODO("implemented in the https://oktainc.atlassian.net/browse/OKTA-1258537")
