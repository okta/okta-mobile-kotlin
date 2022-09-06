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
package com.okta.nativeauthentication.form

import com.okta.idx.kotlin.dto.IdxRemediation

sealed interface Element {
    sealed class Builder<E : Element> {
        internal abstract fun build(): E
    }

    class Action private constructor(
        val text: String,
        val onClick: () -> Unit,
    ) : Element {
        internal class Builder : Element.Builder<Action>() {
            var text: String = ""
            var onClick: () -> Unit = {}

            override fun build(): Action {
                return Action(text, onClick)
            }
        }
    }

    class TextInput private constructor(
        private val idxField: IdxRemediation.Form.Field,
        val label: String,
        val isSecret: Boolean,
        value: String,
    ) : Element {
        internal class Builder(
            private val idxField: IdxRemediation.Form.Field,
        ) : Element.Builder<TextInput>() {
            var label: String = ""
            var value: String = ""
            var isSecret: Boolean = false

            override fun build(): TextInput {
                return TextInput(idxField, label, isSecret, value)
            }
        }

        @Volatile var value: String = value
            set(valueToSet) {
                field = valueToSet
                idxField.value = valueToSet
            }
    }

    class Label private constructor(
        val text: String,
    ) : Element {
        internal class Builder : Element.Builder<Label>() {
            var text: String = ""

            override fun build(): Label {
                return Label(text)
            }
        }
    }
}
