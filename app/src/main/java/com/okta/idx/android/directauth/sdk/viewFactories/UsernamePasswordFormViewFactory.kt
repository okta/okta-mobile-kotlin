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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import com.okta.idx.android.R
import com.okta.idx.android.databinding.FormUsernameAndPasswordBinding
import com.okta.idx.android.databinding.RowFactorBinding
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.forms.UsernamePasswordForm
import com.okta.idx.android.directauth.sdk.util.bindText
import com.okta.idx.android.directauth.sdk.util.inflateBinding
import com.okta.idx.sdk.api.model.Idp
import timber.log.Timber
import java.util.Locale

internal class UsernamePasswordFormViewFactory : FormViewFactory<UsernamePasswordForm> {
    override fun createUi(
        references: FormViewFactory.References,
        form: UsernamePasswordForm
    ): View {
        val binding = references.parent.inflateBinding(FormUsernameAndPasswordBinding::inflate)

        bindText(
            editText = binding.usernameEditText,
            textInputLayout = binding.usernameTextInputLayout,
            valueField = form.viewModel::username,
            errorsLiveData = form.viewModel.usernameErrorsLiveData,
            references = references
        )

        bindText(
            editText = binding.passwordEditText,
            textInputLayout = binding.passwordTextInputLayout,
            valueField = form.viewModel::password,
            errorsLiveData = form.viewModel.passwordErrorsLiveData,
            references = references
        )

        for (idp in form.viewModel.socialIdps) {
            binding.idpLayout.addView(idp.createView(binding.idpLayout, form.viewModel))
        }

        binding.submitButton.setOnClickListener {
            form.signIn()
        }

        binding.signUpButton.setOnClickListener {
            form.register()
        }

        binding.forgotPasswordButton.setOnClickListener {
            form.forgotPassword()
        }

        return binding.root
    }

    private fun Idp.createView(
        parent: ViewGroup,
        viewModel: UsernamePasswordForm.ViewModel,
    ): View {
        val binding = parent.inflateBinding(RowFactorBinding::inflate)
        val idpName = type.toLowerCase(Locale.US).capitalize(Locale.US)
        binding.typeTextView.text = parent.context.getString(R.string.login_with_idp, idpName)
        binding.selectButton.setOnClickListener {
            viewModel.hasSelectedIdp = true

            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(href))
                parent.context.startActivity(browserIntent)
            } catch (e: ActivityNotFoundException) {
                Timber.e(e, "Failed to load URL.")
            }
        }
        return binding.root
    }
}
