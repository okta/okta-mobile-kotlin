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
fun Credential.crossAppAccessSubject(type: SubjectAssertion.Type = SubjectAssertion.Type.ID_TOKEN): Result<SubjectAssertion> =
    runCatching {
        when (type) {
            SubjectAssertion.Type.ID_TOKEN -> {
                SubjectAssertion.idToken(
                    token.idToken ?: throw IllegalStateException("This credential has no ID token to use as a Cross App Access subject.")
                )
            }

            SubjectAssertion.Type.ACCESS_TOKEN -> {
                SubjectAssertion.accessToken(token.accessToken)
            }

            SubjectAssertion.Type.REFRESH_TOKEN -> {
                SubjectAssertion.refreshToken(
                    token.refreshToken
                        ?: throw IllegalStateException("This credential has no refresh token to use as a Cross App Access subject.")
                )
            }
        }
    }

/**
 * One-call convenience that derives a subject assertion from this credential and runs the
 * complete Cross App Access exchange against [target], without mutating, replacing, or
 * invalidating this credential.
 *
 * [idpClient] must be the client that actually manages this credential — i.e. its configured
 * issuer and client ID must match [Credential.token]'s [TokenInfo.issuerUrl] and
 * [TokenInfo.clientId]. Otherwise the derived subject assertion would be presented to an IdP
 * authorization server that never minted it.
 *
 * @param idpClient the primary client, already configured against the IdP authorization server
 *   that minted this credential's token.
 * @param target the resource authorization server to redeem an ID-JAG at.
 * @param subjectType which of the credential's stored tokens to derive the subject assertion
 *   from; see [crossAppAccessSubject]. Defaults to the ID token.
 * @param scope requested scopes at the target; see [CrossAppAccessFlow.start].
 * @return [Result.success] with the resource access [TokenInfo], or [Result.failure] with an
 *   [IllegalArgumentException] if [idpClient] does not match this credential's issuer or client
 *   ID, or otherwise from deriving the subject assertion, from [CrossAppAccessFlow.create], or
 *   from the exchange itself.
 */
suspend fun Credential.crossAppAccessToken(
    idpClient: OAuth2Client,
    target: CrossAppAccessTarget,
    subjectType: SubjectAssertion.Type = SubjectAssertion.Type.ID_TOKEN,
    scope: List<String>? = null,
): Result<TokenInfo> =
    runCatching {
        require(idpClient.configuration.issuerUrl.trimEnd('/') == token.issuerUrl.trimEnd('/')) {
            "idpClient is configured for issuer ${idpClient.configuration.issuerUrl}, but this credential's token " +
                "was minted by ${token.issuerUrl}. Pass the OAuth2Client that manages this credential."
        }
        require(idpClient.configuration.clientId == token.clientId) {
            "idpClient's client ID (${idpClient.configuration.clientId}) does not match this credential's token " +
                "client ID (${token.clientId}). Pass the OAuth2Client that manages this credential."
        }
        val subject = crossAppAccessSubject(subjectType).getOrThrow()
        val flow = CrossAppAccessFlow.create(idpClient, target).getOrThrow()
        flow.exchange(subject, scope).getOrThrow()
    }
