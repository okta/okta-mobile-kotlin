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
package com.okta.idx.android.directauth.sdk.viewFactories

import android.view.View
import com.okta.idx.android.databinding.FormLaunchBinding
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.forms.LaunchForm
import com.okta.idx.android.directauth.sdk.util.inflateBinding

internal class LaunchFormViewFactory : FormViewFactory<LaunchForm> {
    override fun createUi(
        references: FormViewFactory.References,
        form: LaunchForm
    ): View {
        val binding = references.parent.inflateBinding(FormLaunchBinding::inflate)

        binding.loginButton.setOnClickListener {
            form.login()
        }

        binding.selfServiceRegistrationButton.setOnClickListener {
            form.register()
        }

        return binding.root
    }
}
