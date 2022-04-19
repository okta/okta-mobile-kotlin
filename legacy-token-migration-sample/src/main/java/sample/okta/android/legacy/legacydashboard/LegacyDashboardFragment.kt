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
package sample.okta.android.legacy.legacydashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.legacy.R
import sample.okta.android.legacy.databinding.FragmentDashboardBinding
import sample.okta.android.legacy.databinding.RowDashboardClaimBinding
import sample.okta.android.legacy.util.BaseFragment
import sample.okta.android.legacy.util.inflateBinding

internal class LegacyDashboardFragment : BaseFragment<FragmentDashboardBinding>(
    FragmentDashboardBinding::inflate
) {
    private val viewModel by viewModels<LegacyDashboardViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.registerCallback(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.tokenLiveData.observe(viewLifecycleOwner) { token ->
            binding.tokenType.text = "Bearer"
            binding.expiresIn.text = token.expiresIn.toString()
            binding.accessToken.text = token.accessToken
            binding.refreshToken.text = token.refreshToken
            binding.idToken.text = token.idToken
            binding.scope.text = token.scope?.joinToString(" ") ?: ""

            if (token.refreshToken == null) {
                binding.refreshAccessTokenButton.visibility = View.GONE
                binding.revokeRefreshTokenButton.visibility = View.GONE
            }
        }

        viewModel.userInfoLiveData.observe(viewLifecycleOwner) { userInfo ->
            binding.claimsTitle.visibility = if (userInfo.isEmpty()) View.GONE else View.VISIBLE
            for (entry in userInfo) {
                val nestedBinding = binding.linearLayout.inflateBinding(RowDashboardClaimBinding::inflate)
                nestedBinding.textViewKey.text = entry.key
                nestedBinding.textViewValue.text = entry.value
                nestedBinding.textViewValue.setTag(R.id.claim, entry.key)
                binding.claimsLinearLayout.addView(nestedBinding.root)
            }
        }

        binding.refreshAccessTokenButton.setOnClickListener {
            viewModel.refresh(binding.refreshAccessTokenButton.id)
        }

        binding.revokeAccessTokenButton.setOnClickListener {
            viewModel.revoke(binding.revokeAccessTokenButton.id, false)
        }
        binding.revokeRefreshTokenButton.setOnClickListener {
            viewModel.revoke(binding.revokeRefreshTokenButton.id, true)
        }
        binding.logoutWebButton.setOnClickListener {
            viewModel.logoutOfWeb(requireActivity())
        }
        binding.deleteCredentialButton.setOnClickListener {
            viewModel.deleteToken()
        }

        viewModel.requestStateLiveData.observe(viewLifecycleOwner) { state ->
            val button = binding.root.findViewById<View>(viewModel.lastButtonId)
            when (state) {
                LegacyDashboardViewModel.RequestState.Loading -> {
                    button?.isEnabled = false
                }
                is LegacyDashboardViewModel.RequestState.Result -> {
                    button?.isEnabled = true
                    binding.lastRequestInfo.text = state.text
                }
            }
        }

        binding.backToLogin.setOnClickListener {
            findNavController().navigate(LegacyDashboardFragmentDirections.legacyDashboardToLogin())
        }
    }
}
