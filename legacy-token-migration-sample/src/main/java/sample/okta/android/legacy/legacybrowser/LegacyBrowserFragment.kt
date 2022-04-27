/*
 * Copyright 2022-Present Okta, Inc.
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
package sample.okta.android.legacy.legacybrowser

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.legacy.databinding.FragmentBrowserBinding
import sample.okta.android.legacy.util.BaseFragment

class LegacyBrowserFragment : BaseFragment<FragmentBrowserBinding>(
    FragmentBrowserBinding::inflate
) {
    private val viewModel by viewModels<LegacyBrowserViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.registerCallback(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginWithBrowserButton.setOnClickListener {
            viewModel.login(requireActivity())
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility = View.GONE
            binding.errorTextView.visibility = View.GONE
            when (state) {
                is BrowserState.Error -> {
                    binding.errorTextView.visibility = View.VISIBLE
                    binding.errorTextView.text = state.message
                }
                BrowserState.Idle -> {
                }
                BrowserState.Token -> {
                    findNavController().navigate(LegacyBrowserFragmentDirections.legacyBrowserToDashboard())
                }
                BrowserState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }
    }
}
