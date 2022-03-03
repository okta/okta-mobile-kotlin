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
package com.okta.authfoundation.client.dto

import com.okta.authfoundation.util.JsonPayloadDeserializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException

/**
 * User profile information.
 *
 * This provides a convenience mechanism for accessing information related to a user.
 */
data class OidcUserInfo internal constructor(
    private val jsonPayloadDeserializer: JsonPayloadDeserializer,
) {
    /**
     * Used to get access to the payload data in a type safe way.
     *
     * @param deserializationStrategy the [DeserializationStrategy] capable of deserializing the specified type.
     *
     * @throws SerializationException if the payload data can't be deserialized into the specified type.
     * @return the specified type, deserialized from the payload.
     */
    suspend fun <T> payload(deserializationStrategy: DeserializationStrategy<T>): T {
        return jsonPayloadDeserializer.payload(deserializationStrategy)
    }
}
