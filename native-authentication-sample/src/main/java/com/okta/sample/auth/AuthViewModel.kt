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
package com.okta.sample.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.credential.Token
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.idx.kotlin.client.InteractionCodeFlow.Companion.createInteractionCodeFlow
import com.okta.nativeauthentication.NativeAuthenticationClient
import com.okta.nativeauthentication.form.Form
import com.okta.sample.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val nativeAuthenticationCallback = object : NativeAuthenticationClient.Callback() {
        override fun signInComplete(token: Token) {
            viewModelScope.launch {
                CredentialBootstrap.defaultCredential().storeToken(token)
            }
        }
    }

    val forms: Flow<Form> = NativeAuthenticationClient.create(nativeAuthenticationCallback) {
        CredentialBootstrap.oidcClient.createInteractionCodeFlow(BuildConfig.REDIRECT_URI)
    }.shareIn(viewModelScope, SharingStarted.Lazily, 1)
}
