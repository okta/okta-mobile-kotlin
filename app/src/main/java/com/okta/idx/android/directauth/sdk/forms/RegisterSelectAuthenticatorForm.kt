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

import com.okta.idx.android.directauth.sdk.Form
import com.okta.idx.android.directauth.sdk.FormAction
import com.okta.idx.sdk.api.client.Authenticator
import com.okta.idx.sdk.api.client.ProceedContext

class RegisterSelectAuthenticatorForm internal constructor(
    val viewModel: ViewModel,
    private val formAction: FormAction,
) : Form {
    class ViewModel internal constructor(
        val authenticators: List<Authenticator>,
        val canSkip: Boolean,
        internal val proceedContext: ProceedContext,
    )

    fun register(factor: Authenticator.Factor) {
        formAction.proceed {
            val response = authenticationWrapper.enrollAuthenticator(
                viewModel.proceedContext,
                factor
            )
            handleKnownTransitions(response)?.let { return@proceed it }

            when (factor.method) {
                "email" -> {
                    FormAction.ProceedTransition.FormTransition(
                        RegisterVerifyCodeForm(
                            RegisterVerifyCodeForm.ViewModel(proceedContext = response.proceedContext),
                            formAction
                        )
                    )
                }
                "password" -> {
                    FormAction.ProceedTransition.FormTransition(
                        RegisterPasswordForm(
                            RegisterPasswordForm.ViewModel(proceedContext = response.proceedContext),
                            formAction
                        )
                    )
                }
                "sms" -> {
                    FormAction.ProceedTransition.FormTransition(
                        RegisterPhoneForm(
                            RegisterPhoneForm.ViewModel(
                                proceedContext = response.proceedContext,
                                factor = factor
                            ),
                            formAction
                        )
                    )
                }
                "voice" -> {
                    FormAction.ProceedTransition.FormTransition(
                        RegisterPhoneForm(
                            RegisterPhoneForm.ViewModel(
                                proceedContext = response.proceedContext,
                                factor = factor
                            ),
                            formAction
                        )
                    )
                }
                else -> unsupportedPolicy()
            }
        }
    }

    fun skip() {
        formAction.skip(viewModel.proceedContext)
    }

    fun signOut() {
        formAction.signOut()
    }
}
