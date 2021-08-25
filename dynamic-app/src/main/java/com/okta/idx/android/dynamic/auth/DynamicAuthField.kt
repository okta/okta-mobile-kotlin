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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.okta.idx.android.util.emitValidation

sealed class DynamicAuthField {
    data class Text(
        val label: String,
        val isRequired: Boolean,
        val isSecure: Boolean,
        private val valueUpdater: (String) -> Unit
    ) : DynamicAuthField() {
        private val _errorsLiveData = MutableLiveData("")
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

    data class Action(
        val label: String,
        val onClick: () -> Unit
    ) : DynamicAuthField()

    open fun validate(): Boolean {
        return true
    }
}
