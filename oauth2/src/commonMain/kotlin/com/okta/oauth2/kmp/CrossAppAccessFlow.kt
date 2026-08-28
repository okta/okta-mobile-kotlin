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
 * Implements [Cross App Access](https://developer.okta.com/docs/concepts/xaa/) (XAA), the
 * Identity Assertion JWT Authorization Grant (ID-JAG) authorization-chaining exchange defined by
 * [draft-ietf-oauth-identity-assertion-authz-grant](https://datatracker.ietf.org/doc/draft-ietf-oauth-identity-assertion-authz-grant/).
 *
 * Cross App Access lets a signed-in user's session in a *requesting app* be used to call a
 * *resource app*'s API in a different security domain — with no second consent prompt and no
 * static API key — provided an administrator has configured a trusted connection between the two
 * apps. The exchange is two steps:
 *
 * 1. [start] presents the user's assertion at the **IdP authorization server** and receives a
 *    short-lived [IdJagAssertion].
 * 2. [redeem] presents that assertion at the **resource authorization server** and receives a
 *    short-lived, scoped resource access token.
 *
 * ```kotlin
 * val target = CrossAppAccessTarget.forIssuer("https://resource.example.com") {
 *     clientSecret = "target-client-secret"
 * }
 * val flow = CrossAppAccessFlow.create(idpClient, target).getOrThrow()
 * val idJag = flow.start(SubjectAssertion.idToken(idToken)).getOrThrow()
 * val resourceToken = flow.redeem(idJag).getOrThrow()
 * ```
 *
 * If you do not need to keep the intermediate [IdJagAssertion], call [exchange] instead.
 *
 * This capability is not appropriate for background or machine-to-machine processing with no
 * active human user session — use the org's other grant flows for that.
 */
interface CrossAppAccessFlow {
    /**
     * The **IdP authorization server** client — used for [start], where the ID-JAG is minted.
     *
     * ID-JAG issuance events arrive on this client's `events` stream; resource-token events
     * arrive on [targetClient] instead.
     */
    val idpClient: OAuth2Client

    /**
     * The **resource authorization server** client — used for [redeem], where the ID-JAG is
     * redeemed for a resource access token.
     *
     * Exposed so callers can collect its `events` stream: resource-token issuance is emitted
     * here, not on [idpClient].
     */
    val targetClient: OAuth2Client

    /**
     * Exchanges [subjectAssertion] for an ID-JAG at the IdP authorization server.
     *
     * The audience sent is always the target's resolved issuer; there is no per-call override.
     *
     * @param subjectAssertion the signed-in user's assertion to present.
     * @param scope requested scopes at the target, taking precedence over any configured on the
     *   target itself. When neither is supplied, this call fails before any network request.
     * @return [Result.success] with the [IdJagAssertion], or [Result.failure] with a
     *   [CrossAppAccessException.IdpExchangeFailed] wrapping the underlying cause.
     */
    suspend fun start(
        subjectAssertion: SubjectAssertion,
        scope: List<String>? = null,
    ): Result<IdJagAssertion>

    /**
     * Redeems [idJag] for a resource access token at the resource authorization server.
     *
     * Repeatable while [idJag] remains unexpired — this is the renewal path in place of a
     * refresh token, so a fresh resource access token can be obtained without repeating [start].
     *
     * @param idJag a previously obtained (or [IdJagAssertion.restore]d) assertion.
     * @return [Result.success] with the resource access [TokenInfo], or [Result.failure] with a
     *   [CrossAppAccessException.TargetRedemptionFailed] wrapping the underlying cause.
     */
    suspend fun redeem(idJag: IdJagAssertion): Result<TokenInfo>

    /**
     * Convenience that runs [start] then [redeem] and returns the resulting resource access
     * token.
     *
     * @param subjectAssertion the signed-in user's assertion to present.
     * @param scope requested scopes at the target; see [start].
     * @return [Result.success] with the resource access [TokenInfo], or [Result.failure] from
     *   whichever step failed.
     */
    suspend fun exchange(
        subjectAssertion: SubjectAssertion,
        scope: List<String>? = null,
    ): Result<TokenInfo>

    /**
     * A diagnostic summary naming both authorization servers in exchange order. Contains only
     * the two resolved issuer URLs — never an assertion, token, secret, scope, or subject value.
     * This format is for humans, is not stable across releases, and must not be parsed.
     */
    override fun toString(): String

    companion object {
        /**
         * Builds the target client from [target] (or adopts one already built), validates that
         * the resulting flow can actually authenticate at both servers, and returns the flow.
         *
         * This is the single validation point for the whole capability: every developer-reachable
         * rejection — an invalid target shape, a missing target credential, a target that
         * resolves to [idpClient]'s own issuer — surfaces from here, before any network request.
         *
         * @param idpClient the primary client, already configured against the IdP authorization
         *   server.
         * @param target the resource authorization server to redeem ID-JAGs at.
         * @return [Result.success] with the flow, or [Result.failure] with an
         *   [IllegalArgumentException] describing the configuration problem.
         */
        @JvmStatic
        fun create(
            idpClient: OAuth2Client,
            target: CrossAppAccessTarget,
        ): Result<CrossAppAccessFlow> = CrossAppAccessFlowImpl.create(idpClient, target)
    }
}
