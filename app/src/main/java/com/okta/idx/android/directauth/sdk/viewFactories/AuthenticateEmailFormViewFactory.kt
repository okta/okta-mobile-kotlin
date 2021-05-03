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
import com.okta.idx.android.databinding.FormAuthenticateEmailBinding
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.forms.AuthenticateEmailForm
import com.okta.idx.android.directauth.sdk.util.bindText
import com.okta.idx.android.directauth.sdk.util.inflateBinding

internal class AuthenticateEmailFormViewFactory :
    FormViewFactory<AuthenticateEmailForm> {
    override fun createUi(
        references: FormViewFactory.References,
        form: AuthenticateEmailForm
    ): View {
        val binding =
            references.parent.inflateBinding(FormAuthenticateEmailBinding::inflate)

        bindText(
            editText = binding.emailCodeEditText,
            textInputLayout = binding.emailCodeInputLayout,
            valueField = form.viewModel::code,
            errorsLiveData = form.viewModel.codeErrorsLiveData,
            references = references
        )

        binding.submitButton.setOnClickListener {
            form.authenticate()
        }

        binding.signOutButton.setOnClickListener {
            form.signOut()
        }

        return binding.root
    }
}
