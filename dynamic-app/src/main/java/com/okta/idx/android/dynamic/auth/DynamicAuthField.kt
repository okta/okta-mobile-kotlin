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
package com.okta.idx.android.dynamic.auth

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.okta.idx.android.util.emitValidation
import com.okta.idx.kotlin.dto.IdxRemediation

sealed class DynamicAuthField {
    data class Text(
        val label: String,
        val isRequired: Boolean,
        val isSecure: Boolean,
        val errorMessage: String?,
        private val valueUpdater: (String) -> Unit
    ) : DynamicAuthField() {
        private val _errorsLiveData = MutableLiveData(errorMessage ?: "")
        val errorsLiveData: LiveData<String> = _errorsLiveData

        var value: String = ""
            set(value) {
                field = value
                valueUpdater(value)
            }

        override fun validate(): Boolean {
            if (isRequired) {
                return _errorsLiveData.emitValidation { value.isNotEmpty() }
            } else {
                return true
            }
        }
    }

    data class CheckBox(
        val label: String,
        private val valueUpdater: (Boolean) -> Unit
    ) : DynamicAuthField() {
        var value: Boolean = false
            set(value) {
                field = value
                valueUpdater(value)
            }
    }

    data class Options(
        val label: String?,
        val options: List<Option>,
        val isRequired: Boolean,
        val errorMessage: String?,
        private val valueUpdater: (IdxRemediation.Form.Field?) -> Unit,
    ) : DynamicAuthField() {
        data class Option(
            private val field: IdxRemediation.Form.Field,
            val label: String?,
            val fields: List<DynamicAuthField>,
        ) {
            fun update(valueUpdater: (IdxRemediation.Form.Field?) -> Unit) {
                valueUpdater(field)
            }
        }

        private val _errorsLiveData = MutableLiveData(errorMessage ?: "")
        val errorsLiveData: LiveData<String> = _errorsLiveData

        var option: Option? = null
            set(value) {
                field = value
                value?.update(valueUpdater) ?: valueUpdater(null)
            }

        override fun validate(): Boolean {
            if (isRequired) {
                val nestedFieldsAreValid: Boolean = options.flatMap { option ->
                    option.fields.map { field ->
                        field.validate()
                    }
                }.reduce { acc, b ->
                    acc && b
                }
                return _errorsLiveData.emitValidation { option != null } && nestedFieldsAreValid
            } else {
                return true
            }
        }
    }

    data class Action(
        val label: String,
        val onClick: (context: Context) -> Unit
    ) : DynamicAuthField()

    data class Image(
        val label: String,
        val bitmap: Bitmap,
        val sharedSecret: String?,
    ) : DynamicAuthField()

    data class Label(
        val label: String,
    ) : DynamicAuthField()

    open fun validate(): Boolean {
        return true
    }
}
