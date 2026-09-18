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
import com.okta.directauth.app.util.FailureAttribution
import com.okta.oauth2.kmp.IdJagAssertion

/**
 * State rendered by the Cross App Access screen.
 *
 * Managed by [com.okta.directauth.app.viewModel.CrossAppAccessViewModel]. The target's
 * configuration itself is held separately on the view model (it does not change once loaded), so
 * it does not need to be threaded through every state variant here.
 */
sealed class CrossAppAccessState {
    /** Configuration is absent, incomplete, or ambiguous — shown before any network request. */
    data class NotConfigured(
        val validation: ConfigValidation,
    ) : CrossAppAccessState()

    /** Idle and startable. */
    data class Ready(
        /** Subject kinds this state's session (if any) can supply. Empty when there is no session. */
        val availableSubjectKinds: List<SubjectKind>,
        /** The currently selected kind — [SubjectKind.IDENTITY] by default. */
        val selectedKind: SubjectKind,
        /**
         * Whether the dedicated Cross App Access sign-in (via [com.okta.directauth.app.viewModel.CrossAppAccessViewModel.signIn])
         * has produced a session. When `false`, the only way to proceed is that sign-in — there is
         * no pasted-token fallback, since Cross App Access requires an actual signed-in session at
         * a specific, dedicated requesting-app identity.
         */
        val hasSession: Boolean,
        /**
         * Raw, space-separated scope text entered by the user, taking precedence over any scope
         * configured on the target itself — the SDK's org authorization server rejects a
         * scope-less exchange, so this must be non-blank before starting one.
         *
         * Must be a custom, resource-specific scope defined on the *target's* authorization
         * server (e.g. `chat.read`) — not a standard OIDC scope like `openid`/`profile`/`email`/
         * `offline_access`. Those are valid for the dedicated sign-in's own login, but the org
         * authorization server rejects them here with "The following scopes are not allowed for
         * this request", since this scope is requested at the ID-JAG exchange itself, for the
         * target resource, not for the identity provider. Left blank by default (rather than
         * prefilled with a value that would always fail on a target that doesn't define that
         * exact scope) — see [com.okta.directauth.app.ui.XaaStrings.SCOPE_PLACEHOLDER] for the
         * in-field hint.
         */
        val scopeInput: String = "",
        /**
         * The session's raw [TokenInfo], so the screen can decode and display the contents of
         * whichever [SubjectKind] the developer wants to inspect — `null` when [hasSession] is
         * `false`.
         */
        val session: TokenInfo? = null,
    ) : CrossAppAccessState() {
        // Kept a data class for .copy() (selectSubjectKind()/updateScopeInput() rely on it), but
        // the default toString() would print session's raw tokens — overridden so an incidental
        // `log("state: $state")` down the line can never do that. See redactedTokenInfo.
        override fun toString(): String =
            "Ready(availableSubjectKinds=$availableSubjectKinds, selectedKind=$selectedKind, hasSession=$hasSession, " +
                "scopeInput=$scopeInput, session=${session?.let(::redactedTokenInfo)})"
    }

    /** A step of the exchange (or the dedicated sign-in) is in flight. */
    data class Working(
        val step: Step,
    ) : CrossAppAccessState() {
        enum class Step { SIGNING_IN, OBTAINING_ID_JAG, REDEEMING, INTROSPECTING }
    }

    /** The first step succeeded; the ID-JAG has not been redeemed since [redemptionCount] resets. */
    data class IdJagObtained(
        val idJag: IdJagAssertion,
        val redemptionCount: Int,
    ) : CrossAppAccessState() {
        // See Ready.toString(): idJag.value is itself a bearer assertion.
        override fun toString(): String = "IdJagObtained(idJag=${redactedIdJag(idJag)}, redemptionCount=$redemptionCount)"
    }

    /** A resource access token was obtained. */
    data class ResourceTokenObtained(
        val tokenInfo: TokenInfo,
        /** The ID-JAG that produced this token, present only in step-by-step mode. */
        val idJag: IdJagAssertion?,
        val redemptionCount: Int,
        /**
         * The result of introspecting [tokenInfo] at the resource authorization server, once the
         * developer has asked for it — `null` until then. Proves the token is actually accepted
         * server-side, which a successful exchange alone does not: the exchange only proves the
         * resource authorization server *issued* the token.
         */
        val introspection: IntrospectDisplay? = null,
    ) : CrossAppAccessState() {
        // See Ready.toString().
        override fun toString(): String =
            "ResourceTokenObtained(tokenInfo=${redactedTokenInfo(tokenInfo)}, idJag=${idJag?.let(::redactedIdJag)}, " +
                "redemptionCount=$redemptionCount, introspection=$introspection)"
    }

    /** A failure, attributed to a named server (or configuration). */
    data class Failed(
        val attribution: FailureAttribution,
        val serverMessage: String,
        /**
         * The ID-JAG a failed (not expired — that one is never retained, since it's already
         * unusable) redemption was attempting to redeem, if any — lets the screen offer redeeming
         * the same ID-JAG again without a fresh identity-provider round trip. `null` for every
         * other failure path, including a failed exchange() (which never surfaces its intermediate
         * ID-JAG to begin with) and a failed signIn()/start()/introspect().
         */
        val retainedIdJag: IdJagAssertion? = null,
    ) : CrossAppAccessState() {
        // See Ready.toString().
        override fun toString(): String = "Failed(attribution=$attribution, serverMessage=$serverMessage, retainedIdJag=${retainedIdJag?.let(::redactedIdJag)})"
    }
}

/** Describes [token] for logging/toString without ever including its bearer values. */
private fun redactedTokenInfo(token: TokenInfo): String =
    "TokenInfo(tokenType=${token.tokenType}, scope=${token.scope}, expiresIn=${token.expiresIn}, " +
        "hasIdToken=${token.idToken != null}, hasRefreshToken=${token.refreshToken != null})"

/** Describes [idJag] for logging/toString without ever including its bearer assertion value. */
private fun redactedIdJag(idJag: IdJagAssertion): String = "IdJagAssertion(audience=${idJag.audience}, scope=${idJag.scope}, expiresIn=${idJag.expiresIn})"

/**
 * The result of an RFC 7662 introspection call against the resource authorization server,
 * reduced to the one thing that answers "does this token actually work": [active]. Deliberately
 * carries nothing else — claim-level detail is available via the SDK's own `IntrospectInfo` for
 * anyone who needs it, but isn't this screen's job to surface.
 */
data class IntrospectDisplay(
    val active: Boolean,
)
