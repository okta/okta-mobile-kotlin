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

import androidx.lifecycle.MutableLiveData
import com.okta.idx.android.directauth.sdk.Form
import com.okta.idx.android.directauth.sdk.FormAction
import com.okta.idx.android.directauth.sdk.util.emitValidation
import com.okta.idx.sdk.api.model.UserProfile

class RegisterForm internal constructor(
    val viewModel: ViewModel,
    private val formAction: FormAction
) : Form {
    class Attribute(
        val key: String,
        val label: String,
        @Volatile var value: String = "",
        val errorsLiveData: MutableLiveData<String> = MutableLiveData("")
    )

    class ViewModel internal constructor(
        val attributes: List<Attribute>
    ) {
        fun isValid(): Boolean {
            return attributes.map {
                it.errorsLiveData.emitValidation { it.value.isNotEmpty() }
            }.reduce { acc, b ->
                acc && b
            }
        }
    }

    fun register() {
        if (!viewModel.isValid()) return

        formAction.proceed {
            // Need to begin the transaction again, in case an error occurred.
            val beginResponse = authenticationWrapper.begin()
            handleTerminalTransitions(beginResponse)?.let { return@proceed it }

            val newUserRegistrationResponse = authenticationWrapper.fetchSignUpFormValues(
                beginResponse.proceedContext
            )
            handleTerminalTransitions(newUserRegistrationResponse)?.let { return@proceed it }

            val userProfile = UserProfile()
            for (attribute in viewModel.attributes) {
                userProfile.addAttribute(attribute.key, attribute.value)
            }

            val response = authenticationWrapper.register(
                newUserRegistrationResponse.proceedContext,
                userProfile
            )
            handleKnownTransitions(response)
        }
    }

    fun signOut() {
        formAction.signOut()
    }
}
