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
package com.okta.idx.android.dynamic

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.okta.idx.android.dynamic.databinding.FragmentLaunchBinding
import com.okta.idx.android.util.BaseFragment

internal class LaunchFragment : BaseFragment<FragmentLaunchBinding>(
    FragmentLaunchBinding::inflate
) {
    private val viewModel by viewModels<LaunchViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.start()

        binding.loginButton.setOnClickListener {
            val recoveryToken = binding.recoveryToken.editText?.text?.toString() ?: ""
            findNavController().navigate(
                LaunchFragmentDirections.launchToDynamic(
                    recoveryToken = recoveryToken
                )
            )
        }
    }
}
