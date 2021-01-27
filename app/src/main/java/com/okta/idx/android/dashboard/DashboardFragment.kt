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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.TokenViewModel
import com.okta.idx.android.databinding.FragmentDashboardBinding
import com.okta.idx.android.util.BaseFragment

internal class DashboardFragment : BaseFragment<FragmentDashboardBinding>(
    FragmentDashboardBinding::inflate
) {
    private val viewModel by activityViewModels<TokenViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tokenType.text = viewModel.tokenResponse.tokenType
        binding.expiresIn.text = viewModel.tokenResponse.expiresIn.toString()
        binding.accessToken.text = viewModel.tokenResponse.accessToken
        binding.idToken.text = viewModel.tokenResponse.idToken
        binding.scope.text = viewModel.tokenResponse.scope

        binding.signOutButton.setOnClickListener {
            findNavController().navigate(DashboardFragmentDirections.dashboardToScenarioSelect())
        }
    }
}
