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
package sample.okta.android.dashboard

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.client.dto.IntrospectInfo
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundation.credential.kmp.Credential
import com.okta.webauthenticationui.WebAuthentication
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sample.okta.android.BuildConfig
import sample.okta.android.SampleApplication
import sample.okta.android.SampleHelper
import timber.log.Timber

internal class DashboardViewModel(
    private val credentialTagNameValue: String?,
) : ViewModel() {
    private val _requestStateLiveData = MutableLiveData<RequestState>(RequestState.Result(""))
    val requestStateLiveData: LiveData<RequestState> = _requestStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    private val _credentialLiveData = MutableLiveData<CredentialState>()
    val credentialLiveData: LiveData<CredentialState> = _credentialLiveData

    var lastButtonId: Int = 0
    private var lastRequestJob: Job? = null

    private lateinit var credential: Credential

    init {
        viewModelScope.launch {
            val credentialManager = SampleApplication.credentialManager
            val cred =
                if (credentialTagNameValue == null) {
                    credentialManager.getDefault().getOrThrow()
                } else {
                    credentialManager
                        .find { metadata: TokenMetadata -> metadata.tags[SampleHelper.CREDENTIAL_NAME_TAG_KEY] == credentialTagNameValue }
                        .getOrThrow()
                        .firstOrNull() ?: credentialManager.getDefault().getOrThrow()
                }
            cred?.let { setCredential(it) } ?: run {
                _credentialLiveData.value = CredentialState.LoggedOut
            }
        }
    }

    fun setCredential(credential: Credential) {
        this.credential = credential
        viewModelScope.launch {
            _credentialLiveData.value = CredentialState.Loaded(credential)
            getUserInfo()
        }
    }

    fun revoke(
        buttonId: Int,
        tokenType: RevokeTokenType,
    ) {
        performRequest(buttonId) { credential ->
            credential.revokeToken(tokenType).fold(
                onFailure = { RequestState.Result("Failed to revoke token.") },
                onSuccess = { RequestState.Result("Token Revoked.") }
            )
        }
    }

    fun refresh(buttonId: Int) {
        performRequest(buttonId) { credential ->
            credential.refreshToken().fold(
                onFailure = { RequestState.Result("Failed to refresh token.") },
                onSuccess = { refreshed ->
                    this.credential = refreshed
                    _credentialLiveData.value = CredentialState.Loaded(refreshed) // Update the UI.
                    RequestState.Result("Token Refreshed.")
                }
            )
        }
    }

    fun introspect(
        buttonId: Int,
        tokenType: TokenType,
    ) {
        performRequest(buttonId) { credential ->
            credential.introspectToken(tokenType).fold(
                onFailure = { RequestState.Result("Failed to introspect token.") },
                onSuccess = { info ->
                    if (info is IntrospectInfo.Active) {
                        RequestState.Result(info.deserializeClaims(JsonObject.serializer()).asMap().displayableKeyValues())
                    } else {
                        RequestState.Result(mapOf("active" to "false").displayableKeyValues())
                    }
                }
            )
        }
    }

    fun logoutOfWeb(context: Context) {
        viewModelScope.launch {
            val idToken = credential.token.idToken ?: return@launch
            when (
                val result =
                    WebAuthentication(SampleApplication.oAuth2Client).logoutOfBrowser(
                        context = context,
                        redirectUrl = BuildConfig.SIGN_OUT_REDIRECT_URI,
                        idToken = idToken
                    )
            ) {
                is OAuth2ClientResult.Error -> {
                    Timber.e(result.exception, "Failed to start logout flow.")
                    _requestStateLiveData.value = RequestState.Result("Logout failed.")
                }

                is OAuth2ClientResult.Success -> {
                    credential.deleteAsync()
                    _requestStateLiveData.value = RequestState.Result("Logout successful!")
                }
            }
        }
    }

    private fun performRequest(
        buttonId: Int,
        performer: suspend (Credential) -> RequestState,
    ) {
        if (lastRequestJob?.isActive == true) {
            // Re-enable the button, so it's not permanently disabled.
            _requestStateLiveData.value = RequestState.Result("")
        }
        lastRequestJob?.cancel()
        lastButtonId = buttonId

        val credential = credential
        _requestStateLiveData.value = RequestState.Loading

        lastRequestJob =
            viewModelScope.launch {
                _requestStateLiveData.postValue(performer(credential))
            }
    }

    private fun Map<String, String>.displayableKeyValues(): String {
        var result = ""
        for (entry in this) {
            result += entry.key + ": " + entry.value + "\n"
        }
        return result
    }

    private suspend fun getUserInfo() {
        credential.getUserInfo().fold(
            onFailure = { exception ->
                Timber.e(exception, "Failed to fetch user info.")
                _userInfoLiveData.postValue(emptyMap())
            },
            onSuccess = { userInfo ->
                _userInfoLiveData.postValue(userInfo.deserializeClaims(JsonObject.serializer()).asMap())
            }
        )
    }

    sealed class RequestState {
        data object Loading : RequestState()

        data class Result(
            val text: String,
        ) : RequestState()
    }

    sealed interface CredentialState {
        data object LoggedOut : CredentialState

        data class Loaded(
            val credential: Credential,
        ) : CredentialState
    }

    fun deleteCredential() {
        viewModelScope.launch {
            credential.deleteAsync()
        }
    }
}

private fun JsonObject.asMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for (entry in this) {
        val value = entry.value
        if (value is JsonPrimitive) {
            map[entry.key] = value.content
        } else {
            map[entry.key] = value.toString()
        }
    }
    return map
}
