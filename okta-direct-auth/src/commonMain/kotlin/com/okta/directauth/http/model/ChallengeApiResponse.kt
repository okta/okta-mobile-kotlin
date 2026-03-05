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
package com.okta.directauth.http.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

internal object ChallengeApiResponseSerializer : JsonContentPolymorphicSerializer<ChallengeApiResponse>(ChallengeApiResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ChallengeApiResponse> =
        if ("publicKey" in element.jsonObject) {
            WebAuthnChallengeResponse.serializer()
        } else {
            ChallengeResponse.serializer()
        }
}

@Serializable(with = ChallengeApiResponseSerializer::class)
internal sealed interface ChallengeApiResponse
