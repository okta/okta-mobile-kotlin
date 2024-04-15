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
package sample.okta.android.legacy.launch

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.okta.authfoundationbootstrap.CredentialBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sample.okta.android.legacy.SampleWebAuthClientHelper
import sample.okta.android.legacy.databinding.FragmentLaunchBinding
import sample.okta.android.legacy.util.BaseFragment

internal class LaunchFragment : BaseFragment<FragmentLaunchBinding>(
    FragmentLaunchBinding::inflate
) {
    private val viewModel by viewModels<LaunchViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateDashboardButtonVisibility()

        viewModel.migratedLiveData.observe(viewLifecycleOwner) {
            updateDashboardButtonVisibility()
        }

        binding.loginWithLegacyBrowserButton.setOnClickListener {
            findNavController().navigate(LaunchFragmentDirections.launchToLegacyBrowser())
        }
        binding.loginWithBrowserButton.setOnClickListener {
            findNavController().navigate(LaunchFragmentDirections.launchToBrowser())
        }
        binding.migrateTokens.setOnClickListener {
            viewModel.migrateTokens(requireContext().applicationContext)
        }
    }

    private fun updateDashboardButtonVisibility() {
        lifecycleScope.launch {
            val token = withContext(Dispatchers.Default) {
                SampleWebAuthClientHelper.webAuthClient.sessionClient.tokens
            }
            if (token != null) {
                binding.loggedInTextView.visibility = View.VISIBLE
                binding.legacyDashboardButton.visibility = View.VISIBLE
                binding.legacyDashboardButton.setOnClickListener {
                    findNavController().navigate(LaunchFragmentDirections.launchToLegacyDashboard())
                }
            }
            if (CredentialBootstrap.defaultCredential() != null) {
                binding.loggedInTextView.visibility = View.VISIBLE
                binding.dashboardButton.visibility = View.VISIBLE
                binding.dashboardButton.setOnClickListener {
                    findNavController().navigate(LaunchFragmentDirections.launchToDashboard())
                }
            }
        }
    }
}
