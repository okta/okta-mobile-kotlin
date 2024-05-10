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
package sample.okta.android.resourceowner

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.client.OAuth2ClientResult
import com.okta.authfoundation.credential.Credential
import com.okta.oauth2.ResourceOwnerFlow
import kotlinx.coroutines.launch
import timber.log.Timber

internal class ResourceOwnerViewModel : ViewModel() {
    private val _state = MutableLiveData<ResourceOwnerState>(ResourceOwnerState.Idle)
    val state: LiveData<ResourceOwnerState> = _state

    fun login(username: String, password: String) {
        _state.value = ResourceOwnerState.Loading

        viewModelScope.launch {
            val resourceOwnerFlow = ResourceOwnerFlow()
            when (val result = resourceOwnerFlow.start(username, password)) {
                is OAuth2ClientResult.Error -> {
                    Timber.e(result.exception, "Failed to start resource owner flow.")
                    _state.value = ResourceOwnerState.Error("An error occurred.")
                }
                is OAuth2ClientResult.Success -> {
                    val credential = Credential.store(token = result.result)
                    Credential.setDefaultAsync(credential)
                    _state.value = ResourceOwnerState.Token
                }
            }
        }
    }
}

sealed class ResourceOwnerState {
    object Idle : ResourceOwnerState()
    object Loading : ResourceOwnerState()
    data class Error(val message: String) : ResourceOwnerState()
    object Token : ResourceOwnerState()
}
