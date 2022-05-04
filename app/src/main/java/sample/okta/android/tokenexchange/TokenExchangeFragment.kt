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
package sample.okta.android.tokenexchange

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.databinding.FragmentTokenExchangeBinding
import sample.okta.android.util.BaseFragment

internal class TokenExchangeFragment : BaseFragment<FragmentTokenExchangeBinding>(
    FragmentTokenExchangeBinding::inflate
) {
    private val viewModel by viewModels<TokenExchangeViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tryAgainButton.setOnClickListener {
            viewModel.start()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.tryAgainButton.visibility = View.GONE
            binding.errorTextView.visibility = View.GONE
            binding.progressBar.visibility = View.GONE

            when (state) {
                is TokenExchangeState.Error -> {
                    binding.errorTextView.text = state.message
                    binding.errorTextView.visibility = View.VISIBLE
                    binding.tryAgainButton.visibility = View.VISIBLE
                }
                is TokenExchangeState.Token -> {
                    findNavController().navigate(
                        TokenExchangeFragmentDirections.tokenExchangeToDashboard(state.nameTagValue)
                    )
                }
                TokenExchangeState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }
    }
}
