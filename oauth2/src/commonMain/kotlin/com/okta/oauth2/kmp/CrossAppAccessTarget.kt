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

import com.okta.authfoundation.client.ClientAssertionProvider
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.OAuth2EndpointOverrides
import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.kmp.OAuth2Client

/**
 * Describes the resource authorization server that a [CrossAppAccessFlow] redeems an ID-JAG at.
 *
 * A target is named by exactly one of three mutually exclusive forms — [Issuer],
 * [AuthorizationServerId], or [Prebuilt] — each reached through its own factory below. Naming a
 * target by more than one form, or by none, is unrepresentable rather than validated: there is no
 * single constructor that could accept two identifiers or zero, so there is nothing for a runtime
 * check to guard against.
 *
 * None of the three factories can fail; every developer-reachable validation failure (a malformed
 * issuer, a missing credential, and so on) surfaces later, from [CrossAppAccessFlow.create], which
 * is the only place that holds both the primary client and this target's fully-built configuration.
 */
sealed class CrossAppAccessTarget {
    /** Scopes requested at this target, or `null` to rely on a per-call value. */
    abstract val scope: List<String>?

    /** RFC 8707 resource indicator to send with the first step, or `null` to omit it. */
    abstract val resource: String?

    /**
     * A human-readable, credential-free description of this target. Declared `abstract` rather
     * than inherited, so a future fourth form cannot silently pick up a default implementation
     * that discloses a credential.
     */
    abstract override fun toString(): String

    /**
     * A target named by its issuer **origin** — scheme, host, and non-default port, with no path.
     * This is the shape of an org authorization server's issuer.
     */
    class Issuer internal constructor(
        /** The target's issuer origin, e.g. `https://resource.example.com`. */
        val issuer: String,
        override val scope: List<String>?,
        override val resource: String?,
        /** The client identifier to authenticate with at this target, or `null` to use the primary's. */
        val clientId: String?,
        /** The client secret to authenticate with at this target, mutually exclusive with [clientAssertionProvider]. */
        val clientSecret: String?,
        /** The client-assertion provider to authenticate with at this target, mutually exclusive with [clientSecret]. */
        val clientAssertionProvider: ClientAssertionProvider?,
        /** Explicit endpoint overrides for this target, taking precedence over discovered values. */
        val endpointOverrides: OAuth2EndpointOverrides?,
        /** Applied to the target client's builder last, after every setting above. */
        val clientBuildAction: (OAuth2ClientBuilder.() -> Unit)?,
    ) : CrossAppAccessTarget() {
        override fun toString(): String = "CrossAppAccessTarget.Issuer(issuer=$issuer)"
    }

    /**
     * A target named by an Okta custom authorization server identifier, resolved against the
     * **primary** client's org — its scheme, host, and non-default port, plus `/oauth2/<id>`.
     */
    class AuthorizationServerId internal constructor(
        /** The custom authorization server identifier, e.g. `"default"` or a custom AS id. */
        val authorizationServerId: String,
        override val scope: List<String>?,
        override val resource: String?,
        /** The client identifier to authenticate with at this target, or `null` to use the primary's. */
        val clientId: String?,
        /** The client secret to authenticate with at this target, mutually exclusive with [clientAssertionProvider]. */
        val clientSecret: String?,
        /** The client-assertion provider to authenticate with at this target, mutually exclusive with [clientSecret]. */
        val clientAssertionProvider: ClientAssertionProvider?,
        /** Explicit endpoint overrides for this target, taking precedence over discovered values. */
        val endpointOverrides: OAuth2EndpointOverrides?,
        /** Applied to the target client's builder last, after every setting above. */
        val clientBuildAction: (OAuth2ClientBuilder.() -> Unit)?,
    ) : CrossAppAccessTarget() {
        override fun toString(): String = "CrossAppAccessTarget.AuthorizationServerId(authorizationServerId=$authorizationServerId)"
    }

    /**
     * A target backed by an [OAuth2Client] the caller already built and owns. Carries no
     * credential or endpoint settings of its own — those already live on the supplied client's
     * configuration, which is also where the client-authentication requirement is checked.
     */
    class Prebuilt internal constructor(
        internal val client: OAuth2Client,
        override val scope: List<String>?,
        override val resource: String?,
    ) : CrossAppAccessTarget() {
        override fun toString(): String = "CrossAppAccessTarget.Prebuilt(issuer=${client.configuration.issuerUrl})"
    }

