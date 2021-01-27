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
package com.okta.idx.android.dynamic

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.databinding.ErrorFieldBinding
import com.okta.idx.android.databinding.FragmentDynamicBinding
import com.okta.idx.android.databinding.LoadingBinding
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.android.util.BaseFragment

internal class DynamicFragment : BaseFragment<FragmentDynamicBinding>(
    FragmentDynamicBinding::inflate
) {
    private val viewModel by viewModels<DynamicViewModel>()
    private val tokenViewModel by activityViewModels<TokenViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.formContent.isSaveFromParentEnabled = false

        viewModel.stateLiveData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DynamicViewModel.State.Form -> {
                    binding.messagesContent.removeAllViews()
                    state.addMessageViews(binding.messagesContent)
                    binding.formContent.removeAllViews()
                    binding.formContent.addView(
                        state.displayableStep.createUi(
                            binding.formContent,
                            viewLifecycleOwner,
                            )
                    )
                    binding.submitButton.visibility = View.VISIBLE
                    binding.cancelButton.visibility = View.VISIBLE
                }
                DynamicViewModel.State.Loading -> {
                    binding.formContent.removeAllViews()
                    addLoadingView(binding.formContent)
                    binding.submitButton.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                }
                is DynamicViewModel.State.Success -> {
                    tokenViewModel.tokenResponse = state.tokenResponse
                    findNavController().navigate(DynamicFragmentDirections.dynamicToDashboard())
                }
            }
        }

        binding.submitButton.setOnClickListener {
            val form = viewModel.stateLiveData.value as DynamicViewModel.State.Form
            if (form.displayableStep.isValid()) {
                viewModel.signIn(form)
            }
        }

        binding.cancelButton.setOnClickListener {
            val form = viewModel.stateLiveData.value as DynamicViewModel.State.Form
            viewModel.cancel(form)
        }
    }

    private fun DynamicViewModel.State.Form.addMessageViews(parent: ViewGroup) {
        parent.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        for (message in messages) {
            val binding = parent.inflateBinding(ErrorFieldBinding::inflate)
            binding.errorTextView.text = message.message
            binding.errorTextView.setTextColor(message.textColor)
            parent.addView(binding.root)
        }
    }

    private fun addLoadingView(parent: ViewGroup) {
        val binding = parent.inflateBinding(LoadingBinding::inflate)
        parent.addView(binding.root)
    }
}
