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
package sample.okta.android.deviceauthorization

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.oauth2.DeviceAuthorizationFlow
import com.okta.oauth2.DeviceAuthorizationFlow.Companion.createDeviceAuthorizationFlow
import kotlinx.coroutines.launch

internal class DeviceAuthorizationViewModel : ViewModel() {
    private val _state = MutableLiveData<DeviceAuthorizationState>(DeviceAuthorizationState.Loading)
    val state: LiveData<DeviceAuthorizationState> = _state

    init {
        start()
    }

    fun start() {
        _state.value = DeviceAuthorizationState.Loading

        viewModelScope.launch {
            val deviceAuthorizationFlow = CredentialBootstrap.oidcClient.createDeviceAuthorizationFlow()
            when (val result = deviceAuthorizationFlow.start()) {
                is OidcClientResult.Error -> {
                    _state.value = DeviceAuthorizationState.Error(result.exception.message ?: "An error occurred.")
                }
                is OidcClientResult.Success -> {
                    _state.value = DeviceAuthorizationState.Polling(result.result.userCode, result.result.verificationUri)
                    resume(deviceAuthorizationFlow, result.result)
                }
            }
        }
    }

    private suspend fun resume(deviceAuthorizationFlow: DeviceAuthorizationFlow, flowContext: DeviceAuthorizationFlow.Context) {
        when (val result = deviceAuthorizationFlow.resume(flowContext)) {
            is OidcClientResult.Error -> {
                _state.value = DeviceAuthorizationState.Error(result.exception.message ?: "An error occurred.")
            }
            is OidcClientResult.Success -> {
                CredentialBootstrap.defaultCredential().storeToken(token = result.result)
                _state.value = DeviceAuthorizationState.Token
            }
        }
    }
}

sealed class DeviceAuthorizationState {
    data class Polling(val code: String, val url: String) : DeviceAuthorizationState()
    object Loading : DeviceAuthorizationState()
    data class Error(val message: String) : DeviceAuthorizationState()
    object Token : DeviceAuthorizationState()
}