    companion object {
        /**
         * Names a target by its issuer **origin**.
         *
         * The origin must carry no path — a full issuer URL such as
         * `https://example.okta.com/oauth2/default` should instead be split into its origin and
         * authorization server id and passed to [forAuthorizationServerId], since a path-bearing
         * value would otherwise resolve to a different server than the one named.
         *
         * @param issuer the target's issuer origin.
         * @param buildAction optional configuration block for the target's remaining settings.
         */
        @JvmStatic
        @JvmOverloads
        fun forIssuer(
            issuer: String,
            buildAction: (CrossAppAccessTargetBuilder.() -> Unit)? = null,
        ): Issuer {
            val builder = CrossAppAccessTargetBuilder()
            buildAction?.invoke(builder)
            return Issuer(
                issuer = issuer,
                scope = builder.scope,
                resource = builder.resource,
                clientId = builder.clientId,
                clientSecret = builder.clientSecret,
                clientAssertionProvider = builder.clientAssertionProvider,
                endpointOverrides = builder.endpointOverrides,
                clientBuildAction = builder.clientBuildAction
            )
        }

        /**
         * Names a target by an Okta custom authorization server identifier, resolved against the
         * primary client's own org.
         *
         * @param authorizationServerId the custom authorization server identifier.
         * @param buildAction optional configuration block for the target's remaining settings.
         */
        @JvmStatic
        @JvmOverloads
        fun forAuthorizationServerId(
            authorizationServerId: String,
            buildAction: (CrossAppAccessTargetBuilder.() -> Unit)? = null,
        ): AuthorizationServerId {
            val builder = CrossAppAccessTargetBuilder()
            buildAction?.invoke(builder)
            return AuthorizationServerId(
                authorizationServerId = authorizationServerId,
                scope = builder.scope,
                resource = builder.resource,
                clientId = builder.clientId,
                clientSecret = builder.clientSecret,
                clientAssertionProvider = builder.clientAssertionProvider,
                endpointOverrides = builder.endpointOverrides,
                clientBuildAction = builder.clientBuildAction
            )
        }

        /**
         * Wraps a target [OAuth2Client] the caller already built and owns — for example, one
         * shared with an app-wide client registry, or one configured with a test-only executor.
         *
         * @param targetClient the already-built client for the target authorization server.
         * @param resource optional RFC 8707 resource indicator to send with the first step.
         * @param scope optional scopes requested at this target.
         */
        @JvmStatic
        @JvmOverloads
        fun wrapping(
            targetClient: OAuth2Client,
            resource: String? = null,
            scope: List<String>? = null,
        ): Prebuilt = Prebuilt(client = targetClient, scope = scope, resource = resource)
    }
}

/**
 * Mutable configuration receiver for [CrossAppAccessTarget.forIssuer] and
 * [CrossAppAccessTarget.forAuthorizationServerId].
 *
 * Public `var` properties are this SDK's accepted builder exception to its public-immutability
 * rule; the product built from them remains immutable.
 */
class CrossAppAccessTargetBuilder internal constructor() {
    /**
     * The client identifier to authenticate with at the target only. Never sent on the first
     * step, whose client identifier is always the primary client's.
     */
    var clientId: String? = null

    /**
     * Scopes requested at the target. Sent as the first step's `scope` when set here or supplied
     * per call; a per-call value takes precedence over this one.
     */
    var scope: List<String>? = null

    /** The client secret to authenticate with at the target, mutually exclusive with [clientAssertionProvider]. */
    var clientSecret: String? = null

    /** The client-assertion provider to authenticate with at the target, mutually exclusive with [clientSecret]. */
    var clientAssertionProvider: ClientAssertionProvider? = null

    /** Explicit endpoint overrides for the target, taking precedence over discovered values. */
    var endpointOverrides: OAuth2EndpointOverrides? = null

    /** RFC 8707 resource indicator to send with the first step. */
    var resource: String? = null

    /**
     * Applied to the target client's [OAuth2ClientBuilder] last, after every setting above, so it
     * wins on conflict. Use this for target-client settings this builder does not name directly
     * (a test executor, a clock, a cache, and so on).
     *
     * Because it is applied last, it can overwrite the credential and identity settings above —
     * including with the primary client's own credential. Configure the target's credential
     * through [clientSecret] or [clientAssertionProvider]; reserve this property for settings
     * those do not cover.
     */
    var clientBuildAction: (OAuth2ClientBuilder.() -> Unit)? = null
}

