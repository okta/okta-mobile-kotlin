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
package com.okta.idx.android.signin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.databinding.FragmentSignInBinding
import com.okta.idx.android.util.BaseFragment

internal class SignInFragment : BaseFragment<FragmentSignInBinding>(
    FragmentSignInBinding::inflate
) {
    private val viewModel by viewModels<SignInViewModel>()
    private val tokenViewModel by activityViewModels<TokenViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.stateLiveData.observe(viewLifecycleOwner) { state ->
            when (state) {
                SignInState.Idle -> {
                    resetFormState()
                }
                SignInState.Loading -> {
                    // TODO: Show loading
                    binding.usernameEditText.isEnabled = false
                    binding.passwordEditText.isEnabled = false
                    binding.signInButton.isEnabled = false
                }
                is SignInState.Success -> {
                    tokenViewModel.tokenResponse = state.tokenResponse
                    findNavController().navigate(SignInFragmentDirections.signInToDashboard())
                }
                is SignInState.Failure -> {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.acknowledgeFailure()
                }
            }
        }

        // TODO: Wire up IME submit to usernamePasswordButton.

        binding.signInButton.setOnClickListener {
            val username = binding.usernameEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            var formIsComplete = true

            if (username.isBlank()) {
                formIsComplete = false
                binding.usernameEditText.error = "Username is required."
            }

            if (password.isBlank()) {
                formIsComplete = false
                binding.passwordEditText.error = "Password is required."
            }

            if (formIsComplete) {
                resetFormState()
                viewModel.signIn(username = username, password = password)
            }
        }
    }

    private fun resetFormState() {
        binding.usernameEditText.error = null
        binding.passwordEditText.error = null
        binding.usernameEditText.isEnabled = true
        binding.passwordEditText.isEnabled = true
        binding.signInButton.isEnabled = true
    }
}
