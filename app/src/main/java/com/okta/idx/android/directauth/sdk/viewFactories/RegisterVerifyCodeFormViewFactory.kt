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
import com.okta.idx.android.databinding.FormRegisterVerifyCodeBinding
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.forms.RegisterVerifyCodeForm
import com.okta.idx.android.directauth.sdk.util.bindText
import com.okta.idx.android.directauth.sdk.util.inflateBinding

internal class RegisterVerifyCodeFormViewFactory :
    FormViewFactory<RegisterVerifyCodeForm> {
    override fun createUi(
        references: FormViewFactory.References,
        form: RegisterVerifyCodeForm
    ): View {
        val binding =
            references.parent.inflateBinding(FormRegisterVerifyCodeBinding::inflate)

        bindText(
            editText = binding.codeEditText,
            textInputLayout = binding.codeTextInputLayout,
            valueField = form.viewModel::code,
            errorsLiveData = form.viewModel.codeErrorsLiveData,
            references = references
        )

        binding.submitButton.setOnClickListener {
            form.verify()
        }

        binding.signOutButton.setOnClickListener {
            form.signOut()
        }

        return binding.root
    }
}
