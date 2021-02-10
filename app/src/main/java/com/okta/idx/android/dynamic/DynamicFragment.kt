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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.databinding.ErrorBinding
import com.okta.idx.android.databinding.ErrorFieldBinding
import com.okta.idx.android.databinding.FragmentDynamicBinding
import com.okta.idx.android.databinding.LoadingBinding
import com.okta.idx.android.sdk.DisplayableStep
import com.okta.idx.android.sdk.Step
import com.okta.idx.android.sdk.ViewFactory
import com.okta.idx.android.sdk.steps.ButtonStep
import com.okta.idx.android.sdk.util.inflateBinding
import com.okta.idx.android.util.BaseFragment

internal class DynamicFragment : BaseFragment<FragmentDynamicBinding>(
    FragmentDynamicBinding::inflate
), ViewFactory.Callback {
    private val viewModel by viewModels<DynamicViewModel>()
    private val tokenViewModel by activityViewModels<TokenViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.formContent.isSaveFromParentEnabled = false

        viewModel.stateLiveData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DynamicViewModel.State.Form -> {
                    binding.messagesContent.removeAllViews()
                    addMessageViews(state.messages)

                    binding.formContent.removeAllViews()
                    binding.formContent.addView(
                        state.displayableSteps[0].createUi(
                            ViewFactory.References(
                                binding.formContent,
                                viewLifecycleOwner,
                                this
                            )
                        )
                    )

                    binding.buttonLayout.removeAllViews()
                    val viewFactoryReferences = ViewFactory.References(
                        binding.buttonLayout,
                        viewLifecycleOwner,
                        this
                    )
                    for (buttonStep in state.buttonSteps()) {
                        binding.buttonLayout.addView(buttonStep.createUi(viewFactoryReferences))
                    }

                    binding.submitButton.visibility = View.VISIBLE
                    binding.cancelButton.visibility = View.VISIBLE
                    binding.buttonLayout.visibility = View.VISIBLE
                }
                DynamicViewModel.State.Loading -> {
                    addLoadingView()
                    binding.submitButton.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.buttonLayout.visibility = View.GONE
                }
                is DynamicViewModel.State.Success -> {
                    tokenViewModel.tokenResponse = state.tokenResponse
                    findNavController().navigate(DynamicFragmentDirections.dynamicToDashboard())
                }
                is DynamicViewModel.State.FailedToLoad -> {
                    addMessageViews(state.messages)
                    addErrorView()
                    binding.submitButton.visibility = View.GONE
                    binding.cancelButton.visibility = View.GONE
                    binding.buttonLayout.visibility = View.GONE
                }
            }
        }

        binding.submitButton.setOnClickListener {
            val form = viewModel.stateLiveData.value as DynamicViewModel.State.Form
            val step = form.displayableSteps[0].step
            if (step.isValid()) {
                viewModel.signIn(form, step)
            }
        }

        binding.cancelButton.setOnClickListener {
            val form = viewModel.stateLiveData.value as DynamicViewModel.State.Form
            viewModel.cancel(form)
        }
    }

    private fun addMessageViews(messages: List<DynamicViewModel.State.Message>) {
        val parent = binding.messagesContent
        parent.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        parent.removeAllViews()
        for (message in messages) {
            val binding = parent.inflateBinding(ErrorFieldBinding::inflate)
            binding.errorTextView.text = message.message
            binding.errorTextView.setTextColor(message.textColor)
            parent.addView(binding.root)
        }
    }

    private fun addLoadingView() {
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
            viewModel.start()
        }
        parent.addView(binding.root)
    }

    override fun proceed(step: Step) {
        val form = viewModel.stateLiveData.value as DynamicViewModel.State.Form
        if (step.isValid()) {
            viewModel.signIn(form, step)
        }
    }
}

private fun DynamicViewModel.State.Form.buttonSteps(): List<DisplayableStep<*>> {
    return displayableSteps.filter { it.step is ButtonStep }.toList()
}
