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
package com.okta.authfoundation.credential

/**
 * The type of token an operation should be used with.
 */
enum class TokenType {
    /** Indicates the refresh token. */
    REFRESH_TOKEN,

    /** Indicates the access token. */
    ACCESS_TOKEN,

    /** Indicates the ID token. */
    ID_TOKEN,

    /** Indicates the device secret. */
    DEVICE_SECRET;

    fun toTokenTypeHint(): String {
        return when (this) {
            ACCESS_TOKEN -> {
                "access_token"
            }
            REFRESH_TOKEN -> {
                "refresh_token"
            }
            ID_TOKEN -> {
                "id_token"
            }
            DEVICE_SECRET -> {
                "device_secret"
            }
        }
    }
}
