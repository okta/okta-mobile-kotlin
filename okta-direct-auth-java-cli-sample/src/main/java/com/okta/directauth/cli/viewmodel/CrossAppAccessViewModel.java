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
package com.okta.directauth.cli.viewmodel;

import com.okta.authfoundation.client.OidcClock;
import com.okta.authfoundation.client.TokenInfo;
import com.okta.authfoundation.client.dto.IntrospectInfo;
import com.okta.directauth.cli.CliLogger;
import com.okta.directauth.cli.model.CrossAppAccessConfig;
import com.okta.directauth.cli.model.CrossAppAccessFailure;
import com.okta.directauth.cli.model.IdJagDisplay;
import com.okta.directauth.cli.model.OAuth2Screen;
import com.okta.directauth.cli.model.SubjectKind;
import com.okta.directauth.cli.model.TokenDisplay;
import com.okta.directauth.cli.oauth2.CrossAppAccessFlows;
import com.okta.directauth.cli.oauth2.CrossAppAccessIdpFlow;
import com.okta.oauth2.kmp.IdJagAssertion;
import com.okta.oauth2.kmp.SubjectAssertion;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestration ViewModel for the Cross App Access demonstration — both the one-action exchange and
 * the step-by-step mode that exposes the intermediate ID-JAG for inspection and reuse.
 *
 * <p>Cross App Access owns its own signed-in session, established by {@link #signIn}, entirely
 * separate from the rest of this CLI's session: the ID-JAG exchange must be submitted to the org's
 * own authorization server, never a custom one, so it cannot safely reuse a session some other flow
 * may have established against a custom {@code authorizationServerId}.
 *
 * <p>Delegates to the {@link CrossAppAccessFlows} seam (production: {@link
 * com.okta.directauth.cli.oauth2.WrapperCrossAppAccessFlows}, tests: a fake) and the {@link
 * CrossAppAccessIdpFlow} seam for the dedicated sign-in, and notifies registered {@link
 * CrossAppAccessViewModelListener}s of state changes. All flow methods are non-blocking; results
 * arrive via listener callbacks. Mirrors {@link OAuth2ViewModel}'s shape so the two are easy to
 * compare side by side.
 */
public final class CrossAppAccessViewModel implements Closeable {
  private static final String TAG = "CrossAppAccessViewModel";

  private final CrossAppAccessFlows flows;
  private final CrossAppAccessIdpFlow idpFlow;
  private final CrossAppAccessConfig config;
  private final boolean hasTargetCredential;
  private final OidcClock clock;
  private final List<CrossAppAccessViewModelListener> listeners = new CopyOnWriteArrayList<>();
  private final AtomicInteger redemptionCount = new AtomicInteger(0);

  private volatile OAuth2Screen currentScreen = OAuth2Screen.CROSS_APP_ACCESS_MENU;
  private volatile SubjectKind selectedKind = SubjectKind.IDENTITY;
  // Must be a custom, resource-specific scope defined on the target's authorization server (e.g.
  // "chat.read") — not a standard OIDC scope like "openid"/"profile". This scope is requested at
  // the ID-JAG exchange itself, for the target resource, not for the identity provider; the org
  // authorization server rejects standard OIDC scopes here with "The following scopes are not
  // allowed for this request".
  private volatile List<String> selectedScope = List.of("chat.read");
  private volatile TokenInfo xaaSession;
  private volatile IdJagAssertion lastIdJag;
  private volatile IdJagDisplay lastIdJagDisplay;
  private volatile TokenDisplay lastTokenDisplay;
  private volatile Boolean introspectionActive;
  private volatile CompletableFuture<?> pendingFuture;

  /**
   * Creates a new CrossAppAccessViewModel.
   *
   * @param flows the Cross App Access flow seam to use
   * @param idpFlow the dedicated Cross App Access sign-in seam, or {@code null} if {@code
   *     xaaIdpIssuer}/{@code xaaIdpClientId} are not configured — {@link #signIn} then reports an
   *     error instead of attempting anything, which {@link #validateConfig} also prevents the
   *     console view from ever reaching in the first place
   * @param config the resource app target (and IdP identity) configuration
   * @param hasTargetCredential whether a target credential was found in local.properties, per
   *     {@code ClientAuthentication.hasTargetCredential} — passed as a boolean, never the
   *     credential value itself, so this class cannot leak it even by accident. There is no
   *     equivalent IdP-credential parameter: unlike the target, the IdP client's credential is
   *     never required — see {@link CrossAppAccessConfig#validate}
   * @param clock the IdP client's clock, used to evaluate ID-JAG expiry with no network request
   */
  public CrossAppAccessViewModel(
      CrossAppAccessFlows flows,
      CrossAppAccessIdpFlow idpFlow,
      CrossAppAccessConfig config,
      boolean hasTargetCredential,
      OidcClock clock) {
    this.flows = flows;
    this.idpFlow = idpFlow;
    this.config = config;
    this.hasTargetCredential = hasTargetCredential;
    this.clock = clock;
  }

  /**
   * Validates the resource app target and IdP identity configuration.
   *
   * @return the validation outcome
   */
  public CrossAppAccessConfig.Validation validateConfig() {
    return config.validate(hasTargetCredential, idpFlow != null);
  }

  /**
   * Adds a listener for state change notifications.
   *
   * @param listener the listener to add
   */
  public void addListener(CrossAppAccessViewModelListener listener) {
    listeners.add(listener);
  }

  /**
   * Returns the resource app target (and IdP identity) configuration.
   *
   * @return the configuration
   */
  public CrossAppAccessConfig getConfig() {
    return config;
  }

  /**
   * Returns the current Cross App Access navigation screen.
   *
   * @return current screen
   */
  public OAuth2Screen getCurrentScreen() {
    return currentScreen;
  }

  /**
   * Returns the currently selected subject kind. Defaults to {@link SubjectKind#IDENTITY}.
   *
   * @return the selected kind
   */
  public SubjectKind getSelectedKind() {
    return selectedKind;
  }

  /**
   * Returns the ID-JAG display from the last successful first step, or null.
   *
   * @return last ID-JAG display, or null
   */
  public IdJagDisplay getLastIdJagDisplay() {
    return lastIdJagDisplay;
  }

  /**
   * Returns the resource token display from the last successful exchange or redemption, or null.
   *
   * @return last token display, or null
   */
  public TokenDisplay getLastTokenDisplay() {
    return lastTokenDisplay;
  }

  /**
   * Returns the result of introspecting {@link #getLastTokenDisplay}'s access token, once the
   * developer has asked for it via {@link #introspect} — {@code null} until then.
   *
   * @return whether the token is active, or null if not yet introspected
   */
  public Boolean getIntrospectionActive() {
    return introspectionActive;
  }

  /**
   * Whether the dedicated Cross App Access sign-in has produced a session. There is no pasted-token
   * fallback — {@link #signIn} is the only way to obtain one.
   *
   * @return true if a session is available
   */
  public boolean hasSession() {
    return xaaSession != null;
  }

  /**
   * Whether {@code kind} is available from the dedicated session. Always false if there is no
   * session.
   *
   * @param kind the subject kind to check
   * @return true if the session can supply this kind
   */
  public boolean isKindAvailable(SubjectKind kind) {
    TokenInfo session = xaaSession;
    return session != null && kind.availableIn(session);
  }

  /**
   * Updates which subject kind is selected.
   *
   * @param kind the kind to select
   */
  public void selectSubjectKind(SubjectKind kind) {
    this.selectedKind = kind;
  }

  /**
   * Updates the requested scope, taking precedence over any scope configured on the target itself —
   * the IdP authorization server rejects a scope-less request for this grant, so this (or a
   * target-configured default) must be non-empty before {@link #exchange}/{@link #start}.
   *
   * @param scope the scope to request
   */
  public void selectScope(List<String> scope) {
    this.selectedScope = List.copyOf(scope);
  }

  /**
   * Performs the dedicated Cross App Access sign-in (Authorization Code + PKCE), separate from
   * every other flow's session (non-blocking).
   *
   * <p>Clears any previous {@link #xaaSession} and ID-JAG/resource-token state up front, before
   * starting the new browser flow — so a failed re-authentication cannot leave {@link #exchange}/
   * {@link #start} still acting under a stale, previously signed-in identity.
   */
  public void signIn() {
    if (idpFlow == null) {
      notifyError("Cross App Access IdP is not configured.");
      return;
    }
    CliLogger.info(TAG, "Starting Cross App Access dedicated sign-in");
    xaaSession = null;
    lastIdJag = null;
    lastIdJagDisplay = null;
    lastTokenDisplay = null;
    introspectionActive = null;
    redemptionCount.set(0);
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_SIGN_IN_WAITING);
    pendingFuture =
        idpFlow
            .browserSignIn()
            .thenAccept(this::handleSignInResult)
            .exceptionally(
                t -> {
                  handleSignInError(t);
                  return null;
                });
  }

  /** One-action mode: obtains an ID-JAG and redeems it in a single call (non-blocking). */
  public void exchange() {
    SubjectAssertion subject = resolveSubjectAssertion();
    if (subject == null) {
      notifyError("No subject available. Sign in first.");
      return;
    }
    CliLogger.info(
        TAG, "Starting Cross App Access one-action exchange (subject kind: " + selectedKind + ")");
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_WORKING);
    pendingFuture =
        flows
            .exchange(subject, selectedScope)
            .thenAccept(this::handleExchangeResult)
            .exceptionally(
                t -> {
                  handleError(t);
                  return null;
                });
  }

  /** Step-by-step mode: obtains the ID-JAG only, without redeeming it (non-blocking). */
  public void start() {
    SubjectAssertion subject = resolveSubjectAssertion();
    if (subject == null) {
      notifyError("No subject available. Sign in first.");
      return;
    }
    CliLogger.info(
        TAG,
        "Starting Cross App Access step-by-step exchange (subject kind: " + selectedKind + ")");
    redemptionCount.set(0);
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_WORKING);
    pendingFuture =
        flows
            .start(subject, selectedScope)
            .thenAccept(this::handleIdJagResult)
            .exceptionally(
                t -> {
                  handleError(t);
                  return null;
                });
  }

  /**
   * Redeems the currently held ID-JAG for a resource access token (non-blocking). Callable
   * repeatedly while it remains unexpired — each call makes no identity-provider request.
   */
  public void redeem() {
    IdJagAssertion idJag = lastIdJag;
    if (idJag == null) {
      notifyError("No ID-JAG to redeem. Run the first step first.");
      return;
    }
    if (idJag.isExpired(clock)) {
      CliLogger.info(TAG, "ID-JAG has expired; not issuing a redemption request");
      notifyError("This ID-JAG has expired. Run the first step again to obtain a new one.");
      return;
    }
    CliLogger.info(
        TAG, "Redeeming Cross App Access ID-JAG (redemption #" + (redemptionCount.get() + 1) + ")");
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_WORKING);
    pendingFuture =
        flows
            .redeem(idJag)
            .thenAccept(this::handleRedemptionResult)
            .exceptionally(
                t -> {
                  handleError(t);
                  return null;
                });
  }

  /**
   * Checks {@link #getLastTokenDisplay}'s access token with the resource authorization server per
   * <a href="https://datatracker.ietf.org/doc/html/rfc7662">RFC 7662</a> (non-blocking) — proves
   * the token is actually accepted server-side, which a successful {@link #exchange}/{@link
   * #redeem} alone does not (that only proves the resource authorization server <em>issued</em>
   * it).
   */
  public void introspect() {
    TokenDisplay token = lastTokenDisplay;
    if (token == null) {
      notifyError("No resource token to introspect. Run an exchange first.");
      return;
    }
    CliLogger.info(TAG, "Introspecting Cross App Access resource token");
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_WORKING);
    pendingFuture =
        flows
            .introspectResourceToken(token.getAccessToken())
            .thenAccept(this::handleIntrospectResult)
            .exceptionally(
                t -> {
                  handleError(t);
                  return null;
                });
  }

  /**
   * Abandons any pending exchange and returns to the Cross App Access menu. Never clears {@link
   * #xaaSession} — an aborted exchange shouldn't force signing in again; only a fresh {@link
   * #signIn} replaces it.
   *
   * <p>"Abandons", not "cancels": {@link CompletableFuture#cancel} only marks the tail stage
   * returned by the {@code thenAccept}/{@code exceptionally} chain as cancelled — it does not stop
   * the underlying flow call in progress, which keeps running and will still invoke {@code
   * handle*Result}/{@code handleError} (writing state and notifying listeners) once it completes.
   * Only {@link #close} actually stops in-flight work, by cancelling the flow's coroutine scope.
   */
  public void reset() {
    CompletableFuture<?> pending = pendingFuture;
    if (pending != null) {
      pending.cancel(true);
    }
    pendingFuture = null;
    lastIdJag = null;
    lastIdJagDisplay = null;
    lastTokenDisplay = null;
    introspectionActive = null;
    redemptionCount.set(0);
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_MENU);
  }

  @Override
  public void close() {
    reset();
    flows.close();
    if (idpFlow != null) {
      idpFlow.close();
    }
  }

  private SubjectAssertion resolveSubjectAssertion() {
    TokenInfo session = xaaSession;
    if (session == null || !selectedKind.availableIn(session)) {
      return null;
    }
    return selectedKind.toSubjectAssertion(sessionTokenValue(session, selectedKind));
  }

  private static String sessionTokenValue(TokenInfo token, SubjectKind kind) {
    switch (kind) {
      case IDENTITY:
        return token.getIdToken();
      case ACCESS:
        return token.getAccessToken();
      case REFRESH:
        return token.getRefreshToken();
      default:
        throw new IllegalStateException("Unknown SubjectKind: " + kind);
    }
  }

  private void handleSignInResult(TokenInfo tokenInfo) {
    CliLogger.info(TAG, "Cross App Access sign-in succeeded");
    xaaSession = tokenInfo;
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_SIGNED_IN);
  }

  private void handleSignInError(Throwable t) {
    Throwable cause = t;
    while (cause.getCause() != null && cause != cause.getCause()) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    String description = message != null ? message : cause.getClass().getSimpleName();
    CliLogger.error(TAG, "Cross App Access sign-in failed: " + description, t);
    notifyError("The identity provider rejected the sign-in: " + description);
  }

  private void handleExchangeResult(TokenInfo tokenInfo) {
    CliLogger.info(TAG, "Cross App Access exchange succeeded");
    lastTokenDisplay = TokenDisplay.fromTokenInfo(tokenInfo);
    // A new token has never been introspected — without this, redeeming again after introspecting
    // would attribute the previous token's "Active" verdict to this brand-new one.
    introspectionActive = null;
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_RESULT);
    for (CrossAppAccessViewModelListener listener : listeners) {
      listener.onResourceToken(lastTokenDisplay);
    }
  }

  private void handleIdJagResult(IdJagAssertion idJag) {
    CliLogger.info(TAG, "Cross App Access ID-JAG obtained (audience: " + idJag.getAudience() + ")");
    lastIdJag = idJag;
    lastIdJagDisplay = IdJagDisplay.fromIdJagAssertion(idJag, 0);
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_ID_JAG);
    for (CrossAppAccessViewModelListener listener : listeners) {
      listener.onIdJag(lastIdJagDisplay);
    }
  }

  private void handleRedemptionResult(TokenInfo tokenInfo) {
    int count = redemptionCount.incrementAndGet();
    CliLogger.info(TAG, "Cross App Access redemption succeeded (redemption #" + count + ")");
    lastIdJagDisplay = IdJagDisplay.fromIdJagAssertion(lastIdJag, count);
    lastTokenDisplay = TokenDisplay.fromTokenInfo(tokenInfo);
    // See handleExchangeResult: this is a new token too, never yet introspected.
    introspectionActive = null;
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_RESULT);
    for (CrossAppAccessViewModelListener listener : listeners) {
      listener.onResourceToken(lastTokenDisplay);
    }
  }

  private void handleIntrospectResult(IntrospectInfo info) {
    CliLogger.info(
        TAG, "Cross App Access introspection succeeded (active: " + info.getActive() + ")");
    introspectionActive = info.getActive();
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_RESULT);
    // Not onResourceToken: no new resource token was obtained, only a verdict on the existing one.
    for (CrossAppAccessViewModelListener listener : listeners) {
      listener.onIntrospection(introspectionActive);
    }
  }

  private void handleError(Throwable t) {
    // Unwrap CompletableFuture wrappers (e.g. CompletionException) is handled inside classify()
    // itself, which scans the full cause chain for CrossAppAccessException before ever walking to
    // a root cause — unlike OAuth2ViewModel.handleError, which walks to root first and would
    // discard this attribution.
    CrossAppAccessFailure failure = CrossAppAccessFailure.classify(t, selectedKind);
    CliLogger.error(TAG, "Cross App Access failed: " + failure.getMessage(), t);
    notifyError(failure.getMessage());
  }

  private void setScreen(OAuth2Screen screen) {
    CliLogger.debug(TAG, "Screen: " + screen);
    this.currentScreen = screen;
    for (CrossAppAccessViewModelListener listener : listeners) {
      listener.onScreenChanged(screen);
    }
  }

  /**
   * Single source of truth for the error screen transition and error callbacks.
   *
   * <p>Callers must NOT call {@link #setScreen(OAuth2Screen)} with the error screen separately.
   *
   * <p>Notifies {@code onError} before {@link #setScreen}, not after: {@code setScreen}'s own
   * {@code onScreenChanged(CROSS_APP_ACCESS_ERROR)} callback is what releases {@code
   * OAuth2ConsoleView}'s waiting latch, so if the screen changed first, the console thread could
   * wake up and read {@code lastErrorMessage} before this method's {@code onError} call below ever
   * stored it.
   */
  private void notifyError(String message) {
    for (CrossAppAccessViewModelListener listener : listeners) {
      listener.onError(message);
    }
    setScreen(OAuth2Screen.CROSS_APP_ACCESS_ERROR);
  }
}
