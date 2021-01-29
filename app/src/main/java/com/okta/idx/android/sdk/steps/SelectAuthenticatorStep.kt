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
import android.widget.RadioGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.observe
import com.okta.idx.android.R
import com.okta.idx.android.databinding.StepSelectAuthenticatorBinding
import com.okta.idx.android.databinding.StepSelectAuthenticatorItemBinding
import com.okta.idx.android.databinding.StepSelectAuthenticatorMethodBinding
import com.okta.idx.android.databinding.StepSelectAuthenticatorMethodItemBinding
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.StepFactory
import com.okta.idx.android.sdk.StepState
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.util.emitValidation
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.Authenticator
import com.okta.idx.sdk.api.model.FormValue
import com.okta.idx.sdk.api.model.OptionsForm
import com.okta.idx.sdk.api.model.RemediationOption
import com.okta.idx.sdk.api.response.IDXResponse

class SelectAuthenticatorStep private constructor(
    override val viewModel: ViewModel,
) : Step<SelectAuthenticatorStep.ViewModel> {
    class Factory : StepFactory<ViewModel> {
        override fun get(remediationOption: RemediationOption): Step<ViewModel>? {
            if (remediationOption.name == "select-authenticator-authenticate") {
                return SelectAuthenticatorStep(
                    remediationOption.viewModel(AuthenticatorType.Authenticate)
                )
            } else if (remediationOption.name == "select-authenticator-enroll") {
                return SelectAuthenticatorStep(
                    remediationOption.viewModel(AuthenticatorType.Enroll)
                )
            } else {
                return null
            }
        }

        private fun RemediationOption.viewModel(authenticatorType: AuthenticatorType): ViewModel {
            val authenticator = form().first { it.name == "authenticator" }
            return ViewModel(
                authenticatorType = authenticatorType,
                options = authenticator.stepOptions(),
            )
        }

        private fun FormValue.stepOptions(): List<Option> {
            val result = mutableListOf<Option>()
            for (option in options) {
                val formValues = (option.value as OptionsForm).form.value
                result += Option(
                    id = formValues.first { it.name == "id" }.value as String,
                    label = option.label,
                    method = formValues.first { it.name == "methodType" }.asMethod(),
                )
            }
            return result
        }

        private fun FormValue.asMethod(): Option.Method {
            val localValue = value
            if (localValue is String) {
                return Option.Method.Flat(localValue)
            } else {
                val nestedOptions = mutableListOf<Option.Method.Nested.NestedOption>()
                for (option in options) {
                    nestedOptions += Option.Method.Nested.NestedOption(
                        option.label,
                        option.value as String
                    )
                }
                return Option.Method.Nested(
                    options = nestedOptions,
                )
            }
        }
    }

    class ViewModel internal constructor(
        internal val authenticatorType: AuthenticatorType,
        val options: List<Option>,
        var selectedOption: Option? = null
    ) {
        private val _errorsLiveData = MutableLiveData<String>("")
        val errorsLiveData: LiveData<String> = _errorsLiveData

        fun isValid(): Boolean {
            val selectedOptionIsValid = _errorsLiveData.emitValidation { selectedOption != null }
            return selectedOptionIsValid && selectedOption?.isValid() ?: false
        }
    }

    internal sealed class AuthenticatorType {
        object Authenticate : AuthenticatorType() {
            override fun proceed(viewModel: ViewModel, state: StepState): IDXResponse {
                val selectedOption = viewModel.selectedOption!!
                return state.challenge(selectedOption.id, selectedOption.method.value)
            }
        }

        object Enroll : AuthenticatorType() {
            override fun proceed(viewModel: ViewModel, state: StepState): IDXResponse {
                val selectedOption = viewModel.selectedOption!!
                return state.enroll(Authenticator().apply {
                    id = selectedOption.id
                })
            }
        }

        abstract fun proceed(viewModel: ViewModel, state: StepState): IDXResponse
    }

    data class Option(
        val id: String,
        val label: String,
        val method: Method,
    ) {
        val viewId: Int by lazy { View.generateViewId() }

        sealed class Method {
            data class Nested(
                val options: List<NestedOption>,
                var selectedOption: NestedOption? = null,
            ) : Method() {
                data class NestedOption(
                    val label: String,
                    val value: String,
                ) {
                    val viewId: Int by lazy { View.generateViewId() }
                }

                private val _errorsLiveData = MutableLiveData<String>("")
                val errorsLiveData: LiveData<String> = _errorsLiveData

                override val value: String
                    get() = selectedOption!!.value

                override fun isValid(): Boolean {
                    return _errorsLiveData.emitValidation { selectedOption != null }
                }
            }

            data class Flat(
                override val value: String,
            ) : Method() {
                override fun isValid(): Boolean {
                    return true
                }
            }

            abstract val value: String

            abstract fun isValid(): Boolean
        }

        fun isValid(): Boolean {
            return method.isValid()
        }
    }

    override fun proceed(state: StepState): IDXResponse {
        return viewModel.authenticatorType.proceed(viewModel, state)
    }

    override fun isValid(): Boolean {
        return viewModel.isValid()
    }
}

