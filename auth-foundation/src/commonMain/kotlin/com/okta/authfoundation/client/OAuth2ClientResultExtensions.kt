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
package com.okta.authfoundation.client

import com.okta.authfoundation.InternalAuthFoundationApi

/**
 * Converts an [OAuth2ClientResult] to a Kotlin [Result].
 */
internal fun <T> OAuth2ClientResult<T>.toResult(): Result<T> =
    when (this) {
        is OAuth2ClientResult.Success -> Result.success(result)
        is OAuth2ClientResult.Error -> Result.failure(exception)
    }

/**
 * Converts a Kotlin [Result] to an [OAuth2ClientResult].
 */
@OptIn(InternalAuthFoundationApi::class)
internal fun <T> Result<T>.toOAuth2ClientResult(): OAuth2ClientResult<T> =
    fold(
        onSuccess = { OAuth2ClientResult.Success(it) },
        onFailure = { OAuth2ClientResult.Error(it as Exception) }
    )
