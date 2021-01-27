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
package com.okta.idx.android.scenario

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.databinding.FragmentScenarioSelectBinding
import com.okta.idx.android.dynamic.EnrollMockScenario
import com.okta.idx.android.dynamic.MfaEmailMockScenario
import com.okta.idx.android.network.mock.OktaMockWebServer
import com.okta.idx.android.signin.SignInMockScenario
import com.okta.idx.android.util.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ScenarioSelectFragment : BaseFragment<FragmentScenarioSelectBinding>(
    FragmentScenarioSelectBinding::inflate
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.oktaBackendButton.setOnClickListener {
            lifecycleScope.launch {
                binding.progress.visibility = View.VISIBLE
                binding.scenarioContent.visibility = View.GONE

                withContext(Dispatchers.IO) {
                    OktaMockWebServer.mockingEnabled.set(false)
                }

                findNavController().navigate(ScenarioSelectFragmentDirections.scenarioSelectToDynamic())
            }
        }

        binding.usernamePasswordButton.setOnClickListener {
            lifecycleScope.launch {
                binding.progress.visibility = View.VISIBLE
                binding.scenarioContent.visibility = View.GONE

                withContext(Dispatchers.IO) {
                    OktaMockWebServer.mockingEnabled.set(true)
                    SignInMockScenario.prepare()
                }

                findNavController().navigate(ScenarioSelectFragmentDirections.scenarioSelectToSignIn())
            }
        }

        binding.mfaButton.setOnClickListener {
            lifecycleScope.launch {
                binding.progress.visibility = View.VISIBLE
                binding.scenarioContent.visibility = View.GONE

                withContext(Dispatchers.IO) {
                    OktaMockWebServer.mockingEnabled.set(true)
                    MfaEmailMockScenario.prepare()
                }

                findNavController().navigate(ScenarioSelectFragmentDirections.scenarioSelectToDynamic())
            }
        }

        binding.enrollButton.setOnClickListener {
            lifecycleScope.launch {
                binding.progress.visibility = View.VISIBLE
                binding.scenarioContent.visibility = View.GONE

                withContext(Dispatchers.IO) {
                    OktaMockWebServer.mockingEnabled.set(true)
                    EnrollMockScenario.prepare()
                }

                findNavController().navigate(ScenarioSelectFragmentDirections.scenarioSelectToDynamic())
            }
        }
    }
}
