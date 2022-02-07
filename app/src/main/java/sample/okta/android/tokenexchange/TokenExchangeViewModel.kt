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
package sample.okta.android.tokenexchange

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.oauth2.TokenExchangeFlow
import com.okta.oauth2.TokenExchangeFlow.Companion.tokenExchangeFlow
import kotlinx.coroutines.launch
import sample.okta.android.OktaHelper

class TokenExchangeViewModel : ViewModel() {
    companion object {
        private const val METADATA_KEY: String = "sample.okta.tokenExchange"
    }

    private val _state = MutableLiveData<TokenExchangeState>(TokenExchangeState.Loading)
    val state: LiveData<TokenExchangeState> = _state

    init {
        start()
    }

    fun start() {
        _state.value = TokenExchangeState.Loading

        viewModelScope.launch {
            val credential = OktaHelper.defaultCredential
            val tokenExchangeCredential = OktaHelper.credentialDataSource.fetchOrCreate { metadata ->
                metadata.containsKey(METADATA_KEY)
            }
            tokenExchangeCredential.storeToken(metadata = mapOf(Pair(METADATA_KEY, METADATA_KEY)))
            val tokenExchangeFlow = tokenExchangeCredential.oidcClient.tokenExchangeFlow()
            val idToken = credential.token?.idToken
            if (idToken == null) {
                _state.value = TokenExchangeState.Error("Missing Id Token")
                return@launch
            }
            val deviceSecret = credential.token?.deviceSecret
            if (deviceSecret == null) {
                _state.value = TokenExchangeState.Error("Missing Device Secret")
                return@launch
            }
            when (val result = tokenExchangeFlow.start(idToken, deviceSecret)) {
                is TokenExchangeFlow.Result.Error -> {
                    _state.value = TokenExchangeState.Error(result.message)
                }
                is TokenExchangeFlow.Result.Token -> {
                    _state.value = TokenExchangeState.Token(METADATA_KEY)
                }
            }
        }
    }
}

sealed class TokenExchangeState {
    object Loading : TokenExchangeState()
    data class Error(val message: String) : TokenExchangeState()
    data class Token(val metadataKey: String) : TokenExchangeState()
}
