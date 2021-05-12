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
import com.okta.idx.sdk.api.model.UserProfile

class RegisterForm internal constructor(
    val viewModel: ViewModel = ViewModel(),
    private val formAction: FormAction
) : Form {
    class ViewModel internal constructor(
        var lastName: String = "",
        var firstName: String = "",
        var primaryEmail: String = "",
    ) {
        private val _lastNameErrorsLiveData = MutableLiveData("")
        val lastNameErrorsLiveData: LiveData<String> = _lastNameErrorsLiveData

        private val _firstNameErrorsLiveData = MutableLiveData("")
        val firstNameErrorsLiveData: LiveData<String> = _firstNameErrorsLiveData

        private val _primaryEmailErrorsLiveData = MutableLiveData("")
        val primaryEmailErrorsLiveData: LiveData<String> = _primaryEmailErrorsLiveData

        fun isValid(): Boolean {
            val lastNameValid = _lastNameErrorsLiveData.emitValidation { lastName.isNotEmpty() }
            val firstNameValid = _firstNameErrorsLiveData.emitValidation { firstName.isNotEmpty() }
            val primaryEmailValid =
                _primaryEmailErrorsLiveData.emitValidation { primaryEmail.isNotEmpty() }
            return lastNameValid && firstNameValid && primaryEmailValid
        }
    }

    fun register() {
        if (!viewModel.isValid()) return

        formAction.proceed {
            val newUserRegistrationResponse = authenticationWrapper.fetchSignUpFormValues()

            val userProfile = UserProfile()
            userProfile.addAttribute("lastName", viewModel.lastName)
            userProfile.addAttribute("firstName", viewModel.firstName)
            userProfile.addAttribute("email", viewModel.primaryEmail)

            val proceedContext = newUserRegistrationResponse.proceedContext

            val response = authenticationWrapper.register(proceedContext, userProfile)
            handleKnownTransitions(response)
        }
    }

    fun signOut() {
        formAction.signOut()
    }
}
