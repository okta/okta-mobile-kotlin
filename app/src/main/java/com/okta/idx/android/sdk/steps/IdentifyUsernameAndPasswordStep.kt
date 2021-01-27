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
package com.okta.idx.android.sdk.steps

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.okta.idx.android.databinding.StepIdentifyUsernameAndPasswordBinding
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.StepFactory
import com.okta.idx.android.sdk.StepState
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.util.doOnTextChanged
import com.okta.idx.android.sdk.util.emitValidation
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.Credentials
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

class IdentifyUsernameAndPasswordStep private constructor(
    override val viewModel: ViewModel,
) : Step<IdentifyUsernameAndPasswordStep.ViewModel> {
    class Factory : StepFactory<ViewModel> {
        override fun get(remediationOption: RemediationOption): Step<ViewModel>? {
            if (remediationOption.name == "identify") {
                val usernameLabel = remediationOption.form().first { it.name == "identifier" }.label
                val rememberMeLabel =
                    remediationOption.form().first { it.name == "rememberMe" }.label
                if (remediationOption.form().any { it.name == "credentials" }) {
                    val passwordLabel =
                        remediationOption.form().first { it.name == "credentials" }.label
                    return IdentifyUsernameAndPasswordStep(
                        ViewModel(
                            usernameLabel,
                            passwordLabel,
                            rememberMeLabel
                        )
                    )
                } else {
                    return null
                }
            }
            return null
        }
    }

    class ViewModel internal constructor(
        val usernameLabel: String,
        val passwordLabel: String,
        val rememberMeLabel: String,
        var username: String = "",
        var password: String = "",
        var rememberMe: Boolean = false
    ) {
        private val _usernameErrorsLiveData = MutableLiveData<String>("")
        val usernameErrorsLiveData: LiveData<String> = _usernameErrorsLiveData

        private val _passwordErrorsLiveData = MutableLiveData<String>("")
        val passwordErrorsLiveData: LiveData<String> = _passwordErrorsLiveData

        fun isValid(): Boolean {
            _usernameErrorsLiveData.emitValidation { username.isEmpty() }
            _passwordErrorsLiveData.emitValidation { password.isEmpty() }
            return username.isNotEmpty() && password.isNotEmpty()
        }
    }

    override fun proceed(state: StepState): IDXResponse {
        return state.identify(viewModel.username, viewModel.rememberMe) {
            withCredentials(Credentials().apply { passcode = viewModel.password.toCharArray() })
        }
    }

    override fun isValid(): Boolean {
        return viewModel.isValid()
    }
}

class IdentifyUsernameAndPasswordViewFactory :
    ViewFactory<IdentifyUsernameAndPasswordStep.ViewModel> {
    override fun createUi(
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
        viewModel: IdentifyUsernameAndPasswordStep.ViewModel
    ): View {
        val binding = parent.inflateBinding(StepIdentifyUsernameAndPasswordBinding::inflate)

        binding.usernameTextInputLayout.hint = viewModel.usernameLabel
        binding.usernameEditText.setText(viewModel.username)
        binding.usernameEditText.doOnTextChanged { username ->
            viewModel.username = username
        }
        viewModel.usernameErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.usernameTextInputLayout.error = errorMessage
        }

        binding.passwordTextInputLayout.hint = viewModel.passwordLabel
        binding.passwordEditText.setText(viewModel.password)
        binding.passwordEditText.doOnTextChanged { password ->
            viewModel.password = password
        }
        viewModel.passwordErrorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.passwordTextInputLayout.error = errorMessage
        }

        binding.rememberMeCheckBox.isChecked = viewModel.rememberMe
        binding.rememberMeCheckBox.text = viewModel.rememberMeLabel
        binding.rememberMeCheckBox.setOnCheckedChangeListener { _, checked ->
            viewModel.rememberMe = checked
        }

        return binding.root
    }
}
