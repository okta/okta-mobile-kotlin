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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.idx.android.directauth.sdk.DisplayableForm
import com.okta.idx.android.directauth.sdk.FormAction
import com.okta.idx.android.network.Network
import com.okta.idx.sdk.api.response.TokenResponse

internal class DirectAuthViewModel : ViewModel() {
    private val _stateLiveData = MutableLiveData<FormAction.State>()
    val stateLiveData: LiveData<FormAction.State> = _stateLiveData

    private val formAction = FormAction(viewModelScope, _stateLiveData, Network.idxClient())

    init {
        formAction.signOut()
    }

    fun signOut() {
        formAction.signOut()
    }
}
