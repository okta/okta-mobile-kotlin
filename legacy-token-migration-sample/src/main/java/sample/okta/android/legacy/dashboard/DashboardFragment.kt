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
package sample.okta.android.legacy.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.okta.authfoundation.credential.RevokeTokenType
import sample.okta.android.legacy.R
import sample.okta.android.legacy.databinding.FragmentDashboardBinding
import sample.okta.android.legacy.databinding.RowDashboardClaimBinding
import sample.okta.android.legacy.util.BaseFragment
import sample.okta.android.legacy.util.inflateBinding

internal class DashboardFragment : BaseFragment<FragmentDashboardBinding>(
    FragmentDashboardBinding::inflate
) {
    private val viewModel by viewModels<DashboardViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.credentialLiveData.observe(viewLifecycleOwner) { credentialState ->
            when (credentialState) {
                is DashboardViewModel.CredentialState.LoggedOut -> {
                    findNavController().navigate(DashboardFragmentDirections.dashboardToLogin())
                }
                is DashboardViewModel.CredentialState.Loaded -> {
                    val credential = credentialState.credential
                    val token = credential.token ?: return@observe
                    binding.tokenType.text = token.tokenType
                    binding.expiresIn.text = token.expiresIn.toString()
                    binding.accessToken.text = token.accessToken
                    binding.refreshToken.text = token.refreshToken
                    binding.idToken.text = token.idToken
                    binding.scope.text = token.scope

                    if (token.refreshToken == null) {
                        binding.refreshAccessTokenButton.visibility = View.GONE
                        binding.revokeRefreshTokenButton.visibility = View.GONE
                    }
                }
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
            viewModel.revoke(binding.revokeAccessTokenButton.id, RevokeTokenType.ACCESS_TOKEN)
        }
        binding.revokeRefreshTokenButton.setOnClickListener {
            viewModel.revoke(binding.revokeRefreshTokenButton.id, RevokeTokenType.REFRESH_TOKEN)
        }
        binding.logoutWebButton.setOnClickListener {
            viewModel.logoutOfWeb(requireContext())
        }
        binding.deleteCredentialButton.setOnClickListener {
            viewModel.deleteCredential()
        }

        viewModel.requestStateLiveData.observe(viewLifecycleOwner) { state ->
            val button = binding.root.findViewById<View>(viewModel.lastButtonId)
            when (state) {
                DashboardViewModel.RequestState.Loading -> {
                    button?.isEnabled = false
                }
                is DashboardViewModel.RequestState.Result -> {
                    button?.isEnabled = true
                    binding.lastRequestInfo.text = state.text
                }
            }
        }

        binding.backToLogin.setOnClickListener {
            findNavController().navigate(DashboardFragmentDirections.dashboardToLogin())
        }
    }
}
