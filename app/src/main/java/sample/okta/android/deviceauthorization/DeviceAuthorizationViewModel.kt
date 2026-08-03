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
import com.okta.oauth2.kmp.DeviceAuthorizationFlow
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext
import kotlinx.coroutines.launch
import sample.okta.android.SampleApplication
import sample.okta.android.toTokenData
import timber.log.Timber

internal class DeviceAuthorizationViewModel : ViewModel() {
    private val _state = MutableLiveData<DeviceAuthorizationState>(DeviceAuthorizationState.Loading)
    val state: LiveData<DeviceAuthorizationState> = _state

    init {
        start()
    }

    fun start() {
        _state.value = DeviceAuthorizationState.Loading

        viewModelScope.launch {
            val deviceAuthorizationFlow = DeviceAuthorizationFlow(SampleApplication.oAuth2Client)
            deviceAuthorizationFlow.start(SampleApplication.oAuth2Client.configuration.defaultScope).fold(
                onFailure = { exception ->
                    Timber.e(exception, "Failed to start device authorization flow.")
                    _state.value = DeviceAuthorizationState.Error("An error occurred.")
                },
                onSuccess = { flowContext ->
                    _state.value = DeviceAuthorizationState.Polling(flowContext.userCode, flowContext.verificationUri)
                    resume(deviceAuthorizationFlow, flowContext)
                }
            )
        }
    }

    private suspend fun resume(
        deviceAuthorizationFlow: DeviceAuthorizationFlow,
        flowContext: DeviceAuthorizationFlowContext,
    ) {
        deviceAuthorizationFlow.resume(flowContext).fold(
            onFailure = { exception ->
                Timber.e(exception, "Failed to resume device authorization flow.")
                _state.value = DeviceAuthorizationState.Error("An error occurred.")
            },
            onSuccess = { tokenInfo ->
                val tokenData = tokenInfo.toTokenData(SampleApplication.oAuth2Client.configuration)
                val credential = SampleApplication.credentialManager.store(tokenData).getOrThrow()
                SampleApplication.credentialManager.setDefault(credential)
                _state.value = DeviceAuthorizationState.Token
            }
        )
    }
}

sealed class DeviceAuthorizationState {
    data class Polling(
        val code: String,
        val url: String,
    ) : DeviceAuthorizationState()

    object Loading : DeviceAuthorizationState()

    data class Error(
        val message: String,
    ) : DeviceAuthorizationState()

    object Token : DeviceAuthorizationState()
}
