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
package com.okta.directauth.app

import com.okta.authfoundation.client.OidcClock
import com.okta.authfoundation.client.TokenInfo
import com.okta.directauth.app.model.CrossAppAccessConfig
import com.okta.directauth.app.model.CrossAppAccessState
import com.okta.directauth.app.model.SubjectKind
import com.okta.directauth.app.platform.TargetCredential
import com.okta.directauth.app.viewModel.CrossAppAccessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies [CrossAppAccessViewModel] state transitions: the dedicated sign-in, cancel-and-supersede,
 * clean re-entry, and the reusable-ID-JAG renewal path — all without a real network, and all
 * against a config injected via constructor rather than the real `local.properties`, so these
 * tests are deterministic regardless of what a developer's own machine has configured.
 *
 * Waits on [CrossAppAccessState] transitions via [kotlinx.coroutines.flow.first] rather than
 * `advanceUntilIdle()`: the SDK's token-request plumbing hops onto its own compute/IO dispatchers
 * internally, which a `TestDispatcher` installed only as [Dispatchers.Main] cannot see or
 * fast-forward. [awaitSettled] additionally hops the *awaiting* coroutine itself onto
 * [Dispatchers.Default] with a real-time [withTimeout] — a virtual-time timeout ambient to
 * `runTest` raced ahead of that genuinely-real async work in practice; see its KDoc.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrossAppAccessStateTest {
    private val idpIssuer = "https://idp.example.com"
    private val targetIssuer = "https://resource.example.com"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun completeConfig() =
        CrossAppAccessConfig(
            idpIssuer = idpIssuer,
            idpClientId = "idp-client-id",
            issuer = targetIssuer,
            authorizationServerId = null,
            clientId = "idp-client-id",
            resource = null
        )

    /** A fake dedicated sign-in that succeeds immediately with [tokenInfo], never touching the network. */
    private fun signInSucceeding(tokenInfo: TokenInfo = FakeTokenInfo()): suspend (Any?) -> Result<TokenInfo> = { Result.success(tokenInfo) }

    /** A fake dedicated sign-in that fails immediately with [error], never touching the network. */
    private fun signInFailing(error: Throwable): suspend (Any?) -> Result<TokenInfo> = { Result.failure(error) }

    private fun buildViewModel(
        executor: RecordingApiExecutor,
        config: CrossAppAccessConfig = completeConfig(),
        targetCredential: TargetCredential = TargetCredential.Secret("target-secret"),
        clock: OidcClock = MutableClock(),
        performSignIn: suspend (Any?) -> Result<TokenInfo> = signInSucceeding(),
    ): CrossAppAccessViewModel {
        val idpClient = buildTestOAuth2Client(idpIssuer, "idp-client-id", executor, clock = clock)
        // The idp client's discovery is skipped via endpoint overrides (see
        // buildTestOAuth2Client), but a Cross App Access target is named by issuer and fetches
        // real discovery by design — stub it once here so every test that reaches redeem() doesn't
        // have to repeat it.
        executor.enqueue("$targetIssuer/.well-known/openid-configuration", 200, discoveryDocumentJson(targetIssuer))
        return CrossAppAccessViewModel(
            idpClient = idpClient,
            config = config,
            resolveTargetCredential = { targetCredential },
            performSignIn = performSignIn
        )
    }

    /**
     * Suspends until [CrossAppAccessViewModel.state] reaches the outcome of the *next* action.
     *
     * Two-phase deliberately: the state *before* calling `signIn()`/`start()`/`exchange()`/`redeem()`
     * again can already be a terminal-looking value (e.g. `IdJagObtained`, ahead of a second
     * `redeem()` call) — matching a "not Working" or "is terminal" predicate immediately with that
     * stale value rather than ever waiting for the new call to run. Waiting for `Working` to appear
     * first proves the new action has actually started before waiting for it to resolve.
     *
     * Hops onto [Dispatchers.Default] with a real-time [withTimeout]: the awaited work runs on the
     * SDK's own compute/IO dispatchers, not the [Dispatchers.Main] `TestDispatcher` installed in
     * [setUp] — a *virtual*-time timeout ambient to `runTest` raced ahead of that real async work
     * in practice, producing a spurious timeout despite the underlying call having already
     * succeeded (confirmed by tracing the actual state transitions while diagnosing this).
     */
    private suspend fun CrossAppAccessViewModel.awaitSettled(): CrossAppAccessState =
        withContext(Dispatchers.Default) {
            withTimeout(20_000) {
                state.first { it is CrossAppAccessState.Working }
                state.first { it !is CrossAppAccessState.Working }
            }
        }

    /**
     * Suspends only until [CrossAppAccessViewModel.state] first reaches [CrossAppAccessState.Working]
     * — the first half of [awaitSettled], exposed on its own for a test that needs to act (e.g.
     * cancel) while an action is genuinely in flight rather than waiting for it to finish.
     *
     * Necessary, not just convenient: `viewModelScope`'s coroutine is launched onto the
     * [StandardTestDispatcher] installed as [Dispatchers.Main] in [setUp], which only actually runs
     * queued work once something suspends through it — calling e.g. `exchange()` and immediately
     * asserting on [CrossAppAccessViewModel.state] observes the *pre-call* value, since the launched
     * coroutine body (including its own transition to `Working`) has not run yet at that point
     * (confirmed by tracing actual execution while diagnosing this). Awaiting `Working` here first
     * is what lets that queued coroutine actually start.
     */
    private suspend fun CrossAppAccessViewModel.awaitWorking() {
        withContext(Dispatchers.Default) {
            withTimeout(20_000) {
                state.first { it is CrossAppAccessState.Working }
            }
        }
    }

    /**
     * Signs in and awaits [CrossAppAccessState.Ready] with a session, then enters a default scope
     * — the common test setup step. Scope is no longer configured statically (it's entered
     * per-call, taking precedence over any target-configured default), so every test that reaches
     * `exchange()`/`start()` needs one entered here first.
     */
    private suspend fun CrossAppAccessViewModel.signInAndAwaitReady(): CrossAppAccessState.Ready {
        signIn(null)
        assertIs<CrossAppAccessState.Ready>(awaitSettled())
        updateScopeInput("chat.read")
        return assertIs<CrossAppAccessState.Ready>(state.value)
    }

    @Test
    fun state_ConfigurationIncomplete_SettlesInNotConfiguredWithNoNetworkRequest() {
        val executor = RecordingApiExecutor()
        val incompleteConfig = CrossAppAccessConfig(null, null, null, null, null, null)
        val viewModel = buildViewModel(executor, config = incompleteConfig, targetCredential = TargetCredential.None)

        assertIs<CrossAppAccessState.NotConfigured>(viewModel.state.value)
        assertTrue(executor.capturedRequests.isEmpty())
    }

    @Test
    fun state_ConfigurationComplete_SettlesInReadyWithIdentityPreselectedAndNoSession() {
        val executor = RecordingApiExecutor()
        val viewModel = buildViewModel(executor)

        val ready = assertIs<CrossAppAccessState.Ready>(viewModel.state.value)
        assertEquals(SubjectKind.IDENTITY, ready.selectedKind)
        assertEquals(false, ready.hasSession)
        assertTrue(executor.capturedRequests.isEmpty())
    }

    @Test
    fun state_ConfigurationComplete_DefaultsScopeInputToBlankNotAHardcodedTestValue() {
        // A default that already "works" against this sample's own test org (formerly a
        // hardcoded "chat.read") would silently fail on every other target — see scopeInput's own
        // KDoc.
        val executor = RecordingApiExecutor()
        val viewModel = buildViewModel(executor)

        val ready = assertIs<CrossAppAccessState.Ready>(viewModel.state.value)
        assertTrue(ready.scopeInput.isEmpty())
    }

    @Test
    fun signIn_Success_TransitionsToReadyWithSession() =
        runTest {
            val executor = RecordingApiExecutor()
            val viewModel = buildViewModel(executor)

            val ready = viewModel.signInAndAwaitReady()

            assertTrue(ready.hasSession)
            assertEquals(listOf(SubjectKind.IDENTITY, SubjectKind.ACCESS, SubjectKind.REFRESH), ready.availableSubjectKinds)
            assertTrue(executor.capturedRequests.isEmpty())
        }

    @Test
    fun signIn_Failure_TransitionsToFailedAttributedToIdentityProvider() =
        runTest {
            val executor = RecordingApiExecutor()
            val viewModel = buildViewModel(executor, performSignIn = signInFailing(IllegalStateException("browser closed")))

            viewModel.signIn(null)
            val failed = assertIs<CrossAppAccessState.Failed>(viewModel.awaitSettled())

            assertEquals(com.okta.directauth.app.util.FailureAttribution.IDENTITY_PROVIDER, failed.attribution)
            assertTrue(failed.serverMessage.contains("browser closed"))
        }

    @Test
    fun exchange_AfterSignIn_TransitionsToResourceTokenObtained() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson(scope = "chat.read"))
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.exchange()
            val settled = viewModel.awaitSettled()

            val result = assertIs<CrossAppAccessState.ResourceTokenObtained>(settled)
            assertEquals("chat.read", result.tokenInfo.scope)
            assertEquals(null, result.idJag)
            assertEquals(1, executor.countTo("$idpIssuer/v1/token"))
            assertEquals(1, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun introspect_AfterExchange_ReturnsActiveAndSendsClientSecret() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson(scope = "chat.read"))
            executor.enqueue("$targetIssuer/v1/introspect", 200, introspectResponseJson(active = true))
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()
            viewModel.exchange()
            viewModel.awaitSettled()

            viewModel.introspect()
            val settled = viewModel.awaitSettled()

            val result = assertIs<CrossAppAccessState.ResourceTokenObtained>(settled)
            assertEquals(true, result.introspection?.active)
            // Proves the auth-foundation fix is wired through end to end: introspectToken() now
            // authenticates with the target's own confidential-client credential.
            val introspectRequest = executor.capturedRequests.first { it.url() == "$targetIssuer/v1/introspect" }
            val form =
                (introspectRequest as com.okta.authfoundation.api.http.ApiFormRequest)
                    .formParameters()
                    .mapValues { it.value.first() }
            assertEquals("target-secret", form["client_secret"])
        }

    @Test
    fun exchange_BlankScopeInput_FailsWithoutIssuingATokenRequest() =
        runTest {
            // Scope is entered per-call now, taking precedence over any target-configured
            // default; the org authorization server rejects a scope-less request, and the SDK
            // enforces this client-side before any token request (discovery for the target
            // client, fetched as part of building the flow, still happens).
            val executor = RecordingApiExecutor()
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()
            viewModel.updateScopeInput("")

            viewModel.exchange()
            val failed = assertIs<CrossAppAccessState.Failed>(viewModel.awaitSettled())

            assertTrue(failed.serverMessage.contains("scope", ignoreCase = true))
            assertEquals(0, executor.countTo("$idpIssuer/v1/token"))
            assertEquals(0, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun exchange_StartingASecondTime_CancelsTheFirstAttempt() =
        runTest {
            val executor = RecordingApiExecutor()
            // Long enough (in real time) that the first attempt is still suspended in its delay
            // when the second one starts and supersedes it — short enough not to make the test slow.
            executor.responseDelayMillis = 300
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson(assertion = "first-attempt"))
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson(assertion = "second-attempt"))
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson())
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.exchange() // first attempt: suspended mid-delay, never reaches the executor
            viewModel.exchange() // second attempt: cancels the first, then proceeds
            val settled = viewModel.awaitSettled()

            assertIs<CrossAppAccessState.ResourceTokenObtained>(settled)
            // Only the superseding attempt's requests were ever recorded — the cancelled attempt
            // never got past its delay to reach the executor at all.
            assertEquals(1, executor.countTo("$idpIssuer/v1/token"))
            assertEquals(1, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun exchange_IssuerAndAuthorizationServerIdBothSet_TargetsCustomServerOnTheDifferentOrg() =
        runTest {
            // Both set names a custom authorization server on the DIFFERENT org named by `issuer`
            // — proof this actually reaches that combined issuer, not the org's default server
            // (targetIssuer alone) and not this app's own org (idpIssuer).
            val executor = RecordingApiExecutor()
            val combinedIssuer = "$targetIssuer/oauth2/customAuthServer"
            executor.enqueue("$combinedIssuer/.well-known/openid-configuration", 200, discoveryDocumentJson(combinedIssuer))
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$combinedIssuer/v1/token", 200, resourceTokenResponseJson(scope = "chat.read"))
            val config =
                CrossAppAccessConfig(
                    idpIssuer = idpIssuer,
                    idpClientId = "idp-client-id",
                    issuer = targetIssuer,
                    authorizationServerId = "customAuthServer",
                    clientId = "idp-client-id",
                    resource = null
                )
            val viewModel = buildViewModel(executor, config = config)
            viewModel.signInAndAwaitReady()

            viewModel.exchange()
            val settled = viewModel.awaitSettled()

            val result = assertIs<CrossAppAccessState.ResourceTokenObtained>(settled)
            assertEquals("chat.read", result.tokenInfo.scope)
            assertEquals(1, executor.countTo("$combinedIssuer/v1/token"))
            assertEquals(0, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun redeem_CalledTwice_IncrementsRedemptionCountWithOnlyOneIdpRequest() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson(expiresIn = 300))
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson(accessToken = "resource-token-1"))
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson(accessToken = "resource-token-2"))
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.start()
            assertIs<CrossAppAccessState.IdJagObtained>(viewModel.awaitSettled())

            viewModel.redeem()
            val firstRedemption = assertIs<CrossAppAccessState.ResourceTokenObtained>(viewModel.awaitSettled())
            assertEquals(1, firstRedemption.redemptionCount)
            assertEquals("resource-token-1", firstRedemption.tokenInfo.accessToken)

            viewModel.redeem()
            val secondRedemption = assertIs<CrossAppAccessState.ResourceTokenObtained>(viewModel.awaitSettled())
            assertEquals(2, secondRedemption.redemptionCount)
            assertEquals("resource-token-2", secondRedemption.tokenInfo.accessToken)

            // The whole point of the ID-JAG being reusable: two redemptions, one identity-provider
            // round trip.
            assertEquals(1, executor.countTo("$idpIssuer/v1/token"))
            assertEquals(2, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun redeem_ExpiredIdJag_FailsWithoutIssuingARequest() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson(expiresIn = 300))
            val clock = MutableClock(epochSecond = 0)
            val viewModel = buildViewModel(executor, clock = clock)
            viewModel.signInAndAwaitReady()

            viewModel.start()
            assertIs<CrossAppAccessState.IdJagObtained>(viewModel.awaitSettled())

            // Advance the clock well past the assertion's 300-second reported lifetime.
            clock.epochSecond = 10_000

            // The expiry check short-circuits synchronously — it never launches a coroutine or
            // enters Working — so unlike the other actions here, there is nothing to await.
            viewModel.redeem()

            val failed = assertIs<CrossAppAccessState.Failed>(viewModel.state.value)
            assertTrue(failed.serverMessage.contains("expired", ignoreCase = true))
            assertEquals(0, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun reset_AfterResourceTokenObtained_ReturnsToReadyAndPreservesXaaSession() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson())
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.exchange()
            assertIs<CrossAppAccessState.ResourceTokenObtained>(viewModel.awaitSettled())

            viewModel.reset()

            // reset() must never discard the dedicated sign-in session — only a fresh sign-in
            // (there being no sign-out for this dedicated session) would.
            val ready = assertIs<CrossAppAccessState.Ready>(viewModel.state.value)
            assertTrue(ready.hasSession)
        }

    @Test
    fun reset_AfterResourceTokenObtained_PreservesTheEnteredScopeInput() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson())
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.exchange()
            assertIs<CrossAppAccessState.ResourceTokenObtained>(viewModel.awaitSettled())

            viewModel.reset()

            // "Try Again" (Failed) and "Start Over" (IdJagObtained) both call this same reset() —
            // neither should throw away scope text the developer already typed. Only a fresh
            // sign-in's own Ready (never reached here) starts blank.
            val ready = assertIs<CrossAppAccessState.Ready>(viewModel.state.value)
            assertEquals("chat.read", ready.scopeInput)
        }

    @Test
    fun reset_AfterIdJagObtained_ReturnsToReadyPreservingSessionAndScopeInput() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.start()
            assertIs<CrossAppAccessState.IdJagObtained>(viewModel.awaitSettled())

            // The screen's "Start Over" button on a successfully obtained ID-JAG calls this exact
            // reset() — the same one "Try Again" uses from a Failed screen.
            viewModel.reset()

            val ready = assertIs<CrossAppAccessState.Ready>(viewModel.state.value)
            assertTrue(ready.hasSession)
            assertEquals("chat.read", ready.scopeInput)
        }

    @Test
    fun exchange_CancelledByReset_NeverOverwritesTheResetStateWithAStaleOutcome() =
        runTest {
            val executor = RecordingApiExecutor()
            // Long enough (in real time) that the exchange is still suspended in its delay when
            // reset() below cancels it, once awaitWorking() below has proven it actually started.
            executor.responseDelayMillis = 300
            executor.enqueue("$idpIssuer/v1/token", 400, errorResponseJson("invalid_grant", "should never be observed"))
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.exchange()
            viewModel.awaitWorking() // proves the attempt has actually started before cancelling it
            viewModel.reset()
            assertIs<CrossAppAccessState.Ready>(viewModel.state.value)

            // Give the abandoned attempt's delay time to elapse for real. CrossAppAccessFlowImpl's
            // start()/redeem() each run through their own runCatching internally, which also
            // catches CancellationException — without an explicit rethrow, the cancelled
            // continuation resumes normally and its onFailure branch clobbers the state reset()
            // already produced (reproduced while diagnosing this: the abandoned attempt settled on
            // Failed with "StandaloneCoroutine was cancelled" as its server message).
            withContext(Dispatchers.Default) { delay(1500) }
            assertIs<CrossAppAccessState.Ready>(viewModel.state.value)
            // The cancelled attempt never got past its delay to reach the executor at all.
            assertEquals(0, executor.countTo("$idpIssuer/v1/token"))
        }

    @Test
    fun redeem_FailsAfterObtainingIdJag_RetainsTheIdJagForAnotherAttemptWithoutANewIdpRoundTrip() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson(expiresIn = 300))
            executor.enqueue("$targetIssuer/v1/token", 400, errorResponseJson("invalid_grant", "resource app rejected it"))
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson(accessToken = "resource-token-retry"))
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()

            viewModel.start()
            val idJagObtained = assertIs<CrossAppAccessState.IdJagObtained>(viewModel.awaitSettled())

            viewModel.redeem()
            val failed = assertIs<CrossAppAccessState.Failed>(viewModel.awaitSettled())
            assertEquals(idJagObtained.idJag.value, failed.retainedIdJag?.value)

            // Retrying reuses the retained ID-JAG directly — no second identity-provider round trip.
            viewModel.redeem()
            val resourceToken = assertIs<CrossAppAccessState.ResourceTokenObtained>(viewModel.awaitSettled())
            assertEquals("resource-token-retry", resourceToken.tokenInfo.accessToken)
            assertEquals(1, executor.countTo("$idpIssuer/v1/token"))
            assertEquals(2, executor.countTo("$targetIssuer/v1/token"))
        }

    @Test
    fun redeem_ExpiredIdJag_DoesNotRetainItSinceItIsAlreadyUnusable() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson(expiresIn = 300))
            val clock = MutableClock(epochSecond = 0)
            val viewModel = buildViewModel(executor, clock = clock)
            viewModel.signInAndAwaitReady()

            viewModel.start()
            assertIs<CrossAppAccessState.IdJagObtained>(viewModel.awaitSettled())
            clock.epochSecond = 10_000

            viewModel.redeem()

            val failed = assertIs<CrossAppAccessState.Failed>(viewModel.state.value)
            assertNull(failed.retainedIdJag)
        }

    @Test
    fun introspect_Failure_AttributesToResourceServerRatherThanConfiguration() =
        runTest {
            val executor = RecordingApiExecutor()
            executor.enqueue("$idpIssuer/v1/token", 200, idJagResponseJson())
            executor.enqueue("$targetIssuer/v1/token", 200, resourceTokenResponseJson(scope = "chat.read"))
            // No stubbed response for the introspection endpoint — RecordingApiExecutor fails it
            // with a plain IllegalStateException, never a CrossAppAccessException, exactly like a
            // genuine transport failure would.
            val viewModel = buildViewModel(executor)
            viewModel.signInAndAwaitReady()
            viewModel.exchange()
            viewModel.awaitSettled()

            viewModel.introspect()
            val failed = assertIs<CrossAppAccessState.Failed>(viewModel.awaitSettled())

            assertEquals(com.okta.directauth.app.util.FailureAttribution.RESOURCE_SERVER, failed.attribution)
        }
}
