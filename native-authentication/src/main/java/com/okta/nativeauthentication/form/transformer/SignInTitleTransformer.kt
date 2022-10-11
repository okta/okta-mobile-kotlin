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
package com.okta.nativeauthentication.form.transformer

import com.okta.nativeauthentication.form.Element
import com.okta.nativeauthentication.form.Form
import com.okta.nativeauthentication.form.FormTransformer

internal class SignInTitleTransformer : FormTransformer {
    override fun Form.Builder.transform() {
        val usernameIndex = elements.indexOfFirst { elementBuilder ->
            val textInputElementBuilder = (elementBuilder as? Element.TextInput.Builder) ?: return@indexOfFirst false
            textInputElementBuilder.idxField.name == "identifier" && textInputElementBuilder.remediation.name == "identify"
        }
        if (usernameIndex >= 0) {
            val elementBuilder = Element.Label.Builder(
                remediation = null,
                text = "Sign In",
                type = Element.Label.Type.HEADER,
            )
            elements.add(usernameIndex, elementBuilder)
        }
    }
}
