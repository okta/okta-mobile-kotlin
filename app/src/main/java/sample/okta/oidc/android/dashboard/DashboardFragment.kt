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
package sample.okta.oidc.android.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.oidc.android.databinding.FragmentDashboardBinding
import sample.okta.oidc.android.databinding.RowDashboardClaimBinding
import sample.okta.oidc.android.util.BaseFragment
import com.okta.authfoundation.dto.OidcTokenType
import sample.okta.oidc.android.R
import sample.okta.oidc.android.util.inflateBinding

internal class DashboardFragment : BaseFragment<FragmentDashboardBinding>(
    FragmentDashboardBinding::inflate
) {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tokenType.text = TokenViewModel.tokens.tokenType
        binding.expiresIn.text = TokenViewModel.tokens.expiresIn.toString()
        binding.accessToken.text = TokenViewModel.tokens.accessToken
        binding.refreshToken.text = TokenViewModel.tokens.refreshToken
        binding.idToken.text = TokenViewModel.tokens.idToken
        binding.scope.text = TokenViewModel.tokens.scope

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

        binding.introspectAccessTokenButton.setOnClickListener {
            viewModel.introspect(binding.introspectAccessTokenButton.id, OidcTokenType.ACCESS_TOKEN)
        }
        binding.introspectRefreshTokenButton.setOnClickListener {
            viewModel.introspect(binding.introspectRefreshTokenButton.id, OidcTokenType.REFRESH_TOKEN)
        }
        binding.introspectIdTokenButton.setOnClickListener {
            viewModel.introspect(binding.introspectIdTokenButton.id, OidcTokenType.ID_TOKEN)
        }

        binding.revokeAccessTokenButton.setOnClickListener {
            viewModel.revoke(binding.revokeAccessTokenButton.id, OidcTokenType.ACCESS_TOKEN)
        }
        binding.revokeRefreshTokenButton.setOnClickListener {
            viewModel.revoke(binding.revokeRefreshTokenButton.id, OidcTokenType.REFRESH_TOKEN)
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
