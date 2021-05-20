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
package com.okta.idx.android.directauth

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.android.directauth.sdk.FormAction
import com.okta.idx.android.network.Network

internal class DirectAuthViewModel : ViewModel() {
    private val _stateLiveData = MutableLiveData<FormAction.State>()
    val stateLiveData: LiveData<FormAction.State> = _stateLiveData

    private val formAction = FormAction(viewModelScope, _stateLiveData, Network.authenticationWrapper())

    init {
        formAction.signOut()
    }

    fun goToTableOfContents() {
        formAction.signOut()
    }

    fun handleSocialRedirectUri(uri: Uri) {
        val errorQueryParameter = uri.getQueryParameter("error")
        val stateQueryParameter = uri.getQueryParameter("state")
        if (errorQueryParameter == "interaction_required") {
            formAction.proceed {
                // Validate the state matches. This is a security assurance.
                if (proceedContext?.clientContext?.state != stateQueryParameter) {
                    val error = "IDP redirect failed due to state mismatch."
                    return@proceed FormAction.ProceedTransition.ErrorTransition(listOf(error))
                }
                val response = authenticationWrapper.introspect(proceedContext?.clientContext)
                handleKnownTransitions(response)
            }
            return
        }
        if (errorQueryParameter != null) {
            formAction.proceed {
                val errorDescription = uri.getQueryParameter("error_description") ?: "An error occurred."
                FormAction.ProceedTransition.ErrorTransition(listOf(errorDescription))
            }
            return
        }
        val interactionCodeQueryParameter = uri.getQueryParameter("interaction_code")
        if (interactionCodeQueryParameter != null) {
            formAction.proceed {
                // Validate the state matches. This is a security assurance.
                if (proceedContext?.clientContext?.state != stateQueryParameter) {
                    val error = "IDP redirect failed due to state mismatch."
                    return@proceed FormAction.ProceedTransition.ErrorTransition(listOf(error))
                }
                val response = authenticationWrapper.fetchTokenWithInteractionCode(Network.baseUrl, proceedContext, interactionCodeQueryParameter)
                handleKnownTransitions(response)
            }
        }
    }
}
