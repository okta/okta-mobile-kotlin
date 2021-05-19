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
package com.okta.idx.android.directauth.sdk.forms

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.okta.idx.android.directauth.sdk.Form
import com.okta.idx.android.directauth.sdk.FormAction
import com.okta.idx.android.directauth.sdk.util.emitValidation

class ForgotPasswordForm internal constructor(
    val viewModel: ViewModel = ViewModel(),
    private val formAction: FormAction,
) : Form {
    class ViewModel internal constructor(
        var username: String = "",
    ) {
        private val _usernameErrorsLiveData = MutableLiveData("")
        val usernameErrorsLiveData: LiveData<String> = _usernameErrorsLiveData

        fun isValid(): Boolean {
            return _usernameErrorsLiveData.emitValidation { username.isNotEmpty() }
        }
    }

    fun forgotPassword() {
        if (!viewModel.isValid()) return

        formAction.proceed {
            val response = authenticationWrapper.recoverPassword(viewModel.username, proceedContext)
            handleKnownTransitions(response)
        }
    }

    fun signOut() {
        formAction.signOut()
    }
}
