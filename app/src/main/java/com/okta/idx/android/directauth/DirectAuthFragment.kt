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
package com.okta.idx.android.directauth

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.databinding.ErrorBinding
import com.okta.idx.android.databinding.ErrorFieldBinding
import com.okta.idx.android.databinding.FragmentDirectAuthBinding
import com.okta.idx.android.databinding.LoadingBinding
import com.okta.idx.android.directauth.sdk.FormAction
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.IdxFormRegistry
import com.okta.idx.android.directauth.sdk.util.inflateBinding
import com.okta.idx.android.util.BaseFragment

internal class DirectAuthFragment : BaseFragment<FragmentDirectAuthBinding>(
    FragmentDirectAuthBinding::inflate
) {
    private val viewModel by viewModels<DirectAuthViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.formContent.isSaveFromParentEnabled = false

        viewModel.stateLiveData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FormAction.State.Data -> {
                    binding.messagesContent.removeAllViews()
                    addMessageViews(state.messages)

                    binding.formContent.removeAllViews()
                    binding.formContent.addView(
                        IdxFormRegistry.getDisplayableForm(state.form).createUi(
                            FormViewFactory.References(
                                binding.formContent,
                                viewLifecycleOwner,
                            )
                        )
                    )
                }
                FormAction.State.Loading -> {
                    addLoadingView()
                }
                is FormAction.State.Success -> {
                    TokenViewModel._tokenResponse = state.tokenResponse
                    findNavController().navigate(DirectAuthFragmentDirections.directAuthToDashboard())
                }
                is FormAction.State.FailedToLoad -> {
                    addMessageViews(state.messages)
                    addErrorView()
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

    private fun addLoadingView() {
        binding.messagesContent.removeAllViews()
        val parent = binding.formContent
        parent.removeAllViews()
        val binding = parent.inflateBinding(LoadingBinding::inflate)
        parent.addView(binding.root)
    }

    private fun addErrorView() {
        val parent = binding.formContent
        parent.removeAllViews()
        val binding = parent.inflateBinding(ErrorBinding::inflate)
        binding.button.setOnClickListener {
            viewModel.signOut()
        }
        parent.addView(binding.root)
    }
}
