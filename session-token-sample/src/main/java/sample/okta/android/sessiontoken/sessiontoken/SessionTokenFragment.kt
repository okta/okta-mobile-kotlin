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
package sample.okta.android.sessiontoken.sessiontoken

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.sessiontoken.databinding.FragmentSessionTokenBinding
import sample.okta.android.sessiontoken.util.BaseFragment

internal class SessionTokenFragment : BaseFragment<FragmentSessionTokenBinding>(
    FragmentSessionTokenBinding::inflate
) {
    private val viewModel by viewModels<SessionTokenViewModel>()

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
            binding.errorTextView.visibility = View.GONE
            when (state) {
                is SessionTokenState.Error -> {
                    binding.errorTextView.visibility = View.VISIBLE
                    binding.errorTextView.text = state.message
                }
                SessionTokenState.Idle -> {
                }
                SessionTokenState.Token -> {
                    findNavController().navigate(SessionTokenFragmentDirections.sessionTokenToDashboard())
                }
                SessionTokenState.Loading -> {
                    binding.login.isEnabled = false
                }
            }
        }
    }
}
