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
import com.okta.authfoundation.credential.TokenMetadata
import com.okta.oauth2.kmp.TokenExchangeFlow
import kotlinx.coroutines.launch
import sample.okta.android.SampleApplication
import sample.okta.android.SampleHelper
import sample.okta.android.toTokenData
import timber.log.Timber

class TokenExchangeViewModel : ViewModel() {
    companion object {
        private const val NAME_TAG_VALUE: String = "TokenExchange"
    }

    private val _state = MutableLiveData<TokenExchangeState>(TokenExchangeState.Loading)
    val state: LiveData<TokenExchangeState> = _state

    init {
        start()
    }

    fun start() {
        _state.value = TokenExchangeState.Loading

        viewModelScope.launch {
            val credentialManager = SampleApplication.credentialManager
            val credential = credentialManager.getDefault().getOrThrow()
            val tokenExchangeCredential =
                credentialManager
                    .find { metadata: TokenMetadata -> metadata.tags[SampleHelper.CREDENTIAL_NAME_TAG_KEY] == NAME_TAG_VALUE }
                    .getOrThrow()
                    .firstOrNull()
            val tokenExchangeFlow = TokenExchangeFlow(SampleApplication.oAuth2Client)
            val idToken = credential?.token?.idToken
            if (idToken == null) {
                _state.value = TokenExchangeState.Error("Missing Id Token")
                return@launch
            }
            val deviceSecret = credential.token.deviceSecret
            if (deviceSecret == null) {
                _state.value = TokenExchangeState.Error("Missing Device Secret")
                return@launch
            }
            tokenExchangeFlow.start(idToken, deviceSecret).fold(
                onFailure = { exception ->
                    Timber.e(exception, "Failed to start token exchange flow.")
                    _state.value = TokenExchangeState.Error("An error occurred.")
                },
                onSuccess = { tokenInfo ->
                    tokenExchangeCredential?.deleteAsync()
                    val tokenData = tokenInfo.toTokenData(SampleApplication.oAuth2Client.configuration)
                    credentialManager.store(
                        tokenData,
                        tags = mapOf(Pair(SampleHelper.CREDENTIAL_NAME_TAG_KEY, NAME_TAG_VALUE))
                    )
                    _state.value = TokenExchangeState.Token(NAME_TAG_VALUE)
                }
            )
        }
    }
}

sealed class TokenExchangeState {
    object Loading : TokenExchangeState()

    data class Error(
        val message: String,
    ) : TokenExchangeState()

    data class Token(
        val nameTagValue: String,
    ) : TokenExchangeState()
}
