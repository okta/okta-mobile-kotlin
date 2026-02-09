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
package com.okta.authfoundation.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the OpenID Connect (OIDC) UserInfo response.
 *
 * This data class contains the standard claims defined in the
 * [OpenID Connect Core 1.0 Specification](https://openid.net).
 */
@Serializable
data class UserInfoResponse(
    /**
     * Subject - Identifier for the End-User at the Issuer.
     * **REQUIRED** and unique.
     */
    @SerialName("sub")
    val subject: String,
    /**
     * Full name including all titles and suffixes. (scope: profile)
     * */
    val name: String? = null,
    /**
     * First or primary name(s) of the End-User. (scope: profile)
     * */
    @SerialName("given_name")
    val givenName: String? = null,
    /**
     * Surname(s) or last name(s) of the End-User. (scope: profile)
     * */
    @SerialName("family_name")
    val familyName: String? = null,
    /**
     * Shorthand name the End-User wishes to be referred to at the RP. (scope: profile)
     * */
    @SerialName("preferred_username")
    val preferredUsername: String? = null,
    /**
     * Preferred e-mail address. (scope: email)
     * */
    val email: String? = null,
    /**
     * True if the e-mail address has been verified; otherwise false. (scope: email)
     * */
    @SerialName("email_verified")
    val emailVerified: Boolean? = null,
    /**
     * URL of the End-User's profile picture. (scope: profile)
     * */
    val picture: String? = null,
    /**
     * Physical address components. (scope: address)
     * */
    val address: OidcAddress? = null,
    /**
     * End-User's preferred telephone number. (scope: phone)
     * */
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    /**
     * Time the End-User's information was last updated.
     * Represented as a [Unix timestamp](https://www.unixtimestamp.com).
     */
    @SerialName("updated_at")
    val updatedAt: Long? = null,
)

/**
 * Structured physical address representation for OIDC claims.
 */
@Serializable
data class OidcAddress(
    /**
     * Full mailing address, formatted for display or use on a label.
     * */
    val formatted: String? = null,
    /**
     * Full street address component, which may include house number and street name.
     * */
    @SerialName("street_address")
    val streetAddress: String? = null,
    /**
     * City or locality component.
     * */
    val locality: String? = null,
    /**
     * State, province, prefecture, or region component.
     * */
    val region: String? = null,
    /**
     * Zip code or postal code component.
     * */
    @SerialName("postal_code")
    val postalCode: String? = null,
    /**
     * Country name or [ISO 3166-1 alpha-2](https://www.iso.org) country code.
     * */
    val country: String? = null,
)
