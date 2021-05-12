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
import com.okta.idx.android.databinding.FormSelectFactorBinding
import com.okta.idx.android.databinding.RowFactorBinding
import com.okta.idx.android.directauth.sdk.FormViewFactory
import com.okta.idx.android.directauth.sdk.forms.SelectFactorForm
import com.okta.idx.android.directauth.sdk.util.inflateBinding
import com.okta.idx.sdk.api.client.Authenticator

internal class SelectFactorFormViewFactory :
    FormViewFactory<SelectFactorForm> {
    override fun createUi(
        references: FormViewFactory.References,
        form: SelectFactorForm
    ): View {
        val binding = references.parent.inflateBinding(FormSelectFactorBinding::inflate)

        for (factor in form.viewModel.factors) {
            binding.root.addView(factor.createView(binding.root, form), binding.root.childCount - 2)
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

    private fun Authenticator.Factor.createView(
        parent: ViewGroup,
        form: SelectFactorForm
    ): View {
        val binding = parent.inflateBinding(RowFactorBinding::inflate)
        binding.typeTextView.text = label
        binding.selectButton.setOnClickListener {
            form.select(this)
        }
        return binding.root
    }
}
