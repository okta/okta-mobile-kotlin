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
import android.view.ViewGroup
import com.okta.idx.android.databinding.FormSelectAuthenticatorBinding
import com.okta.idx.android.databinding.RowFactorBinding
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.forms.SelectAuthenticatorForm
import com.okta.idx.android.directauth.sdk.util.inflateBinding
import com.okta.idx.sdk.api.client.Authenticator

internal class SelectAuthenticatorFormViewFactory :
    FormViewFactory<SelectAuthenticatorForm> {
    override fun createUi(
        references: FormViewFactory.References,
        form: SelectAuthenticatorForm
    ): View {
        val binding = references.parent.inflateBinding(FormSelectAuthenticatorBinding::inflate)

        for (authenticator in form.viewModel.authenticators.filterToSupported()) {
            binding.root.addView(authenticator.createView(binding.root, form), binding.root.childCount - 2)
        }

        binding.skipButton.visibility = if (form.viewModel.canSkip) View.VISIBLE else View.GONE
        binding.skipButton.setOnClickListener {
            form.skip()
        }

        binding.signOutButton.setOnClickListener {
            form.signOut()
        }

        return binding.root
    }

    private fun List<Authenticator>.filterToSupported(): List<Authenticator> {
        val supported = setOf("sms", "voice", "password", "email")
        val result = mutableListOf<Authenticator>()
        for (authenticator in this) {
            for (factor in authenticator.factors) {
                if (supported.contains(factor.method)) {
                    result += authenticator
                    break
                }
            }
        }
        return result
    }

    private fun Authenticator.createView(
        parent: ViewGroup,
        form: SelectAuthenticatorForm
    ): View {
        val binding = parent.inflateBinding(RowFactorBinding::inflate)
        binding.typeTextView.text = label
        binding.selectButton.setOnClickListener {
            form.select(this)
        }
        return binding.root
    }
}
