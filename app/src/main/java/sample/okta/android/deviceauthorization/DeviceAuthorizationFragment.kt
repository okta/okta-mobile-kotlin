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
package sample.okta.android.deviceauthorization

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import sample.okta.android.databinding.FragmentDeviceAuthorizationBinding
import sample.okta.android.util.BaseFragment

internal class DeviceAuthorizationFragment : BaseFragment<FragmentDeviceAuthorizationBinding>(
    FragmentDeviceAuthorizationBinding::inflate
) {
    private val viewModel by viewModels<DeviceAuthorizationViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tryAgainButton.setOnClickListener {
            viewModel.start()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.tryAgainButton.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            binding.directionsTextView.visibility = View.GONE
            binding.errorTextView.visibility = View.GONE

            when (state) {
                is DeviceAuthorizationState.Error -> {
                    binding.errorTextView.visibility = View.VISIBLE
                    binding.errorTextView.text = state.message
                    binding.tryAgainButton.visibility = View.VISIBLE
                }
                is DeviceAuthorizationState.Polling -> {
                    binding.directionsTextView.visibility = View.VISIBLE
                    binding.directionsTextView.text = "To sign in, visit ${state.url} and enter ${state.code}"
                }
                DeviceAuthorizationState.Token -> {
                    findNavController().navigate(DeviceAuthorizationFragmentDirections.deviceAuthorizationToDashboard())
                }
                DeviceAuthorizationState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }
    }
}
