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
import kotlinx.coroutines.CoroutineScope
import java.util.Objects

class Form private constructor(
    val elements: List<Element>,
    private val builtFromWithoutTransform: Builder,
    private val builderToElementMap: Map<Element.Builder<*>, Element>,
) {
    internal data class Builder(
        val elements: MutableList<Element.Builder<*>> = mutableListOf(),
        val launchActions: MutableList<suspend CoroutineScope.() -> Unit> = mutableListOf(),
    ) {
        fun build(formTransformers: List<FormTransformer>): Form {
            val builtFromWithoutTransform = Builder(
                elements = elements.toMutableList(),
                launchActions = launchActions.toMutableList()
            )

            for (formFactory in formTransformers) {
                formFactory.apply {
                    transform()
                }
            }
            val builderToElementMap = mutableMapOf<Element.Builder<*>, Element>()
            val builtElements = elements.map { elementBuilder ->
                val element = elementBuilder.build()
                builderToElementMap[elementBuilder] = element
                element
            }
            return Form(builtElements, builtFromWithoutTransform, builderToElementMap)
        }
    }

    internal data class ValidationResult(val formIsValid: Boolean, val updatedFormBuilder: Builder)

    private fun newBuilder(): Builder {
        val builder = Builder(
            elements = builtFromWithoutTransform.elements.toMutableList(),
            launchActions = builtFromWithoutTransform.launchActions.toMutableList()
        )
        for (elementBuilder in builder.elements) {
            builderToElementMap[elementBuilder]?.let { element ->
                elementBuilder.setValueFromBaseElement(element)
            }
        }
        return builder
    }

    internal fun validate(remediation: IdxRemediation): ValidationResult {
        var formIsValid = true
        val updatedForm = newBuilder()
        for (element in updatedForm.elements) {
            if (!element.validate(remediation)) {
                formIsValid = false
            }
        }
        return ValidationResult(formIsValid, updatedForm)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true

        if (other is Form) {
            return other.elements == elements && other.builtFromWithoutTransform == builtFromWithoutTransform
        }

        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(elements, builtFromWithoutTransform)
    }
}
