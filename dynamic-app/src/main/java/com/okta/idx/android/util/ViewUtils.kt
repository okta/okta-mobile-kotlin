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
package com.okta.idx.android.util

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.android.material.textfield.TextInputLayout
import kotlin.reflect.KMutableProperty0

internal fun ViewGroup.inflate(@LayoutRes layoutId: Int): View {
    return LayoutInflater.from(context).inflate(layoutId, this, false)
}

internal fun <B> ViewGroup.inflateBinding(
    bindingFactory: (
        inflater: LayoutInflater,
        container: ViewGroup?,
        attachToParent: Boolean
    ) -> B,
    attachToParent: Boolean = false
): B {
    val inflater = LayoutInflater.from(context)
    return bindingFactory(inflater, this, attachToParent)
}

internal fun EditText.doOnTextChanged(listener: (text: String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            listener(s?.toString() ?: "")
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }
    })
}

internal fun bindText(
    editText: EditText,
    textInputLayout: TextInputLayout,
    valueField: KMutableProperty0<String>,
    errorsLiveData: LiveData<String>,
    viewLifecycleOwner: LifecycleOwner
) {
    editText.setText(valueField.get())
    editText.doOnTextChanged { value ->
        valueField.set(value)
    }
    errorsLiveData.observe(viewLifecycleOwner) { errorMessage ->
        textInputLayout.error = errorMessage
    }
}
