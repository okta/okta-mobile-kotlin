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

import com.okta.idx.kotlin.dto.IdxResponse

sealed class DynamicAuthState {
    object Loading : DynamicAuthState()

    data class Form(
        internal val idxResponse: IdxResponse,
        val fields: List<DynamicAuthField>,
        val messages: List<String>,
    ) : DynamicAuthState()

    data class Error(val message: String) : DynamicAuthState()

    object Tokens : DynamicAuthState()
}
