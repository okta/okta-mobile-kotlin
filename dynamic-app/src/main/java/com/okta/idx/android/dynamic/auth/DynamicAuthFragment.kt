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
package com.okta.idx.android.dynamic.auth

import android.graphics.Color
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.viewModels
import com.google.android.material.textfield.TextInputLayout
import com.okta.idx.android.dynamic.databinding.ErrorBinding
import com.okta.idx.android.dynamic.databinding.ErrorFieldBinding
import com.okta.idx.android.dynamic.databinding.FormActionPrimaryBinding
import com.okta.idx.android.dynamic.databinding.FormCheckBoxBinding
import com.okta.idx.android.dynamic.databinding.FormTextBinding
import com.okta.idx.android.dynamic.databinding.FragmentDynamicAuthBinding
import com.okta.idx.android.dynamic.databinding.LoadingBinding
import com.okta.idx.android.util.BaseFragment
import com.okta.idx.android.util.bindText
import com.okta.idx.android.util.inflateBinding

internal class DynamicAuthFragment : BaseFragment<FragmentDynamicAuthBinding>(
    FragmentDynamicAuthBinding::inflate
) {
    private val viewModel by viewModels<DynamicAuthViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DynamicAuthState.Form -> {
                    addMessageViews(state.messages)
                    binding.formContent.removeAllViews()
                    for (field in state.fields) {
                        binding.formContent.addView(field.createView())
                    }
                }
                is DynamicAuthState.Error -> {
                    addErrorView()
                }
                DynamicAuthState.Loading -> {
                    addLoadingView()
                }
            }
        }
    }

    private fun addMessageViews(messages: List<String>) {
        val parent = binding.messagesContent
        parent.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        parent.removeAllViews()
        for (message in messages) {
            val binding = parent.inflateBinding(ErrorFieldBinding::inflate)
            binding.errorTextView.text = message
            binding.errorTextView.setTextColor(Color.RED)
            parent.addView(binding.root)
        }
    }

    private fun addErrorView() {
        val parent = binding.formContent
        parent.removeAllViews()
        val binding = parent.inflateBinding(ErrorBinding::inflate)
        binding.button.setOnClickListener {
            viewModel.resume()
        }
        parent.addView(binding.root)
    }

    private fun addLoadingView() {
        binding.messagesContent.removeAllViews()
        val parent = binding.formContent
        parent.removeAllViews()
        val binding = parent.inflateBinding(LoadingBinding::inflate)
        parent.addView(binding.root)
    }

    private fun DynamicAuthField.createView(): View {
        return when (this) {
            is DynamicAuthField.Text -> {
                val textBinding = binding.formContent.inflateBinding(FormTextBinding::inflate)

                textBinding.textInputLayout.hint = label

                if (isSecure) {
                    textBinding.textInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                    textBinding.editText.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                    textBinding.editText.transformationMethod = PasswordTransformationMethod.getInstance()
                }

                bindText(
                    editText = textBinding.editText,
                    textInputLayout = textBinding.textInputLayout,
                    valueField = ::value,
                    errorsLiveData = errorsLiveData,
                    viewLifecycleOwner = viewLifecycleOwner,
                )

                textBinding.root
            }
            is DynamicAuthField.CheckBox -> {
                val actionBinding = binding.formContent.inflateBinding(FormCheckBoxBinding::inflate)
                actionBinding.checkbox.text = label
                actionBinding.checkbox.isChecked = value
                actionBinding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                    value = isChecked
                }
                actionBinding.root
            }
            is DynamicAuthField.Action -> {
                val actionBinding = binding.formContent.inflateBinding(FormActionPrimaryBinding::inflate)
                actionBinding.button.text = label
                actionBinding.button.setOnClickListener { onClick() }
                actionBinding.root
            }
        }
    }
}
