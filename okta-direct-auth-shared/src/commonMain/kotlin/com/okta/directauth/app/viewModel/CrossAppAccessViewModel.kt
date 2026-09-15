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
package com.okta.directauth.app.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.directauth.app.AppConfig
import com.okta.directauth.app.model.ConfigValidation
import com.okta.directauth.app.model.CrossAppAccessConfig
import com.okta.directauth.app.model.CrossAppAccessState
import com.okta.directauth.app.model.IntrospectDisplay
import com.okta.directauth.app.model.SubjectKind
import com.okta.directauth.app.model.validate
import com.okta.directauth.app.platform.TargetCredential
import com.okta.directauth.app.platform.crossAppAccessTargetCredential
import com.okta.directauth.app.platform.platformBrowserLogin
import com.okta.directauth.app.ui.XaaStrings
import com.okta.directauth.app.util.CrossAppAccessErrors
import com.okta.directauth.app.util.FailureAttribution
import com.okta.directauth.app.util.LogScope
import com.okta.directauth.app.util.log
import com.okta.oauth2.kmp.CrossAppAccessFlow
import com.okta.oauth2.kmp.CrossAppAccessTarget
import com.okta.oauth2.kmp.CrossAppAccessTargetBuilder
import com.okta.oauth2.kmp.IdJagAssertion
import com.okta.oauth2.kmp.SubjectAssertion
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Scopes requested by Cross App Access's own dedicated sign-in, matching [OAuth2FlowViewModel]'s. */
private val XAA_IDP_SCOPE = listOf("openid", "profile", "offline_access")

/**
 * ViewModel driving the Cross App Access screen — both the one-action exchange and the
 * step-by-step mode that exposes the intermediate ID-JAG for inspection and reuse.
 *
 * Cross App Access owns its own signed-in session, entirely separate from the rest of this
 * sample's flows and [SessionStore]: the ID-JAG exchange must be submitted to the *org's own*
 * authorization server, never a custom one, so it cannot safely reuse a session that some other
 * flow may have established against a custom `authorizationServerId`. See [signIn].
 *
 * @param idpClient the dedicated Cross App Access requesting-app client, built without an
 *   `authorizationServerId` — `null` when `xaaIdpIssuer`/`xaaIdpClientId` are absent or fail to
 *   build, in which case [state] settles on [CrossAppAccessState.NotConfigured] and no method
 *   here is ever reachable from the UI.
 * @param config the resource app target (and IdP identity) configuration. Defaults to the real
 *   `local.properties`-backed value; overridable so tests can supply a deterministic
 *   configuration without touching the filesystem.
 * @param resolveTargetCredential resolves the target credential. Defaults to the real per-platform
 *   [crossAppAccessTargetCredential]; overridable for the same reason as [config]. There is no
 *   equivalent for the IdP client's own credential: unlike the target, it isn't required, so this
 *   ViewModel never needs to check for its presence — [idpClient] already carries whatever
 *   credential (or none) it was built with.
 * @param performSignIn performs the dedicated browser sign-in. Defaults to the real
 *   [platformBrowserLogin] against [idpClient], with ephemeral browsing enabled — Cross App
 *   Access sign-in reuses the org's own default authorization server that the rest of this app's
 *   flows may also target, so an ephemeral (incognito-equivalent) session here avoids silently
 *   reusing/interfering with a browser cookie session another flow left behind, and vice versa.
 *   Overridable so tests can supply a canned [TokenInfo] without launching a real browser.
 */