class SelectAuthenticatorViewFactory : ViewFactory<SelectAuthenticatorStep.ViewModel> {
    override fun createUi(
        parent: ViewGroup,
        viewLifecycleOwner: LifecycleOwner,
        viewModel: SelectAuthenticatorStep.ViewModel
    ): View {
        val binding = parent.inflateBinding(StepSelectAuthenticatorBinding::inflate)

        for (option in viewModel.options) {
            val itemBinding =
                binding.radioGroup.inflateBinding(StepSelectAuthenticatorItemBinding::inflate)
            itemBinding.radioButton.id = option.viewId
            itemBinding.radioButton.text = option.label
            binding.radioGroup.addView(itemBinding.root)

            if (option.method is SelectAuthenticatorStep.Option.Method.Nested) {
                val methodBinding =
                    binding.radioGroup.inflateBinding(StepSelectAuthenticatorMethodBinding::inflate)
                itemBinding.radioButton.setTag(R.id.nested_content, methodBinding.root)
                option.method.errorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
                    methodBinding.errorTextView.text = errorMessage
                }

                methodBinding.radioGroup.createViewForNestedMethod(option.method)
                binding.radioGroup.addView(methodBinding.root)
            }
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedOption = viewModel.options.first { it.viewId == checkedId }
            viewModel.selectedOption = selectedOption

            binding.radioGroup.updateNestedVisibility(viewModel)
        }

        viewModel.selectedOption?.also { selectedOption ->
            binding.radioGroup.check(selectedOption.viewId)
        }

        binding.radioGroup.updateNestedVisibility(viewModel)

        viewModel.errorsLiveData.observe(viewLifecycleOwner) { error ->
            binding.errorTextView.text = error
            binding.errorTextView.visibility = if (error.isEmpty()) View.GONE else View.VISIBLE
        }

        return binding.root
    }

    private fun RadioGroup.updateNestedVisibility(viewModel: SelectAuthenticatorStep.ViewModel) {
        for (option in viewModel.options) {
            val nestedView = findViewById<View>(option.viewId)
                ?.getTag(R.id.nested_content) as? View

            nestedView?.visibility = if (viewModel.selectedOption == option) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun RadioGroup.createViewForNestedMethod(
        nestedMethod: SelectAuthenticatorStep.Option.Method.Nested
    ) {
        for (option in nestedMethod.options) {
            val binding = inflateBinding(StepSelectAuthenticatorMethodItemBinding::inflate)
            binding.radioButton.id = option.viewId
            binding.radioButton.text = option.label
            addView(binding.root)
        }

        setOnCheckedChangeListener { _, checkedId ->
            val selectedOption = nestedMethod.options.first { it.viewId == checkedId }
            nestedMethod.selectedOption = selectedOption
        }
    }
}
