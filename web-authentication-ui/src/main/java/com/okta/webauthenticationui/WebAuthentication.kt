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
package com.okta.webauthenticationui

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.okta.authfoundation.client.ApplicationContextHolder
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.events.EventCoordinator
import com.okta.oauth2.kmp.AuthorizationCodeFlow
import com.okta.oauth2.kmp.AuthorizationCodeFlowContext
import com.okta.oauth2.kmp.RedirectEndSessionFlow
import com.okta.webauthenticationui.BuildConfig.SDK_VERSION
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.okta.authfoundation.client.OAuth2Client as AndroidOAuth2Client

@Deprecated(
    message = "Renamed to WebAuthentication",
    replaceWith = ReplaceWith("WebAuthentication")
)
typealias WebAuthenticationClient = WebAuthentication

/**
 * Authentication coordinator that simplifies signing users in using browser-based OIDC authentication flows.
 *
 * This simple class encapsulates the details of launching the browser, and coordinating with OAuth2 endpoints.
 *
 * To customize the flow, please read more about customization options: [WebAuthenticationProvider], [DefaultWebAuthenticationProvider].
 * To further customize the authentication flow, please read more about the underlying flows: [AuthorizationCodeFlow],
 * [RedirectEndSessionFlow].
 */
class WebAuthentication private constructor(
    private val authorizationCodeFlowFactory: () -> AuthorizationCodeFlow,
    private val redirectEndSessionFlowFactory: () -> RedirectEndSessionFlow,
    private val defaultScopeValue: List<String>,
    private val webAuthenticationProvider: WebAuthenticationProvider,
    private val pendingLoginPersistence: PendingLoginPersistence = PendingLoginPersistence.Enabled(),
) {
    companion object {
        init {
            SdkVersionsRegistry.register(SDK_VERSION)
        }
    }

    /**
     * Initializes a web authentication client backed by a KMP [OAuth2Client].
     *
     * This is the preferred constructor. The KMP client is used directly to create the underlying
     * [AuthorizationCodeFlow] and [RedirectEndSessionFlow].
     *
     * @param oAuth2Client the KMP [OAuth2Client] to use for authorization requests.
     * @param webAuthenticationProvider the [WebAuthenticationProvider] which will be used to show
     * the UI when performing the redirect flows.
     */
    constructor(
        oAuth2Client: OAuth2Client,
        webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(EventCoordinator(emptyList())),
    ) : this(
        authorizationCodeFlowFactory = { AuthorizationCodeFlow(oAuth2Client) },
        redirectEndSessionFlowFactory = { RedirectEndSessionFlow(oAuth2Client) },
        defaultScopeValue = oAuth2Client.configuration.defaultScope,
        webAuthenticationProvider = webAuthenticationProvider
    )

    /**
     * Creates a [WebAuthentication] with explicit control over the login state persisted for [resumeLogin].
     *
     * @param oAuth2Client the client used for the authorization code and logout flows.
     * @param webAuthenticationProvider the provider used to display the browser.
     * @param pendingLoginPersistence whether, and how, to persist the in-progress login state, which includes
     *  the PKCE `code_verifier`. Use [PendingLoginPersistence.Disabled] to never write it to disk, or
     *  [PendingLoginPersistence.Enabled] with a [PendingLoginEncryptor] to encrypt it. Defaults to enabled
     *  and unencrypted.
     */
    constructor(
        oAuth2Client: OAuth2Client,
        webAuthenticationProvider: WebAuthenticationProvider,
        pendingLoginPersistence: PendingLoginPersistence,
    ) : this(
        authorizationCodeFlowFactory = { AuthorizationCodeFlow(oAuth2Client) },
        redirectEndSessionFlowFactory = { RedirectEndSessionFlow(oAuth2Client) },
        defaultScopeValue = oAuth2Client.configuration.defaultScope,
        webAuthenticationProvider = webAuthenticationProvider,
        pendingLoginPersistence = pendingLoginPersistence
    )

    /**
     * Initializes a web authentication client.
     *
     * @param webAuthenticationProvider the [WebAuthenticationProvider] which will be used to show the UI when performing the
     * redirect flows.
     */
    @Deprecated(
        message = "Use the constructor taking com.okta.authfoundation.client.kmp.OAuth2Client instead.",
        replaceWith =
            ReplaceWith(
                "WebAuthentication(kmpClient, webAuthenticationProvider)",
                "com.okta.authfoundation.client.kmp.OAuth2Client"
            )
    )
    @Suppress("DEPRECATION")
    constructor(
        webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(OidcConfiguration.default.eventCoordinator),
    ) : this(AndroidOAuth2Client.default, webAuthenticationProvider)

    /**
     * Initializes a web authentication client.
     *
     * @param oidcConfiguration the [OidcConfiguration] specifying the authorization servers.
     * @param webAuthenticationProvider the [WebAuthenticationProvider] which will be used to show the UI when performing the
     * redirect flows.
     */
    @Deprecated(
        message = "Use the constructor taking com.okta.authfoundation.client.kmp.OAuth2Client instead.",
        replaceWith =
            ReplaceWith(
                "WebAuthentication(kmpClient, webAuthenticationProvider)",
                "com.okta.authfoundation.client.kmp.OAuth2Client"
            )
    )
    @Suppress("DEPRECATION")
    constructor(
        oidcConfiguration: OidcConfiguration,
        webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(oidcConfiguration.eventCoordinator),
    ) : this(AndroidOAuth2Client.createFromConfiguration(oidcConfiguration), webAuthenticationProvider)

    /**
     * Initializes a web authentication client backed by an Android-only [OAuth2Client].
     *
     * @param client the Android [OAuth2Client] to use for authorization requests.
     * @param webAuthenticationProvider the [WebAuthenticationProvider] which will be used to show the UI when performing the
     * redirect flows.
     */
    @Deprecated(
        message = "Use the constructor taking com.okta.authfoundation.client.kmp.OAuth2Client instead.",
        replaceWith =
            ReplaceWith(
                "WebAuthentication(kmpClient, webAuthenticationProvider)",
                "com.okta.authfoundation.client.kmp.OAuth2Client"
            )
    )
    constructor(
        client: AndroidOAuth2Client,
        webAuthenticationProvider: WebAuthenticationProvider = DefaultWebAuthenticationProvider(client.configuration.eventCoordinator),
    ) : this(
        authorizationCodeFlowFactory = { AuthorizationCodeFlow(client) },
        redirectEndSessionFlowFactory = { RedirectEndSessionFlow(client) },
        defaultScopeValue =
            client.configuration.defaultScope
                .split(" ")
                .filter { it.isNotBlank() },
        webAuthenticationProvider = webAuthenticationProvider
    )

    private val flowLock = Any()

    @Volatile
    private var authorizationCodeFlowBacking: AuthorizationCodeFlow? = null

    @VisibleForTesting
    internal var authorizationCodeFlow: AuthorizationCodeFlow
        get() =
            authorizationCodeFlowBacking ?: synchronized(flowLock) {
                authorizationCodeFlowBacking ?: authorizationCodeFlowFactory().also { authorizationCodeFlowBacking = it }
            }
        set(value) = synchronized(flowLock) { authorizationCodeFlowBacking = value }

    @Volatile
    private var redirectEndSessionFlowBacking: RedirectEndSessionFlow? = null

    @VisibleForTesting
    internal var redirectEndSessionFlow: RedirectEndSessionFlow
        get() =
            redirectEndSessionFlowBacking ?: synchronized(flowLock) {
                redirectEndSessionFlowBacking ?: redirectEndSessionFlowFactory().also { redirectEndSessionFlowBacking = it }
            }
        set(value) = synchronized(flowLock) { redirectEndSessionFlowBacking = value }

    @VisibleForTesting
    internal var redirectCoordinator: RedirectCoordinator = SingletonRedirectCoordinator

    /**
     * Surfaced as the failure cause of a [login] or [logoutOfBrowser] call — inside a failed [Result]
     * for the [List]-scope [login] overload, or as [OAuth2ClientResult.Error.exception] for
     * [logoutOfBrowser] and the deprecated [String]-scope [login] overload.
     *
     * Indicates the requested flow was cancelled.
     */
    class FlowCancelledException internal constructor() : Exception("Flow cancelled.")

    /**
     * Surfaced as the failure cause of a [login] or [logoutOfBrowser] call — inside a failed [Result]
     * for the [List]-scope [login] overload, or as [OAuth2ClientResult.Error.exception] for
     * [logoutOfBrowser] and the deprecated [String]-scope [login] overload.
     *
     * Indicates that [login] or [logoutOfBrowser] was called while another flow was already in
     * progress. Only one redirect flow can be active at a time.
     */
    class FlowAlreadyInProgressException internal constructor() : Exception("Another flow is already in progress.")

    /**
     * Initiates the OIDC Authorization Code redirect flow. Suspends until the browser redirect
     * completes or the user cancels. Preferred over the [String]-scope overload.
     *
     * @param context the Android [Activity] [Context] which is used to display the login flow via the configured
     * [WebAuthenticationProvider].
     * @param redirectUrl the redirect URL.
     * @param scope the scopes to request during sign in.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @return a [Result] containing [TokenInfo] on success, or a failed [Result] wrapping a
     *  [FlowCancelledException] if the user dismissed the browser, a [FlowAlreadyInProgressException]
     *  if another redirect flow was already in progress, or the underlying authorize/token exchange error.
     */
    suspend fun login(
        context: Context,
        redirectUrl: String,
        scope: List<String>,
        extraRequestParameters: Map<String, String> = emptyMap(),
    ): Result<TokenInfo> {
        val pendingLoginStore = pendingLoginStore(context)
        val initializationResult =
            redirectCoordinator.initialize(webAuthenticationProvider, context, redirectUrl) {
                // Runs only once this login owns the coordinator. Drop any earlier pending login first so a
                // failed start or save can never leave a stale entry behind, then persist the new state before
                // the browser opens, so it is on disk by the time the process could be killed.
                pendingLoginStore?.clear()
                authorizationCodeFlow
                    .start(redirectUrl, scope, extraRequestParameters)
                    .mapCatching { flowContext ->
                        pendingLoginStore?.save(flowContext)
                        RedirectInitializationResult.Success(flowContext.url.toHttpUrl(), flowContext)
                    }.getOrElse { e ->
                        RedirectInitializationResult.Error(e.asException())
                    }
            }

        val flowContext =
            when (initializationResult) {
                is RedirectInitializationResult.Error -> return Result.failure(initializationResult.exception)
                is RedirectInitializationResult.Success -> initializationResult.flowContext
            }

        val redirectResult = redirectCoordinator.listenForResult()
        // Reached only if the wait was not cancelled, so an interrupted login stays resumable.
        pendingLoginStore?.clear()
        return finishLogin(redirectResult, flowContext)
    }

    /**
     * Completes a [login] that was interrupted because the app process was killed (or the calling
     * coroutine was cancelled) while the user was in the browser.
     *
     * Call this once the app has restarted, for example from `Application.onCreate()` or the launching
     * Activity, using a lifecycle-aware scope. If a login was interrupted, this suspends until the
     * browser redirect arrives and then performs the token exchange. Cancelling the calling coroutine
     * leaves the pending login in place so it can be resumed again.
     *
     * Pending logins are kept in app-private storage for 10 minutes; if no redirect arrives in that
     * time (for example the user closed the browser without finishing), this returns `null`. Starting a
     * new [login] discards any pending login.
     *
     * Always returns `null` if persistence was turned off with [PendingLoginPersistence.Disabled].
     *
     * Returns `null` immediately, leaving the pending login untouched, if a [login], [logoutOfBrowser]
     * or another [resumeLogin] is already running in this process, since that call owns the redirect.
     *
     * @param context the [Context] used to read the pending login. Defaults to the application context
     *  captured via [com.okta.authfoundation.AuthFoundation.initializeAndroidContext].
     * @return `null` if there is no login to resume. Otherwise a [Result] containing [TokenInfo] on
     *  success, or a failed [Result] wrapping the underlying token exchange or authorization error.
     *  Dismissing the browser after the process was killed is not reported as a
     *  [FlowCancelledException]; it surfaces as `null` once the pending login expires.
     */
    suspend fun resumeLogin(context: Context = ApplicationContextHolder.appContext): Result<TokenInfo>? {
        val pendingLoginStore = pendingLoginStore(context) ?: return null
        val pending = pendingLoginStore.load() ?: return null

        // The redirect cannot arrive after the entry expires, so don't wait longer than that.
        var anotherFlowRunning = false
        val redirectResult =
            withTimeoutOrNull(pending.remainingMillis) {
                redirectCoordinator.listenForResumedResult().also { anotherFlowRunning = it == null }
            }
        if (redirectResult == null) {
            // A flow already running in this process owns the pending login, so leave it. Otherwise it expired.
            if (!anotherFlowRunning) pendingLoginStore.clear()
            return null
        }
        pendingLoginStore.clear()
        return finishLogin(redirectResult, pending.flowContext)
    }

    private fun pendingLoginStore(context: Context): PendingLoginStore? =
        when (pendingLoginPersistence) {
            is PendingLoginPersistence.Disabled -> null
            is PendingLoginPersistence.Enabled -> PendingLoginStore(context, pendingLoginPersistence.encryptor)
        }

    private suspend fun finishLogin(
        redirectResult: RedirectResult,
        flowContext: AuthorizationCodeFlowContext,
    ): Result<TokenInfo> {
        val uri =
            when (redirectResult) {
                is RedirectResult.Error -> return Result.failure(redirectResult.exception)
                is RedirectResult.Redirect -> redirectResult.uri
            }

        return authorizationCodeFlow.resume(uri.toString(), flowContext)
    }

    /**
     * Initiates the OIDC Authorization Code redirect flow using the application context captured
     * via [com.okta.authfoundation.AuthFoundation.initializeAndroidContext]. Suspends until the
     * browser redirect completes or the user cancels.
     *
     * Prefer the [Context]-accepting overload when an [Activity] [Context] is available (e.g. from
     * a UI interaction). Use this overload when one isn't, such as when initiating login from
     * application startup/bootstrapping code. [com.okta.authfoundation.AuthFoundation.initializeAndroidContext]
     * must have already been called, typically from `Application.onCreate()`.
     *
     * @param redirectUrl the redirect URL.
     * @param scope the scopes to request during sign in.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @return a [Result] containing [TokenInfo] on success, or a failed [Result] wrapping a
     *  [FlowCancelledException] if the user dismissed the browser, a [FlowAlreadyInProgressException]
     *  if another redirect flow was already in progress, or the underlying authorize/token exchange error.
     */
    suspend fun login(
        redirectUrl: String,
        scope: List<String>,
        extraRequestParameters: Map<String, String> = emptyMap(),
    ): Result<TokenInfo> = login(ApplicationContextHolder.appContext, redirectUrl, scope, extraRequestParameters)

    /**
     * Initiates the OIDC Authorization Code redirect flow.
     *
     * @param context the Android [Activity] [Context] which is used to display the login flow via the configured
     * [WebAuthenticationProvider].
     * @param redirectUrl the redirect URL.
     * @param extraRequestParameters the extra key value pairs to send to the authorize endpoint.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @param scope the scopes to request during sign in. Defaults to the configured client's default scope.
     * @return an [OAuth2ClientResult]: [OAuth2ClientResult.Success] wrapping the [Token] on success, or
     *  [OAuth2ClientResult.Error] wrapping a [FlowCancelledException] if the user dismissed the browser, a
     *  [FlowAlreadyInProgressException] if another redirect flow was already in progress, or the underlying
     *  authorize/token exchange error.
     */
    @Deprecated(
        message =
            "Use the overload accepting scope as a List<String> instead. Note that overload " +
                "returns Result<TokenInfo> rather than OAuth2ClientResult<Token>, so it isn't a " +
                "drop-in replacement — it cannot be offered as an automatic ReplaceWith."
    )
    suspend fun login(
        context: Context,
        redirectUrl: String,
        extraRequestParameters: Map<String, String> = emptyMap(),
        scope: String = defaultScopeValue.joinToString(" "),
    ): OAuth2ClientResult<Token> =
        login(context, redirectUrl, scope.split(" "), extraRequestParameters)
            .mapToOAuth2ClientResult { it.toToken() }

    /**
     * Initiates the OIDC logout redirect flow.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param context the Android [Activity] [Context] which is used to display the logout flow via the configured
     * [WebAuthenticationProvider].
     * @param redirectUrl the redirect URL.
     * @param idToken the token used to identify the session to log the user out of.
     * @return an [OAuth2ClientResult]: [OAuth2ClientResult.Success] of [Unit] on completed logout, or
     *  [OAuth2ClientResult.Error] (e.g. [FlowCancelledException], [FlowAlreadyInProgressException]).
     */
    suspend fun logoutOfBrowser(
        context: Context,
        redirectUrl: String,
        idToken: String,
    ): OAuth2ClientResult<Unit> {
        val initializationResult =
            redirectCoordinator.initialize(webAuthenticationProvider, context, redirectUrl) {
                redirectEndSessionFlow
                    .start(idToken, redirectUrl)
                    .mapCatching { flowContext ->
                        RedirectInitializationResult.Success(flowContext.url.toHttpUrl(), flowContext)
                    }.getOrElse { e ->
                        RedirectInitializationResult.Error(e.asException())
                    }
            }

        val flowContext =
            when (initializationResult) {
                is RedirectInitializationResult.Error -> return OAuth2ClientResult.Error(initializationResult.exception)
                is RedirectInitializationResult.Success -> initializationResult.flowContext
            }

        val uri =
            when (val redirectResult = redirectCoordinator.listenForResult()) {
                is RedirectResult.Error -> return OAuth2ClientResult.Error(redirectResult.exception)
                is RedirectResult.Redirect -> redirectResult.uri
            }

        return redirectEndSessionFlow.resume(uri.toString(), flowContext).fold(
            onSuccess = { OAuth2ClientResult.Success(Unit) },
            onFailure = { OAuth2ClientResult.Error(it.asException()) }
        )
    }

    /**
     * Initiates the OIDC logout redirect flow using the application context captured via
     * [com.okta.authfoundation.AuthFoundation.initializeAndroidContext].
     *
     * Prefer the [Context]-accepting overload when an [Activity] [Context] is available. Use this
     * overload when one isn't; see [login] (context-less overload) for details on when that applies.
     *
     * > Note: OIDC Logout terminology is nuanced, see [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
     *
     * @param redirectUrl the redirect URL.
     * @param idToken the token used to identify the session to log the user out of.
     * @return an [OAuth2ClientResult]: [OAuth2ClientResult.Success] of [Unit] on completed logout, or
     *  [OAuth2ClientResult.Error] (e.g. [FlowCancelledException], [FlowAlreadyInProgressException]).
     */
    suspend fun logoutOfBrowser(
        redirectUrl: String,
        idToken: String,
    ): OAuth2ClientResult<Unit> = logoutOfBrowser(ApplicationContextHolder.appContext, redirectUrl, idToken)

    private fun Throwable.asException(): Exception = this as? Exception ?: RuntimeException(this)

    private fun TokenInfo.toToken(): Token = legacyToken(this, defaultScopeValue.joinToString(" "))

    private fun <T, R> Result<T>.mapToOAuth2ClientResult(transform: (T) -> R): OAuth2ClientResult<R> =
        fold(
            onSuccess = { OAuth2ClientResult.Success(transform(it)) },
            onFailure = { OAuth2ClientResult.Error(it.asException()) }
        )
}

