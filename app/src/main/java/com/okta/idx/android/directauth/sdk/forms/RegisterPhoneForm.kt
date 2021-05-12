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
import com.okta.idx.sdk.api.client.Authenticator
import com.okta.idx.sdk.api.client.ProceedContext

class RegisterPhoneForm internal constructor(
    val viewModel: ViewModel,
    private val formAction: FormAction,
) : Form {
    class ViewModel internal constructor(
        var phoneNumber: String = "",
        val factor: Authenticator.Factor,
        internal val proceedContext: ProceedContext,
    ) {
        private val _phoneNumberErrorsLiveData = MutableLiveData("")
        val phoneNumberErrorsLiveData: LiveData<String> = _phoneNumberErrorsLiveData

        fun isValid(): Boolean {
            return _phoneNumberErrorsLiveData.emitValidation { phoneNumber.isNotEmpty() }
        }
    }

    fun register() {
        if (!viewModel.isValid()) return

        formAction.proceed {
            val response = authenticationWrapper.submitPhoneAuthenticator(
                viewModel.proceedContext,
                viewModel.phoneNumber,
                viewModel.factor,
            )
            handleTerminalTransitions(response)?.let { return@proceed it }
            FormAction.ProceedTransition.FormTransition(
                VerifyCodeForm(
                    viewModel = VerifyCodeForm.ViewModel(
                        proceedContext = response.proceedContext
                    ),
                    formAction = formAction
                )
            )
        }
    }

    fun signOut() {
        formAction.signOut()
    }
}
