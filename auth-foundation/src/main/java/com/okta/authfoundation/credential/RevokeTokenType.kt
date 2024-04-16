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
package com.okta.authfoundation.credential

/**
 * The possible token types that can be revoked.
 *
 * See [Logout Documentation](https://github.com/okta/okta-mobile-kotlin#logout) for additional details.
 */
enum class RevokeTokenType {
    /**
     * Indicates the access token should be revoked.
     */
    ACCESS_TOKEN,

    /**
     * Indicates the refresh token should be revoked, if one is present. This will result in the access token being revoked as well.
     */
    REFRESH_TOKEN,

    /**
     * Indicates the device secret should be revoked, if one is present. This will result in the access token, and the refresh token
     * being revoked as well.
     */
    DEVICE_SECRET;

    internal fun toTokenType(): TokenType {
        return when (this) {
            ACCESS_TOKEN -> TokenType.ACCESS_TOKEN
            REFRESH_TOKEN -> TokenType.REFRESH_TOKEN
            DEVICE_SECRET -> TokenType.DEVICE_SECRET
        }
    }
}