/**
 * Builds the deprecated [Token] from [tokenInfo]. This is the only place in [WebAuthentication] that
 * references [Token] or [OidcConfiguration] — none of its constructors (preferred KMP-backed or
 * deprecated Android-only) store either type.
 *
 * [tokenInfo]'s own [TokenInfo.clientId] and [TokenInfo.issuerUrl] are used directly, so this works
 * uniformly whether [tokenInfo] came from a KMP [com.okta.authfoundation.client.kmp.OAuth2Client] or
 * from a deprecated Android-only one (bridged the same way as [com.okta.oauth2.kmp.AuthorizationCodeFlow]'s
 * own Android-client compatibility factory).
 */
@Suppress("DEPRECATION")
private fun legacyToken(
    tokenInfo: TokenInfo,
    defaultScope: String,
): Token =
    Token(
        id = tokenInfo.id,
        tokenType = tokenInfo.tokenType,
        expiresIn = tokenInfo.expiresIn,
        accessToken = tokenInfo.accessToken,
        scope = tokenInfo.scope,
        refreshToken = tokenInfo.refreshToken,
        idToken = tokenInfo.idToken,
        deviceSecret = tokenInfo.deviceSecret,
        issuedTokenType = tokenInfo.issuedTokenType,
        oidcConfiguration =
            OidcConfiguration(
                clientId = tokenInfo.clientId,
                defaultScope = defaultScope,
                issuer = tokenInfo.issuerUrl
            )
    )
