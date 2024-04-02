/*
 * Copyright 2021-Present Okta, Inc.
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
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.dto.OidcIntrospectInfo
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TokenType
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.webauthenticationui.WebAuthenticationClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sample.okta.android.BuildConfig
import sample.okta.android.SampleHelper
import timber.log.Timber

internal class DashboardViewModel(private val credentialTagNameValue: String?) : ViewModel() {
    private val _requestStateLiveData = MutableLiveData<RequestState>(RequestState.Result(""))
    val requestStateLiveData: LiveData<RequestState> = _requestStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    private val _credentialLiveData = MutableLiveData<Credential>()
    val credentialLiveData: LiveData<Credential> = _credentialLiveData

    var lastButtonId: Int = 0
    private var lastRequestJob: Job? = null

    private lateinit var credential: Credential

    init {
        viewModelScope.launch {
            credential = if (credentialTagNameValue == null) {
                CredentialBootstrap.defaultCredential()!!
            } else {
                CredentialBootstrap.credentialDataSource.findCredential { it.tags[SampleHelper.CREDENTIAL_NAME_TAG_KEY] == credentialTagNameValue }.firstOrNull()
                    ?: CredentialBootstrap.defaultCredential()!!
            }
            setCredential(credential)
        }
    }

    fun setCredential(credential: Credential) {
        this.credential = credential
        viewModelScope.launch {
            _credentialLiveData.value = credential
            getUserInfo()
        }
    }

    fun revoke(buttonId: Int, tokenType: RevokeTokenType) {
        performRequest(buttonId) { credential ->
            when (credential.revokeToken(tokenType)) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to revoke token.")
                }
                is OidcClientResult.Success -> {
                    RequestState.Result("Token Revoked.")
                }
            }
        }
    }

    fun refresh(buttonId: Int) {
        performRequest(buttonId) { credential ->
            when (credential.refreshToken()) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to refresh token.")
                }
                is OidcClientResult.Success -> {
                    _credentialLiveData.value = credential // Update the UI.
                    RequestState.Result("Token Refreshed.")
                }
            }
        }
    }

    fun introspect(buttonId: Int, tokenType: TokenType) {
        performRequest(buttonId) { credential ->
            when (val result = credential.introspectToken(tokenType)) {
                is OidcClientResult.Error -> {
                    RequestState.Result("Failed to introspect token.")
                }
                is OidcClientResult.Success -> {
                    val successResult = result.result
                    if (successResult is OidcIntrospectInfo.Active) {
                        RequestState.Result(successResult.deserializeClaims(JsonObject.serializer()).asMap().displayableKeyValues())
                    } else {
                        RequestState.Result(mapOf("active" to "false").displayableKeyValues())
                    }
                }
            }
        }
    }

    fun logoutOfWeb(context: Context) {
        viewModelScope.launch {
            val idToken = credential.token?.idToken ?: return@launch
            when (
                val result = WebAuthenticationClient().logoutOfBrowser(
                    context = context,
                    redirectUrl = BuildConfig.SIGN_OUT_REDIRECT_URI,
                    idToken = idToken,
                )
            ) {
                is OidcClientResult.Error -> {
                    Timber.e(result.exception, "Failed to start logout flow.")
                    _requestStateLiveData.value = RequestState.Result("Logout failed.")
                }
                is OidcClientResult.Success -> {
                    credential.delete()
                    _requestStateLiveData.value = RequestState.Result("Logout successful!")
                }
            }
        }
    }

    private fun performRequest(buttonId: Int, performer: suspend (Credential) -> RequestState) {
        if (lastRequestJob?.isActive == true) {
            // Re-enable the button, so it's not permanently disabled.
            _requestStateLiveData.value = RequestState.Result("")
        }
        lastRequestJob?.cancel()
        lastButtonId = buttonId

        val credential = credential
        _requestStateLiveData.value = RequestState.Loading

        lastRequestJob = viewModelScope.launch {
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
        when (val userInfoResult = credential.getUserInfo()) {
            is OidcClientResult.Error -> {
                Timber.e(userInfoResult.exception, "Failed to fetch user info.")
                _userInfoLiveData.postValue(emptyMap())
            }
            is OidcClientResult.Success -> {
                _userInfoLiveData.postValue(userInfoResult.result.deserializeClaims(JsonObject.serializer()).asMap())
            }
        }
    }

    sealed class RequestState {
        object Loading : RequestState()
        data class Result(val text: String) : RequestState()
    }

    fun deleteCredential() {
        viewModelScope.launch {
            credential.delete()
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
