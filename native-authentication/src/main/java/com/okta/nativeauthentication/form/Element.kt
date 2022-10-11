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

sealed class Element {
    sealed class Builder<E : Element> {
        internal abstract fun build(): E

        internal open fun setValueFromElement(element: E) {
            // Value setting will happen in subclasses if necessary.
        }

        internal fun setValueFromBaseElement(element: Element) {
            @Suppress("UNCHECKED_CAST")
            setValueFromElement(element as E)
        }

        internal open fun validate(remediation: IdxRemediation): Boolean {
            // Validation happens in subclasses if necessary.
            return true
        }
    }

    internal abstract fun newBuilder(): Builder<*>

    class Loading private constructor() : Element() {
        internal object Builder : Element.Builder<Loading>() {
            override fun build(): Loading {
                return Loading()
            }
        }

        override fun newBuilder(): Element.Builder<*> {
            return Builder
        }

        override fun equals(other: Any?): Boolean {
            return other is Loading
        }

        override fun hashCode(): Int {
            return Loading::class.java.hashCode()
        }
    }

    class Action private constructor(
        private val remediation: IdxRemediation?,
        val text: String,
        val onClick: (Form) -> Unit,
    ) : Element() {
        internal data class Builder(
            val remediation: IdxRemediation?,
            var text: String,
            var onClick: (Form) -> Unit,
        ) : Element.Builder<Action>() {
            override fun build(): Action {
                return Action(
                    remediation = remediation,
                    text = text,
                    onClick = onClick,
                )
            }
        }

        override fun newBuilder(): Element.Builder<*> {
            return Builder(
                remediation = remediation,
                text = text,
                onClick = onClick,
            )
        }
    }

    class TextInput private constructor(
        internal val remediation: IdxRemediation,
        private val idxField: IdxRemediation.Form.Field,
        val label: String,
        val isSecret: Boolean,
        val isRequired: Boolean,
        val errorMessage: String,
        value: String,
    ) : Element() {
        internal data class Builder(
            val remediation: IdxRemediation,
            val idxField: IdxRemediation.Form.Field,
            var label: String,
            var value: String,
            var isSecret: Boolean,
            var isRequired: Boolean,
            var errorMessage: String,
        ) : Element.Builder<TextInput>() {
            override fun build(): TextInput {
                return TextInput(
                    remediation = remediation,
                    idxField = idxField,
                    label = label,
                    isSecret = isSecret,
                    isRequired = isRequired,
                    errorMessage = errorMessage,
                    value = value,
                )
            }

            override fun setValueFromElement(element: TextInput) {
                value = element.value
            }

            override fun validate(remediation: IdxRemediation): Boolean {
                if (isRequired && value.isEmpty()) {
                    if (this.remediation == remediation) {
                        errorMessage = "Field is required."
                        return false
                    }
                }
                return true
            }
        }

        @Volatile var value: String = value
            set(valueToSet) {
                field = valueToSet
                idxField.value = valueToSet
            }

        override fun newBuilder(): Element.Builder<*> {
            return Builder(
                remediation = remediation,
                idxField = idxField,
                label = label,
                isSecret = isSecret,
                isRequired = isRequired,
                errorMessage = errorMessage,
                value = value,
            )
        }
    }

    class Label private constructor(
        private val remediation: IdxRemediation?,
        val text: String,
        val type: Type,
    ) : Element() {
        enum class Type {
            DESCRIPTION, HEADER, ERROR,
        }

        internal data class Builder(
            val remediation: IdxRemediation?,
            var text: String,
            var type: Type = Type.DESCRIPTION,
        ) : Element.Builder<Label>() {
            override fun build(): Label {
                return Label(
                    remediation = remediation,
                    text = text,
                    type = type,
                )
            }
        }

        override fun newBuilder(): Element.Builder<*> {
            return Builder(
                remediation = remediation,
                text = text,
                type = type,
            )
        }
    }

    class Options private constructor(
        val remediation: IdxRemediation?,
        private val valueUpdater: (IdxRemediation.Form.Field?) -> Unit,
        val options: List<Option>,
        val isRequired: Boolean,
        val errorMessage: String,
        option: Option?,
    ) : Element() {
        internal data class Builder(
            val remediation: IdxRemediation?,
            val options: MutableList<Option.Builder> = mutableListOf(),
            var isRequired: Boolean,
            var errorMessage: String,
            private val valueUpdater: (IdxRemediation.Form.Field?) -> Unit,
            var option: Option? = null,
        ) : Element.Builder<Options>() {
            override fun build(): Options {
                return Options(
                    remediation = remediation,
                    valueUpdater = valueUpdater,
                    options = options.map { it.build() },
                    isRequired = isRequired,
                    errorMessage = errorMessage,
                    option = option,
                )
            }

            override fun setValueFromElement(element: Options) {
                option = element.option
            }

            override fun validate(remediation: IdxRemediation): Boolean {
                if (isRequired && option == null) {
                    if (this.remediation == remediation) {
                        errorMessage = "Field is required."
                        return false
                    }
                }
                return true
            }
        }

        class Option private constructor(
            private val field: IdxRemediation.Form.Field,
            val label: String,
            val elements: List<Element>,
        ) {
            internal data class Builder(
                private val field: IdxRemediation.Form.Field,
                var label: String,
                val elements: MutableList<Element.Builder<*>>,
            ) {
                fun build(): Option {
                    return Option(
                        field = field,
                        label = label,
                        elements = elements.map { it.build() },
                    )
                }
            }

            internal fun update(valueUpdater: (IdxRemediation.Form.Field?) -> Unit) {
                valueUpdater(field)
            }

            internal fun newBuilder(): Builder {
                return Builder(
                    field = field,
                    label = label,
                    elements = elements.map { it.newBuilder() }.toMutableList(),
                )
            }
        }

        @Volatile var option: Option? = option
            set(value) {
                field = value
                value?.update(valueUpdater) ?: valueUpdater(null)
            }

        override fun newBuilder(): Element.Builder<*> {
            return Builder(
                remediation = remediation,
                options = options.map { it.newBuilder() }.toMutableList(),
                isRequired = isRequired,
                errorMessage = errorMessage,
                valueUpdater = valueUpdater,
                option = option,
            )
        }
    }
}
