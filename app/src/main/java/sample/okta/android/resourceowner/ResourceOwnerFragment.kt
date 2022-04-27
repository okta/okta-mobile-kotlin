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
package sample.okta.android.resourceowner

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.databinding.FragmentResourceOwnerBinding
import sample.okta.android.util.BaseFragment

internal class ResourceOwnerFragment : BaseFragment<FragmentResourceOwnerBinding>(
    FragmentResourceOwnerBinding::inflate
) {
    private val viewModel by viewModels<ResourceOwnerViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.login.setOnClickListener {
            viewModel.login(
                username = binding.username.editText?.text?.toString() ?: "",
                password = binding.password.editText?.text?.toString() ?: "",
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.login.isEnabled = true
            when (state) {
                is ResourceOwnerState.Error -> {
                    binding.errorTextView.text = state.message
                }
                ResourceOwnerState.Idle -> {
                }
                ResourceOwnerState.Token -> {
                    findNavController().navigate(ResourceOwnerFragmentDirections.resourceOwnerToDashboard())
                }
                ResourceOwnerState.Loading -> {
                    binding.login.isEnabled = false
                }
            }
        }
    }
}
