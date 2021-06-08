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
package com.okta.idx.android.dashboard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.R
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.databinding.FragmentDashboardBinding
import com.okta.idx.android.databinding.RowDashboardClaimBinding
import com.okta.idx.android.directauth.sdk.util.inflateBinding
import com.okta.idx.android.util.BaseFragment

internal class DashboardFragment : BaseFragment<FragmentDashboardBinding>(
    FragmentDashboardBinding::inflate
) {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tokenType.text = TokenViewModel.tokenResponse.tokenType
        binding.expiresIn.text = TokenViewModel.tokenResponse.expiresIn.toString()
        binding.accessToken.text = TokenViewModel.tokenResponse.accessToken
        binding.refreshToken.text = TokenViewModel.tokenResponse.refreshToken
        binding.idToken.text = TokenViewModel.tokenResponse.idToken
        binding.scope.text = TokenViewModel.tokenResponse.scope

        binding.signOutButton.setOnClickListener {
            viewModel.logout()
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

        viewModel.logoutStateLiveData.observe(viewLifecycleOwner) { state ->
            when (state) {
                DashboardViewModel.LogoutState.Failed -> {
                    binding.signOutButton.isEnabled = true
                    Toast.makeText(requireContext(), "Logout failed.", Toast.LENGTH_LONG).show()
                }
                DashboardViewModel.LogoutState.Idle -> {
                    binding.signOutButton.isEnabled = true
                }
                DashboardViewModel.LogoutState.Loading -> {
                    binding.signOutButton.isEnabled = false
                }
                DashboardViewModel.LogoutState.Success -> {
                    viewModel.acknowledgeLogoutSuccess()
                    findNavController().navigate(DashboardFragmentDirections.dashboardToLogin())
                }
            }
        }
    }
}
