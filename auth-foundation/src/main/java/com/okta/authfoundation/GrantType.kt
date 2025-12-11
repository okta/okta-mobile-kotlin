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
package com.okta.authfoundation

/**
 * Represents the OAuth 2.0 grant type. Determines the mechanism Okta uses to authorize the creation of the tokens.
 *
 * This sealed class allows for a fixed set of standard grant types as well as custom ones.
 *
 * @see [RFC 6749, Section 4](https://tools.ietf.org/html/rfc6749#section-4)
 * @see [Okta Direct Authentication Grant Types](https://developer.okta.com/docs/reference/direct-auth/grant-types/)
 */
sealed class GrantType(
    val value: String,
) {
    /**
     * The authorization code grant type is used to obtain both access tokens and refresh tokens
     * and is optimized for confidential clients.
     *
     * @see [RFC 6749, Section 4.1](https://tools.ietf.org/html/rfc6749#section-4.1)
     */
    data object AuthorizationCode : GrantType("authorization_code")

    /**
     * The refresh token grant type is used by clients to exchange a refresh token for a new
     * access token when the current access token becomes invalid or expires.
     *
     * @see [RFC 6749, Section 6](https://tools.ietf.org/html/rfc6749#section-6)
     */
    data object RefreshToken : GrantType("refresh_token")

    /**
     * The resource owner password credentials grant type is suitable in cases where the resource
     * owner has a trust relationship with the client.
     *
     * @see [RFC 6749, Section 4.3](https://tools.ietf.org/html/rfc6749#section-4.3)
     */
    data object Password : GrantType("password")

    /**
     * The device authorization grant is used by browserless or input-constrained devices
     * in the device flow to obtain an access token.
     *
     * @see [RFC 8628](https://tools.ietf.org/html/rfc8628)
     */
    data object DeviceCode : GrantType("urn:ietf:params:oauth:grant-type:device_code")

    /**
     * A grant type for exchanging a token of one type for a token of another type.
     *
     * @see [RFC 8693](https://tools.ietf.org/html/rfc8693)
     */
    data object TokenExchange : GrantType("urn:ietf:params:oauth:grant-type:token-exchange")

    /**
     * A grant type that uses a JWT as an authorization grant.
     *
     * @see [RFC 7523](https://tools.ietf.org/html/rfc7523)
     */
    data object JwtBearer : GrantType("urn:ietf:params:oauth:grant-type:jwt-bearer")

    /**
     * A grant type for One-Time Passcode (OTP) authentication, often used as a second factor.
     *
     * @see [Okta Direct Authentication OTP](https://developer.okta.com/docs/reference/direct-auth/grant-types/#otp)
     */
    data object Otp : GrantType("urn:okta:params:oauth:grant-type:otp")

    /**
     * A grant type for Out-of-Band (OOB) authentication, such as push notifications.
     *
     * @see [Okta Direct Authentication OOB](https://developer.okta.com/docs/reference/direct-auth/grant-types/#oob)
     */
    data object Oob : GrantType("urn:okta:params:oauth:grant-type:oob")

    /**
     * A grant type for WebAuthn authentication.
     *
     * @see [Okta Direct Authentication WebAuthn](https://developer.okta.com/docs/reference/direct-auth/grant-types/#webauthn)
     */
    data object WebAuthn : GrantType("urn:okta:params:oauth:grant-type:webauthn")

    /**
     * A custom grant type not defined in the standard set.
     *
     * @param type The string representation of the custom grant type.
     */
    data class Other(
        val type: String,
    ) : GrantType(type)
}

/**
 * Represents specialized OAuth 2.0 grant types used for challenge-based authentication mechanisms,
 * such as Multi-Factor Authentication (MFA) with Out-of-Band (OOB), One-Time Passcode (OTP), or WebAuthn.
 *
 * These grant types extend the standard set to support additional security requirements.
 */
sealed class ChallengeGrantType(
    value: String,
) : GrantType(value) {
    /**
     * A grant type for Multi-Factor Authentication (MFA) using Out-of-Band (OOB) methods.
     *
     * @see [Okta MFA OOB](https://developer.okta.com/docs/guides/configure-direct-auth-grants/bmfaotp/main/#about-the-direct-authentication-grant)
     */
    data object OobMfa : ChallengeGrantType("http://auth0.com/oauth/grant-type/mfa-oob")

    /**
     * A grant type for Multi-Factor Authentication (MFA) using One-Time Passcodes (OTP).
     *
     * @see [Okta MFA OTP](https://developer.okta.com/docs/guides/configure-direct-auth-grants/bmfaotp/main/#about-the-direct-authentication-grant)
     */
    data object OtpMfa : ChallengeGrantType("http://auth0.com/oauth/grant-type/mfa-otp")

    /**
     * A grant type for Multi-Factor Authentication (MFA) using WebAuthn.
     *
     * @see [Okta MFA WebAuthn](https://developer.okta.com/docs/guides/configure-direct-auth-grants/bmfawebauthn/main/#about-the-direct-authentication-grant)
     */
    data object WebAuthnMfa : ChallengeGrantType("urn:okta:params:oauth:grant-type:mfa-webauthn")
}
