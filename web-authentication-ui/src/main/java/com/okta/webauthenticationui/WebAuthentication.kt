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
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.client.internal.SdkVersionsRegistry
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.events.EventCoordinator
import com.okta.oauth2.kmp.AuthorizationCodeFlow
import com.okta.oauth2.kmp.RedirectEndSessionFlow
import com.okta.webauthenticationui.BuildConfig.SDK_VERSION
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
     * @param enableEphemeralBrowsing whether to enable ephemeral browsing mode, if supported by the browser.
     *  See [Authorize Documentation](https://developer.okta.com/docs/reference/api/oidc/#authorize) for parameter options.
     * @return a [Result] containing [TokenInfo] on success, or a failed [Result] wrapping a
     *  [FlowCancelledException] if the user dismissed the browser, a [FlowAlreadyInProgressException]
     *  if another redirect flow was already in progress, or the underlying authorize/token exchange error.
     */
    suspend fun login(
        context: Context,
        redirectUrl: String,
        scope: List<String>,
        enableEphemeralBrowsing: Boolean = false,
        extraRequestParameters: Map<String, String> = emptyMap(),
    ): Result<TokenInfo> {
        val initializationResult =
            redirectCoordinator.initialize(webAuthenticationProvider, context, enableEphemeralBrowsing) {
                authorizationCodeFlow
                    .start(redirectUrl, scope, extraRequestParameters)
                    .mapCatching { flowContext ->
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

        val uri =
            when (val redirectResult = redirectCoordinator.listenForResult()) {
                is RedirectResult.Error -> return Result.failure(redirectResult.exception)
                is RedirectResult.Redirect -> redirectResult.uri
            }

        return authorizationCodeFlow.resume(uri.toString(), flowContext)
    }

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
        isEphemeralBrowsing: Boolean = false,
        scope: String = defaultScopeValue.joinToString(" "),
    ): OAuth2ClientResult<Token> =
        login(context, redirectUrl, scope.split(" "), isEphemeralBrowsing, extraRequestParameters)
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
            redirectCoordinator.initialize(webAuthenticationProvider, context) {
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
