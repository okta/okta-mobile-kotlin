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
package com.okta.directauth.app.http

import com.okta.authfoundation.api.http.ApiRequestBody
import com.okta.authfoundation.api.http.ApiRequestMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UpdateProfileRequest(
    val profile: Profile,
)

@Serializable
data class Profile(
    val password: String,
)

/**
 * API request to replace/change a user's password using the Okta myAccount API.
 *
 * This request is used in the self-service password recovery (SSPR) flow after the user
 * has authenticated with the okta.myAccount.password.manage scope.
 *
 * @param issuerUrl The Okta issuer URL (e.g., "https://example.okta.com")
 * @param accessToken The access token with okta.myAccount.password.manage scope
 * @param newPassword The new password to set for the user
 */
internal class MyAccountReplacePasswordRequest(
    private val issuerUrl: String,
    private val accessToken: String,
    private val newPassword: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ApiRequestBody {
    override fun url(): String = issuerUrl.trimEnd('/') + "/idp/myaccount/password"

    override fun method(): ApiRequestMethod = ApiRequestMethod.PUT

    override fun headers(): Map<String, List<String>> =
        mapOf(
            "Accept" to listOf("application/json; okta-version=1.0.0"),
            "Authorization" to listOf("Bearer $accessToken"),
            "Content-Type" to listOf("application/json")
        )

    override fun contentType(): String = "application/json"

    override fun body(): ByteArray {
        val requestBody = UpdateProfileRequest(Profile(newPassword))
        return json.encodeToString(requestBody).toByteArray(Charsets.UTF_8)
    }
}
