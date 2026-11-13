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
package sample.okta.android.browser

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.kmp.TokenData
import com.okta.webauthenticationui.DefaultWebAuthenticationProvider
import com.okta.webauthenticationui.PendingLoginPersistence
import com.okta.webauthenticationui.WebAuthentication
import kotlinx.coroutines.launch
import sample.okta.android.BuildConfig
import sample.okta.android.SampleApplication
import sample.okta.android.SampleHelper
import sample.okta.android.toTokenData
import timber.log.Timber

class BrowserViewModel : ViewModel() {
    private val _state = MutableLiveData<BrowserState>(BrowserState.Idle)
    val state: LiveData<BrowserState> = _state

    init {
        resumeInterruptedLogin()
    }

    /**
     * Finishes a login that was interrupted when Android killed this app's process while the user was in the
     * browser. [WebAuthentication.resumeLogin] returns `null` when there is nothing to resume, and otherwise
     * suspends until the browser redirect arrives (or the pending login expires).
     */
    private fun resumeInterruptedLogin() {
        viewModelScope.launch {
            val result = createWebAuthentication(ephemeralBrowsing = false).resumeLogin() ?: return@launch
            _state.value = BrowserState.Loading
            result
                .onSuccess { storeTokens(it) }
                .onFailure { exception ->
                    if (exception is WebAuthentication.FlowCancelledException) {
                        _state.value = BrowserState.Idle
                    } else {
                        Timber.e(exception, "Failed to resume login flow.")
                        _state.value = BrowserState.Error(exception.message ?: "Failed to resume login flow.")
                    }
                }
        }
    }

    fun login(
        context: Context,
        addDeviceSsoScope: Boolean,
        enableEphemeralBrowsing: Boolean,
    ) {
        viewModelScope.launch {
            _state.value = BrowserState.Loading

            val webAuthentication = createWebAuthentication(enableEphemeralBrowsing)
            val scope =
                if (addDeviceSsoScope) {
                    "${SampleHelper.DEFAULT_SCOPE} device_sso"
                } else {
                    SampleHelper.DEFAULT_SCOPE
                }

            when (
                val result =
                    webAuthentication.login(
                        context = context,
                        redirectUrl = BuildConfig.SIGN_IN_REDIRECT_URI,
                        extraRequestParameters = emptyMap(),
                        scope = scope
                    )
            ) {
                is OAuth2ClientResult.Error -> {
                    Timber.e(result.exception, "Failed to start login flow.")
                    _state.value = BrowserState.Error(result.exception.message ?: "Failed to start login flow.")
                }

                is OAuth2ClientResult.Success -> {
                    storeTokens(result.result)
                }
            }
        }
    }

    /**
     * Persistence is opt-in because the saved state includes the PKCE `code_verifier`. This sample stores it
     * unencrypted for brevity; production apps should pass a `PendingLoginEncryptor` and exclude the DataStore
     * file from backup. See the web-authentication-ui README.
     */
    private fun createWebAuthentication(ephemeralBrowsing: Boolean) =
        WebAuthentication(
            SampleApplication.oAuth2Client,
            DefaultWebAuthenticationProvider(ephemeralBrowsingEnabled = ephemeralBrowsing),
            PendingLoginPersistence.Enabled()
        )

    private suspend fun storeTokens(tokenInfo: TokenInfo) {
        val credentialManager = SampleApplication.credentialManager
        val tokenData = tokenInfo.toTokenData(SampleApplication.oAuth2Client.configuration)
        val credential = credentialManager.store(tokenData).getOrThrow()
        credentialManager.setDefault(credential)
        _state.value = BrowserState.Token
    }
}

sealed class BrowserState {
    object Idle : BrowserState()

    object Loading : BrowserState()

    data class Error(
        val message: String,
    ) : BrowserState()

    object Token : BrowserState()
}
