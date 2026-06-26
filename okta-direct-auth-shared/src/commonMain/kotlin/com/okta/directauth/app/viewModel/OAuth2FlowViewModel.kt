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
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.api.http.getHttpClientEngine
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.kmp.OAuth2Client
import com.okta.directauth.app.AppConfig
import com.okta.directauth.app.model.OAuth2FlowState
import com.okta.directauth.app.platform.platformBrowserLogin
import com.okta.directauth.app.util.AppLogger
import com.okta.directauth.app.util.LogScope
import com.okta.directauth.app.util.log
import com.okta.oauth2.kmp.DeviceAuthorizationFlow
import com.okta.oauth2.kmp.ResourceOwnerFlow
import com.okta.oauth2.kmp.SessionTokenFlow
import com.okta.oauth2.kmp.TokenExchangeFlow
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing all OAuth2 flow operations.
 *
 * Creates a shared [OAuth2Client] from [AppConfig] and provides methods to start each
 * OAuth2 flow. The current flow state is exposed via [flowState].
 */
class OAuth2FlowViewModel :
    ViewModel(),
    LogScope {
    companion object {
        private const val DEFAULT_SCOPE = "openid profile email offline_access"
    }

    override val logTag = "OAuth2FlowViewModel"

    private val _flowState = MutableStateFlow<OAuth2FlowState>(OAuth2FlowState.Idle)

    /** Current state of the active OAuth2 flow. */
    val flowState = _flowState.asStateFlow()

    private var activeJob: Job? = null

    private val client: OAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = AppConfig.ISSUER,
                clientId = AppConfig.CLIENT_ID,
                scope = DEFAULT_SCOPE.split(" ")
            ) {
                authorizationServerId = AppConfig.AUTHORIZATION_SERVER_ID
                apiExecutor =
                    KtorHttpExecutor(
                        getHttpClientEngine().config {
                            install(Logging) {
                                logger = Logger.SIMPLE
                                level = LogLevel.BODY
                            }
                        }
                    )
            }.getOrThrow()

    /**
     * Start the Resource Owner Password grant flow.
     *
     * @param username the user's username
     * @param password the user's password
     */
    fun startResourceOwner(
        username: String,
        password: String,
    ) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            log("Starting Resource Owner flow for user: $username")
            val flow = ResourceOwnerFlow(client)
            flow.start(username, password, DEFAULT_SCOPE).fold(
                onSuccess = { tokenInfo ->
                    log("Resource Owner flow succeeded")
                    _flowState.value = OAuth2FlowState.Authenticated(tokenInfo)
                },
                onFailure = { error ->
                    log("Resource Owner flow failed: ${error.message}")
                    _flowState.value = OAuth2FlowState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Start the Device Authorization grant flow.
     *
     * First requests a device code, emits [OAuth2FlowState.DeviceAuthPolling] with the user code
     * and verification URI, then polls until the user approves or the authorization expires.
     */
    fun startDeviceAuthorization() {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            log("Starting Device Authorization flow")
            val flow = DeviceAuthorizationFlow(client)
            flow.start(DEFAULT_SCOPE).fold(
                onSuccess = { context ->
                    log("Device code received: ${context.userCode}")
                    _flowState.value =
                        OAuth2FlowState.DeviceAuthPolling(
                            userCode = context.userCode,
                            verificationUri = context.verificationUri,
                            verificationUriComplete = context.verificationUriComplete,
                            expiresIn = context.expiresIn
                        )
                    flow.resume(context).fold(
                        onSuccess = { tokenInfo ->
                            log("Device Authorization flow succeeded")
                            _flowState.value = OAuth2FlowState.Authenticated(tokenInfo)
                        },
                        onFailure = { error ->
                            log("Device Authorization polling failed: ${error.message}")
                            _flowState.value = OAuth2FlowState.Error(error.message ?: "Authorization timed out")
                        }
                    )
                },
                onFailure = { error ->
                    log("Device Authorization start failed: ${error.message}")
                    _flowState.value = OAuth2FlowState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Start the Browser Sign-In (Authorization Code + PKCE) flow.
     *
     * Opens the system browser for Okta authorization and captures the redirect callback.
     * On Android, uses Chrome Custom Tabs via web-authentication-ui.
     * On Desktop, uses a localhost HTTP server to capture the redirect.
     *
     * @param platformContext platform-specific context (Android Context or null for JVM)
     */
    fun startBrowserAuth(platformContext: Any?) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.BrowserAuthWaiting
            log("Starting Browser Auth flow")
            platformBrowserLogin(platformContext, client, AppConfig.SIGN_IN_REDIRECT_URI, DEFAULT_SCOPE).fold(
                onSuccess = { tokenInfo ->
                    log("Browser Auth flow succeeded")
                    _flowState.value = OAuth2FlowState.Authenticated(tokenInfo)
                },
                onFailure = { error ->
                    log("Browser Auth flow failed: ${error.message}")
                    _flowState.value = OAuth2FlowState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Start the Token Exchange grant flow (Native SSO).
     *
     * @param idToken an existing ID token to exchange
     * @param deviceSecret an existing device secret
     */
    fun startTokenExchange(
        idToken: String,
        deviceSecret: String,
    ) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            log("Starting Token Exchange flow")
            val flow = TokenExchangeFlow(client)
            flow.start(idToken, deviceSecret, scope = DEFAULT_SCOPE).fold(
                onSuccess = { tokenInfo ->
                    log("Token Exchange flow succeeded")
                    _flowState.value = OAuth2FlowState.Authenticated(tokenInfo)
                },
                onFailure = { error ->
                    log("Token Exchange flow failed: ${error.message}")
                    _flowState.value = OAuth2FlowState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    /**
     * Start the Session Token flow.
     *
     * Exchanges a pre-obtained session token for OAuth2 tokens via a server-side redirect.
     *
     * @param sessionToken a session token obtained from the Okta Authn API or another source
     */
    fun startSessionToken(sessionToken: String) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            log("Starting Session Token flow")
            val flow = SessionTokenFlow(client)
            flow.start(sessionToken, AppConfig.SIGN_IN_REDIRECT_URI, scope = DEFAULT_SCOPE).fold(
                onSuccess = { tokenInfo ->
                    log("Session Token flow succeeded")
                    _flowState.value = OAuth2FlowState.Authenticated(tokenInfo)
                },
                onFailure = { error ->
                    log("Session Token flow failed: ${error.message}")
                    _flowState.value = OAuth2FlowState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    /** Reset the flow state to [OAuth2FlowState.Idle] and cancel any active operation. */
    fun reset() {
        AppLogger.write(logTag, "Resetting OAuth2 flow state")
        activeJob?.cancel()
        activeJob = null
        _flowState.value = OAuth2FlowState.Idle
    }

    private fun cancelAndLaunch(block: suspend context(LogScope) () -> Unit) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch { context(this@OAuth2FlowViewModel) { block() } }
    }
}
