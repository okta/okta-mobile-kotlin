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
package sample.okta.android.browser

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.databinding.FragmentBrowserBinding
import sample.okta.android.util.BaseFragment

internal class BrowserFragment : BaseFragment<FragmentBrowserBinding>(
    FragmentBrowserBinding::inflate
) {
    private val viewModel by viewModels<BrowserViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginWithBrowserButton.setOnClickListener {
            viewModel.login(requireContext())
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BrowserState.Error -> {
                    binding.errorTextView.text = state.message
                }
                BrowserState.Idle -> {

                }
                is BrowserState.Token -> {
                    findNavController().navigate(BrowserFragmentDirections.browserToDashboard())
                }
            }
        }
    }
}
