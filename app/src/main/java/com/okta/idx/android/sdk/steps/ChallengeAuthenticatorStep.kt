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
import com.okta.idx.android.databinding.StepChallengeAuthenticatorBinding
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

class ChallengeAuthenticatorStep private constructor(
    override val viewModel: ViewModel,
) : Step<ChallengeAuthenticatorStep.ViewModel> {
    class Factory : StepFactory<ViewModel> {
        override fun get(remediationOption: RemediationOption): Step<ViewModel>? {
            if (remediationOption.name == "challenge-authenticator") {
                val credentials = remediationOption.form().first { it.name == "credentials" }
                val passcodeLabel = credentials.form.value.first { it.name == "passcode" }.label
                return ChallengeAuthenticatorStep(ViewModel(passcodeLabel))
            }
            return null
        }
    }

    class ViewModel internal constructor(
        val passcodeLabel: String,
        var passcode: String = ""
    ) {
        private val _errorsLiveData = MutableLiveData<String>("")
        val errorsLiveData: LiveData<String> = _errorsLiveData

        fun isValid(): Boolean {
            _errorsLiveData.emitValidation { passcode.isEmpty() }
            return passcode.isNotEmpty()
        }
    }

    override fun proceed(state: StepState): IDXResponse {
        return state.answer(Credentials().apply {
            passcode = viewModel.passcode.toCharArray()
        })
    }

    override fun isValid(): Boolean {
        return viewModel.isValid()
    }
}

class ChallengeAuthenticatorViewFactory : ViewFactory<ChallengeAuthenticatorStep.ViewModel> {
    override fun createUi(
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
        viewModel: ChallengeAuthenticatorStep.ViewModel
    ): View {
        val binding = parent.inflateBinding(StepChallengeAuthenticatorBinding::inflate)

        binding.passcodeTextInputLayout.hint = viewModel.passcodeLabel
        binding.passcodeEditText.setText(viewModel.passcode)
        binding.passcodeEditText.doOnTextChanged { passcode ->
            viewModel.passcode = passcode
        }
        viewModel.errorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.passcodeTextInputLayout.error = errorMessage
        }

        return binding.root
    }
}