/**
 * The ID-JAG assertion produced by [CrossAppAccessFlow.start] and consumed by
 * [CrossAppAccessFlow.redeem].
 *
 * This assertion is reusable, not single-use: it stands in for a refresh token at the target and
 * may be redeemed more than once before it expires.
 */
class IdJagAssertion internal constructor(
    /** The assertion value itself. Treated as opaque — nothing in this SDK parses it. */
    val value: String,
    /** The target authorization server this assertion was minted for. */
    val audience: String,
    /** The assertion's lifetime in seconds, as reported by the IdP authorization server. */
    val expiresIn: Int,
    /** Epoch seconds, from the SDK clock, at the moment this assertion was received or restored. */
    val issuedAt: Long,
    /**
     * The granted scope as the IdP authorization server returned it, verbatim and
     * space-delimited — matching how this SDK reports granted scope everywhere else. May be
     * `null`, since some deployments omit this field entirely.
     */
    val scope: String? = null,
    /** The token type the IdP authorization server reported for this assertion, if any. */
    val issuedTokenType: String? = null,
) {
    /**
     * Whether this assertion's reported lifetime has elapsed, as of [clock]. Performs no network
     * call.
     *
     * This is a liveness hint, not an authorization decision: it is arithmetic over the lifetime
     * the server reported at issuance, so it cannot detect server-side revocation. Treat `false`
     * as "worth attempting", not "known valid" — only the target's own response is authoritative.
     */
    fun isExpired(clock: OidcClock): Boolean = clock.currentTimeEpochSecond() >= issuedAt + expiresIn

    override fun toString(): String = "IdJagAssertion(audience=$audience, expiresIn=$expiresIn, issuedAt=$issuedAt)"

    companion object {
        /**
         * Reconstructs an assertion the caller previously obtained and persisted, so it can be
         * redeemed again after a process restart without a new trip to the IdP authorization
         * server.
         *
         * [issuedAt] must be the *original* issuance instant recorded when this assertion was
         * first obtained — a later value overstates its remaining lifetime.
         */
        @JvmStatic
        @JvmOverloads
        fun restore(
            value: String,
            audience: String,
            expiresIn: Int,
            issuedAt: Long,
            scope: String? = null,
            issuedTokenType: String? = null,
        ): IdJagAssertion = IdJagAssertion(value, audience, expiresIn, issuedAt, scope, issuedTokenType)
    }
}

/**
 * A signed-in user's assertion, paired with the subject token type identifier that matches it.
 *
 * Pairing the value with its type through a closed set of named factories makes it impossible to
 * send a token under the wrong type identifier — a value obtained via [idToken] can never be
 * accidentally labeled as an access token, and vice versa.
 */
class SubjectAssertion private constructor(
    /** The assertion value — the raw token string. */
    val value: String,
    /** Which of the three subject forms [value] is. */
    val type: Type,
) {
    /**
     * The subject forms this SDK can transmit.
     *
     * [ID_TOKEN] and [REFRESH_TOKEN] are defined by the governing ID-JAG specification.
     * [ACCESS_TOKEN] is not — it is an Okta deployment extension, requiring an
     * administrator-configured delegation link. An ID token is the only universally accepted
     * default; verify the other two forms against your own deployment before depending on them.
     */
    enum class Type { ID_TOKEN, ACCESS_TOKEN, REFRESH_TOKEN }

    override fun toString(): String = "SubjectAssertion(type=$type)"

    companion object {
        /** Builds a [SubjectAssertion] from an ID token. */
        @JvmStatic
        fun idToken(value: String): SubjectAssertion = SubjectAssertion(value, Type.ID_TOKEN)

        /** Builds a [SubjectAssertion] from an access token. An Okta deployment extension. */
        @JvmStatic
        fun accessToken(value: String): SubjectAssertion = SubjectAssertion(value, Type.ACCESS_TOKEN)

        /** Builds a [SubjectAssertion] from a refresh token. */
        @JvmStatic
        fun refreshToken(value: String): SubjectAssertion = SubjectAssertion(value, Type.REFRESH_TOKEN)
    }
}
