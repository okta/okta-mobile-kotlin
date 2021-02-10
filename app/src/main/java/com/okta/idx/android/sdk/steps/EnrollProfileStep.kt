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
import com.okta.idx.android.databinding.StepEnrollProfileAttributeBinding
import com.okta.idx.android.databinding.StepEnrollProfileBinding
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.StepFactory
import com.okta.idx.android.sdk.StepState
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.util.doOnTextChanged
import com.okta.idx.android.sdk.util.emitValidation
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

class EnrollProfileStep private constructor(
    val viewModel: ViewModel,
) : Step {
    class Factory : StepFactory<EnrollProfileStep> {
        override fun get(remediationOption: RemediationOption): EnrollProfileStep? {
            if (remediationOption.name == "enroll-profile") {
                val attributes = remediationOption.attributes()
                return EnrollProfileStep(ViewModel(remediationOption, attributes))
            }
            return null
        }

        private fun RemediationOption.attributes(): List<Attribute> {
            val result = mutableListOf<Attribute>()
            val userProfile = form().first { it.name == "userProfile" }
            for (formValue in userProfile.form().value) {
                result += Attribute(
                    formValue.name,
                    formValue.label,
                    formValue.required
                )
            }
            return result
        }
    }

    class ViewModel internal constructor(
        internal val remediationOption: RemediationOption,
        val attributes: List<Attribute>,
    ) {
        fun isValid(): Boolean {
            return attributes.all { it.isValid() }
        }
    }

    class Attribute internal constructor(
        val name: String,
        val label: String,
        val required: Boolean,
        var value: String = "",
    ) {
        private val _errorsLiveData = MutableLiveData<String>("")
        val errorsLiveData: LiveData<String> = _errorsLiveData

        fun isValid(): Boolean {
            return if (required) {
                _errorsLiveData.emitValidation { value.isNotEmpty() }
            } else {
                true
            }
        }
    }

    override fun proceed(state: StepState): IDXResponse {
        val attributes = mutableMapOf<String, Any>()
        for (attribute in viewModel.attributes) {
            attributes[attribute.name] = attribute.value
        }
        return state.enrollUserProfile(viewModel.remediationOption, attributes)
    }

    override fun isValid(): Boolean {
        return viewModel.isValid()
    }
}

class EnrollProfileViewFactory : ViewFactory<EnrollProfileStep> {
    override fun createUi(
        references: ViewFactory.References,
        step: EnrollProfileStep
    ): View {
        val binding = references.parent.inflateBinding(StepEnrollProfileBinding::inflate)
        for (attribute in step.viewModel.attributes) {
            binding.attributesLayout.addView(
                createAttributeUi(
                    parent = binding.attributesLayout,
                    viewLifecycleOwner = references.viewLifecycleOwner,
                    attribute = attribute
                )
            )
        }
        return binding.root
    }

    private fun createAttributeUi(
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
        attribute: EnrollProfileStep.Attribute,
    ): View {
        val binding = parent.inflateBinding(StepEnrollProfileAttributeBinding::inflate)
        binding.textInputLayout.hint = attribute.label
        binding.editText.setText(attribute.value)
        binding.editText.doOnTextChanged { value ->
            attribute.value = value
        }
        attribute.errorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
            binding.textInputLayout.error = errorMessage
        }
        return binding.root
    }
}
