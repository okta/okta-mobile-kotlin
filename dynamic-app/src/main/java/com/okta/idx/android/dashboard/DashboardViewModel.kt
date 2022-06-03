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
package com.okta.idx.android.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundationbootstrap.CredentialBootstrap
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

internal class DashboardViewModel : ViewModel() {
    private val _logoutStateLiveData = MutableLiveData<LogoutState>(LogoutState.Idle)
    val logoutStateLiveData: LiveData<LogoutState> = _logoutStateLiveData

    private val _userInfoLiveData = MutableLiveData<Map<String, String>>(emptyMap())
    val userInfoLiveData: LiveData<Map<String, String>> = _userInfoLiveData

    init {
        viewModelScope.launch {
            when (val result = CredentialBootstrap.defaultCredential().getUserInfo()) {
                is OidcClientResult.Error -> {
                    Timber.e(result.exception, "User info request failed.")
                }
                is OidcClientResult.Success -> {
                    val successResult = result.result
                    _userInfoLiveData.postValue(successResult.deserializeClaims(JsonObject.serializer()).asMap())
                }
            }
        }
    }

    fun logout() {
        _logoutStateLiveData.value = LogoutState.Loading

        viewModelScope.launch {
            when (CredentialBootstrap.defaultCredential().revokeToken(RevokeTokenType.ACCESS_TOKEN)) {
                is OidcClientResult.Error -> {
                    _logoutStateLiveData.postValue(LogoutState.Failed)
                }
                is OidcClientResult.Success -> {
                    _logoutStateLiveData.postValue(LogoutState.Success)
                }
            }
        }
    }

    fun acknowledgeLogoutSuccess() {
        _logoutStateLiveData.value = LogoutState.Idle
    }

    sealed class LogoutState {
        object Idle : LogoutState()
        object Loading : LogoutState()
        object Success : LogoutState()
        object Failed : LogoutState()
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
