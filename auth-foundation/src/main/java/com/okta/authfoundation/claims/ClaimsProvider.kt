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
package com.okta.authfoundation.claims

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Used by classes that contains OAuth2 claims.
 *
 * This provides common conveniences for interacting with information within those claims.
 */
interface ClaimsProvider {
    /**
     * Used to get access to the claims data in a type safe way.
     *
     * @param deserializationStrategy the [DeserializationStrategy] capable of deserializing the specified type.
     *
     * @throws SerializationException if the claims data can't be deserialized into the specified type.
     * @return the specified type, deserialized from the claims.
     */
    fun <T> deserializeClaims(deserializationStrategy: DeserializationStrategy<T>): T

    /**
     * List all claims present in the claims data.
     */
    fun availableClaims(): Set<String>

    /**
     * Deserialize a specific claim from the claims data in a type safe way.
     *
     * @throws SerializationException if the claim can't be deserialized into the specified type.
     * @return the specified type, deserialized from the claims, if present.
     */
    fun <T> deserializeClaim(claim: String, deserializationStrategy: DeserializationStrategy<T>): T?
}

/** The subject of the resource, if available. */
val ClaimsProvider.subject: String?
    get() {
        return deserializeClaim("sub", String.serializer())
    }

/** The full name of the resource. */
val ClaimsProvider.name: String?
    get() {
        return deserializeClaim("name", String.serializer())
    }

/** The person's given, or first, name. */
val ClaimsProvider.givenName: String?
    get() {
        return deserializeClaim("given_name", String.serializer())
    }

/** The person's family, or last, name. */
val ClaimsProvider.familyName: String?
    get() {
        return deserializeClaim("family_name", String.serializer())
    }

/** The person's middle name. */
val ClaimsProvider.middleName: String?
    get() {
        return deserializeClaim("middle_name", String.serializer())
    }

/** The person's nickname. */
val ClaimsProvider.nickname: String?
    get() {
        return deserializeClaim("nickname", String.serializer())
    }

/** The person's preferred username. */
val ClaimsProvider.preferredUsername: String?
    get() {
        return deserializeClaim("preferred_username", String.serializer())
    }

/** The user's email address. */
val ClaimsProvider.email: String?
    get() {
        return deserializeClaim("email", String.serializer())
    }

/** The user's phone number. */
val ClaimsProvider.phoneNumber: String?
    get() {
        return deserializeClaim("phone_number", String.serializer())
    }

/** The user's gender. */
val ClaimsProvider.gender: String?
    get() {
        return deserializeClaim("gender", String.serializer())
    }

/** Indicates whether the token is active or not. */
val ClaimsProvider.active: Boolean?
    get() {
        return deserializeClaim("active", Boolean.serializer())
    }

/** The audience of the token. */
val ClaimsProvider.audience: String?
    get() {
        return deserializeClaim("aud", String.serializer())
    }

/** The ID of the client associated with the token. */
val ClaimsProvider.clientId: String?
    get() {
        return deserializeClaim("client_id", String.serializer())
    }

/** The ID of the device associated with the token. */
val ClaimsProvider.deviceId: String?
    get() {
        return deserializeClaim("device_id", String.serializer())
    }

/** The expiration time of the token in seconds since January 1, 1970 UTC. */
val ClaimsProvider.expirationTime: Int?
    get() {
        return deserializeClaim("exp", Int.serializer())
    }

/** The issuing time of the token in seconds since January 1, 1970 UTC. */
val ClaimsProvider.issuedAt: Int?
    get() {
        return deserializeClaim("iat", Int.serializer())
    }

/** The issuer of the token. */
val ClaimsProvider.issuer: String?
    get() {
        return deserializeClaim("iss", String.serializer())
    }

/** The identifier of the token. */
val ClaimsProvider.jwtId: String?
    get() {
        return deserializeClaim("jti", String.serializer())
    }

/** Identifies the time (a timestamp in seconds since January 1, 1970 UTC) before which the token must not be accepted for processing. */
val ClaimsProvider.notBefore: Int?
    get() {
        return deserializeClaim("nbf", Int.serializer())
    }

/** A space-delimited list of scopes. */
val ClaimsProvider.scope: String?
    get() {
        return deserializeClaim("scope", String.serializer())
    }

/** The type of token. The value is always Bearer. */
val ClaimsProvider.tokenType: String?
    get() {
        return deserializeClaim("token_type", String.serializer())
    }

/** The user ID. This parameter is returned only if the token is an access token and the subject is an end user. */
val ClaimsProvider.userId: String?
    get() {
        return deserializeClaim("uid", String.serializer())
    }

/** The username associated with the token. */
val ClaimsProvider.username: String?
    get() {
        return deserializeClaim("username", String.serializer())
    }

/** The `amr` claim, Authentication Methods References, associated with the token. */
val ClaimsProvider.authMethodsReference: List<String>?
    get() {
        return deserializeClaim("amr", ListSerializer(String.serializer()))
    }

/** The `acr` claim, Authentication Context Class Reference, associated with the token. */
val ClaimsProvider.authContextClassReference: String?
    get() {
        return deserializeClaim("acr", String.serializer())
    }
