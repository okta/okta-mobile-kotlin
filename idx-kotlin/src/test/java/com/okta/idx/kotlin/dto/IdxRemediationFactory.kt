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
package com.okta.idx.kotlin.dto

import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun createRemediation(
    fields: List<IdxRemediation.Form.Field>,
    name: String = "identify",
    authenticators: List<IdxAuthenticator> = emptyList(),
    capabilities: Set<IdxRemediation.Capability> = emptySet(),
): IdxRemediation {
    return IdxRemediation(
        type = IdxRemediation.Type.IDENTIFY,
        name = name,
        form = IdxRemediation.Form(fields),
        authenticators = IdxAuthenticatorCollection(authenticators),
        capabilities = IdxCapabilityCollection(capabilities),
        method = "POST",
        href = "https://test.okta.com/idp/idx/identify".toHttpUrl(),
        accepts = "application/json; okta-version=1.0.0",
    )
}

internal fun createForm(
    fields: List<IdxRemediation.Form.Field>,
): IdxRemediation.Form {
    return IdxRemediation.Form(fields)
}

internal fun createField(
    name: String,
    type: String = "string",
    value: Any? = null,
    form: IdxRemediation.Form? = null,
    authenticator: IdxAuthenticator? = null,
    mutable: Boolean = true,
    isVisible: Boolean = true
): IdxRemediation.Form.Field {
    return IdxRemediation.Form.Field(
        name = name,
        label = "Test",
        type = type,
        _value = value,
        isMutable = mutable,
        isRequired = false,
        isSecret = false,
        form = form,
        options = null,
        messages = IdxMessageCollection(emptyList()),
        authenticator = authenticator,
        isVisible = isVisible,
    )
}
