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
package sample.okta.android

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.okta.authfoundation.credential.Credential
import kotlinx.coroutines.launch
import sample.okta.android.databinding.FragmentLaunchBinding
import sample.okta.android.util.BaseFragment

internal class LaunchFragment :
    BaseFragment<FragmentLaunchBinding>(
        FragmentLaunchBinding::inflate
    ) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            if (Credential.default != null) {
                binding.loggedInTextView.visibility = View.VISIBLE
                binding.dashboardButton.visibility = View.VISIBLE
                binding.dashboardButton.setOnClickListener {
                    findNavController().navigate(LaunchFragmentDirections.launchToDashboard())
                }
            }
        }

        binding.loginWithBrowserButton.setOnClickListener {
            findNavController().navigate(LaunchFragmentDirections.launchToBrowser())
        }

        binding.loginWithResourceOwnerFlow.setOnClickListener {
            findNavController().navigate(LaunchFragmentDirections.launchToResourceOwnerFlow())
        }

        binding.loginWithDeviceAuthorizationFlow.setOnClickListener {
            findNavController().navigate(LaunchFragmentDirections.launchToDeviceAuthorizationFlow())
        }
    }
}
