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

import com.okta.authfoundation.client.dto.JsonWebKeySet
import com.okta.authfoundation.client.dto.TokenIntrospectionResponse
import com.okta.authfoundation.client.dto.UserInfoResponse
import com.okta.authfoundation.credential.RevokeTokenType
import com.okta.authfoundation.credential.TokenInfo
import com.okta.authfoundation.credential.TokenType

/**
 * Interface for authentication client operations.
 *
 * This provides a platform-agnostic contract for interacting with an Authorization Server.
 */
interface AuthClient {
    /**
     * Performs the OIDC User Info call.
     *
     * @param accessToken the access token used for authorization
     * @return Result containing UserInfo on success
     */
    suspend fun getUserInfo(accessToken: String): Result<UserInfoResponse>

    /**
     * Attempt to refresh the token.
     *
     * @param token the Token to refresh
     * @return Result containing the refreshed Token on success
     */
    suspend fun refreshToken(token: TokenInfo): Result<TokenInfo>

    /**
     * Attempt to revoke the specified token.
     *
     * @param revokeTokenType the type of token to revoke
     * @param token the token to revoke
     * @return Result containing Unit on success
     */
    suspend fun revokeToken(
        revokeTokenType: RevokeTokenType,
        token: TokenInfo,
    ): Result<Unit>

    /**
     * Performs token introspection.
     *
     * @param tokenType the type of token to introspect
     * @param token the token to introspect
     * @return Result containing IntrospectInfo on success
     */
    suspend fun introspectToken(
        tokenType: TokenType,
        token: TokenInfo,
    ): Result<TokenIntrospectionResponse>

    /**
     * Fetches the JSON Web Key Set.
     *
     * @return Result containing Jwks on success
     */
    suspend fun jwks(): Result<JsonWebKeySet>
}