class CrossAppAccessViewModel(
    private val idpClient: OAuth2Client?,
    val config: CrossAppAccessConfig = CrossAppAccessConfig.fromAppConfig(),
    private val resolveTargetCredential: () -> TargetCredential = ::crossAppAccessTargetCredential,
    private val performSignIn: suspend (Any?) -> Result<TokenInfo> = { platformContext ->
        platformBrowserLogin(
            platformContext,
            requireNotNull(idpClient),
            AppConfig.SIGN_IN_REDIRECT_URI,
            XAA_IDP_SCOPE,
            enableEphemeralBrowsing = true
        )
    },
) : ViewModel(),
    LogScope {
    override val logTag = "CrossAppAccessViewModel"

    private val _state = MutableStateFlow(computeResetState())

    /** Current state of the Cross App Access screen. */
    val state = _state.asStateFlow()

    private var activeJob: Job? = null

    // Retained between start() and redeem() so the second step can be repeated without a second
    // identity-provider round trip — the whole point of the ID-JAG being reusable.
    private var activeFlow: CrossAppAccessFlow? = null
    private var redemptionCount = 0
    private var lastSelectedKind: SubjectKind = SubjectKind.IDENTITY

    // The dedicated Cross App Access session, established by signIn() — entirely separate from
    // SessionStore, which every other flow shares. Cleared only by reset(); a failed exchange
    // does not require signing in again.
    private var xaaSession: TokenInfo? = null

    /** Updates which subject kind is selected, while [state] is [CrossAppAccessState.Ready]. */
    fun selectSubjectKind(kind: SubjectKind) {
        (_state.value as? CrossAppAccessState.Ready)?.let { ready ->
            _state.value = ready.copy(selectedKind = kind)
        }
    }

    /** Updates the raw scope text entered by the user, while [state] is [CrossAppAccessState.Ready]. */
    fun updateScopeInput(text: String) {
        (_state.value as? CrossAppAccessState.Ready)?.let { ready ->
            _state.value = ready.copy(scopeInput = text)
        }
    }

    /**
     * Performs the dedicated Cross App Access sign-in (Authorization Code + PKCE), separate from
     * every other flow's session. On success, subject kinds become available per [SubjectKind];
     * on failure, attributed to [FailureAttribution.IDENTITY_PROVIDER] — still the IdP org, just
     * this sign-in step rather than the ID-JAG exchange, so it doesn't come from a
     * `CrossAppAccessException` the way the other failure paths here do.
     *
     * @param platformContext platform-specific context (Android `Context`, or `null` on JVM),
     *   passed straight through to [platformBrowserLogin].
     */
    fun signIn(platformContext: Any?) {
        if (_state.value !is CrossAppAccessState.Ready) return

        cancelAndLaunch {
            _state.value = CrossAppAccessState.Working(CrossAppAccessState.Working.Step.SIGNING_IN)
            log("Starting Cross App Access dedicated sign-in")
            performSignIn(platformContext).fold(
                onSuccess = { tokenInfo ->
                    log("Cross App Access sign-in succeeded")
                    xaaSession = tokenInfo
                    _state.value = readyState(tokenInfo)
                },
                onFailure = { error ->
                    log("Cross App Access sign-in failed: ${error.message}")
                    _state.value =
                        CrossAppAccessState.Failed(
                            FailureAttribution.IDENTITY_PROVIDER,
                            error.message ?: "Sign-in failed with an unknown error."
                        )
                }
            )
        }
    }

    /**
     * One-action mode: obtains an ID-JAG and redeems it in a single call, reporting one outcome.
     */
    fun exchange() {
        val ready = _state.value as? CrossAppAccessState.Ready ?: return
        val subject = resolveSubjectAssertion(ready) ?: return
        val client = requireNotNull(idpClient)
        lastSelectedKind = ready.selectedKind

        cancelAndLaunch {
            _state.value = CrossAppAccessState.Working(CrossAppAccessState.Working.Step.OBTAINING_ID_JAG)
            log("Starting Cross App Access one-action exchange (subject kind: ${ready.selectedKind})")
            val flow =
                CrossAppAccessFlow.create(client, buildTarget(client)).getOrElse { error ->
                    log("Failed to build Cross App Access flow: ${error.message}")
                    _state.value = toFailedState(error)
                    return@cancelAndLaunch
                }
            activeFlow = flow
            redemptionCount = 0

            flow.exchange(subject, parseScope(ready.scopeInput)).fold(
                onSuccess = { tokenInfo ->
                    redemptionCount = 1
                    log("Cross App Access exchange succeeded")
                    _state.value = CrossAppAccessState.ResourceTokenObtained(tokenInfo, idJag = null, redemptionCount = redemptionCount)
                },
                onFailure = { error ->
                    log("Cross App Access exchange failed: ${error.message}")
                    _state.value = toFailedState(error)
                }
            )
        }
    }

    /** Step-by-step mode: obtains the ID-JAG only, without redeeming it. */
    fun start() {
        val ready = _state.value as? CrossAppAccessState.Ready ?: return
        val subject = resolveSubjectAssertion(ready) ?: return
        val client = requireNotNull(idpClient)
        lastSelectedKind = ready.selectedKind

        cancelAndLaunch {
            _state.value = CrossAppAccessState.Working(CrossAppAccessState.Working.Step.OBTAINING_ID_JAG)
            log("Starting Cross App Access step-by-step exchange (subject kind: ${ready.selectedKind})")
            val flow =
                CrossAppAccessFlow.create(client, buildTarget(client)).getOrElse { error ->
                    log("Failed to build Cross App Access flow: ${error.message}")
                    _state.value = toFailedState(error)
                    return@cancelAndLaunch
                }
            activeFlow = flow
            redemptionCount = 0

            flow.start(subject, parseScope(ready.scopeInput)).fold(
                onSuccess = { idJag ->
                    log("Cross App Access ID-JAG obtained (audience: ${idJag.audience})")
                    _state.value = CrossAppAccessState.IdJagObtained(idJag, redemptionCount = 0)
                },
                onFailure = { error ->
                    log("Cross App Access start failed: ${error.message}")
                    _state.value = toFailedState(error)
                }
            )
        }
    }

    /**
     * Redeems the currently held ID-JAG for a resource access token. Callable repeatedly while it
     * remains unexpired — each call makes no identity-provider request.
     */
    fun redeem() {
        val idJag = currentIdJag() ?: return
        val flow = activeFlow ?: return
        val client = requireNotNull(idpClient)

        if (idJag.isExpired(client.configuration.clock)) {
            log("ID-JAG has expired; not issuing a redemption request")
            _state.value = CrossAppAccessState.Failed(FailureAttribution.CONFIGURATION, XaaStrings.ID_JAG_EXPIRED_NOTE)
            return
        }

        cancelAndLaunch {
            _state.value = CrossAppAccessState.Working(CrossAppAccessState.Working.Step.REDEEMING)
            log("Redeeming Cross App Access ID-JAG (redemption #${redemptionCount + 1})")
            flow.redeem(idJag).fold(
                onSuccess = { tokenInfo ->
                    redemptionCount += 1
                    log("Cross App Access redemption succeeded")
                    _state.value = CrossAppAccessState.ResourceTokenObtained(tokenInfo, idJag = idJag, redemptionCount = redemptionCount)
                },
                onFailure = { error ->
                    log("Cross App Access redemption failed: ${error.message}")
                    _state.value = toFailedState(error)
                }
            )
        }
    }

    /**
     * Introspects the resource access token at the resource authorization server, per
     * [RFC 7662](https://datatracker.ietf.org/doc/html/rfc7662) — proves the token is actually
     * accepted server-side, which a successful exchange alone does not (that only proves the
     * resource authorization server *issued* it).
     */
    fun introspect() {
        val current = _state.value as? CrossAppAccessState.ResourceTokenObtained ?: return
        val flow = activeFlow ?: return

        cancelAndLaunch {
            _state.value = CrossAppAccessState.Working(CrossAppAccessState.Working.Step.INTROSPECTING)
            log("Introspecting Cross App Access resource token")
            flow.targetClient.introspectToken("access_token", current.tokenInfo.accessToken).fold(
                onSuccess = { info ->
                    log("Cross App Access introspection succeeded (active: ${info.active})")
                    _state.value = current.copy(introspection = IntrospectDisplay(info.active))
                },
                onFailure = { error ->
                    log("Cross App Access introspection failed: ${error.message}")
                    _state.value = toFailedState(error)
                }
            )
        }
    }

    /**
     * Cancels any in-flight work and returns to a clean [CrossAppAccessState.Ready] (or
     * [CrossAppAccessState.NotConfigured]) — recomputed fresh against the current configuration.
     * Called both when leaving the screen and when entering it, so re-entry always starts clean.
     * Never clears [xaaSession] — an aborted exchange shouldn't force signing in again.
     */
    fun reset() {
        activeJob?.cancel()
        activeJob = null
        activeFlow = null
        redemptionCount = 0
        _state.value = computeResetState()
    }

    override fun onCleared() {
        activeJob?.cancel()
        activeFlow = null
    }

    private fun currentIdJag(): IdJagAssertion? =
        when (val current = _state.value) {
            is CrossAppAccessState.IdJagObtained -> current.idJag
            is CrossAppAccessState.ResourceTokenObtained -> current.idJag
            else -> null
        }

    private fun resolveSubjectAssertion(ready: CrossAppAccessState.Ready): SubjectAssertion? {
        val session = xaaSession ?: return null
        if (!ready.selectedKind.availableIn(session)) return null
        return ready.selectedKind.toSubjectAssertion(sessionTokenValue(session, ready.selectedKind))
    }

    private fun sessionTokenValue(
        token: TokenInfo,
        kind: SubjectKind,
    ): String =
        when (kind) {
            SubjectKind.IDENTITY -> requireNotNull(token.idToken) { "Session has no ID token." }
            SubjectKind.ACCESS -> token.accessToken
            SubjectKind.REFRESH -> requireNotNull(token.refreshToken) { "Session has no refresh token." }
        }

    /**
     * Splits [text] on whitespace into a scope list, or `null` if it resolves to nothing — `null`
     * (rather than an empty list) so [CrossAppAccessFlow.exchange]/[CrossAppAccessFlow.start] fall
     * through to their own "no scope was requested" error instead of silently sending an empty
     * `scope` parameter.
     */
    private fun parseScope(text: String): List<String>? =
        text
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .ifEmpty { null }

    private fun buildTarget(client: OAuth2Client): CrossAppAccessTarget {
        val credential = resolveTargetCredential()
        val issuer = config.issuer
        val authorizationServerId = config.authorizationServerId

        val buildAction: CrossAppAccessTargetBuilder.() -> Unit = {
            // Only meaningful when naming the target by issuer: combines with that different
            // org's origin to name a custom authorization server there, exactly like the primary
            // client's own issuer+authorizationServerId — see CrossAppAccessTargetBuilder KDoc.
            // Ignored by forAuthorizationServerId below, whose own parameter already names the id
            // against this app's own org.
            this.authorizationServerId = authorizationServerId.takeIf { issuer != null }
            resource = config.resource
            clientId = config.clientId
            // Credential goes on the target builder's own properties — never inside
            // clientBuildAction, which the SDK applies last and would overwrite it with the
            // primary client's own credential (see CrossAppAccessTargetBuilder KDoc).
            when (credential) {
                is TargetCredential.Secret -> clientSecret = credential.value
                is TargetCredential.Assertion -> clientAssertionProvider = credential.provider
                TargetCredential.None -> Unit
            }
            // clientBuildAction is reserved for settings this builder does not name directly —
            // here, reusing the IdP client's own network executor so the target client makes
            // requests through the same engine (and, in tests, the same fake) rather than
            // constructing a second one with default settings.
            clientBuildAction = { apiExecutor = client.configuration.apiExecutor }
        }
        return if (issuer != null) {
            CrossAppAccessTarget.forIssuer(issuer, buildAction)
        } else {
            CrossAppAccessTarget.forAuthorizationServerId(requireNotNull(authorizationServerId), buildAction)
        }
    }

    private fun toFailedState(error: Throwable): CrossAppAccessState.Failed {
        val classified = CrossAppAccessErrors.classify(error, lastSelectedKind)
        return CrossAppAccessState.Failed(classified.attribution, classified.message)
    }

    private fun computeResetState(): CrossAppAccessState {
        val hasTargetCredential = resolveTargetCredential() != TargetCredential.None
        val validation = config.validate(hasTargetCredential = hasTargetCredential)
        if (validation != ConfigValidation.Complete || idpClient == null) {
            return CrossAppAccessState.NotConfigured(validation)
        }
        return readyState(xaaSession)
    }

    private fun readyState(session: TokenInfo?): CrossAppAccessState.Ready {
        val availableKinds = session?.let { s -> SubjectKind.entries.filter { it.availableIn(s) } } ?: emptyList()
        return CrossAppAccessState.Ready(
            availableSubjectKinds = availableKinds,
            selectedKind = SubjectKind.IDENTITY,
            hasSession = session != null,
            session = session
        )
    }

    private fun cancelAndLaunch(block: suspend context(LogScope) () -> Unit) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch { context(this@CrossAppAccessViewModel) { block() } }
    }
}
