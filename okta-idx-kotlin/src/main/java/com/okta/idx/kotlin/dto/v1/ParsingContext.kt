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
package com.okta.idx.kotlin.dto.v1

import com.okta.idx.kotlin.dto.IdxAuthenticator
import com.okta.idx.kotlin.dto.IdxAuthenticatorCollection
import kotlinx.serialization.json.Json

internal data class ParsingContext(
    val authenticatorCollection: IdxAuthenticatorCollection,
    val authenticatorPathPairMap: Map<String, IdxAuthenticator>,
) {
    companion object {
        fun create(json: Json, response: Response): ParsingContext {
            val authenticatorPathPairs = response.toIdxAuthenticatorPathPairs(json)
            val authenticatorCollection = IdxAuthenticatorCollection(authenticatorPathPairs.map { it.authenticator })
            val authenticatorPathPairMap = authenticatorPathPairs.associateBy({ it.path }, { it.authenticator })
            return ParsingContext(authenticatorCollection, authenticatorPathPairMap)
        }
    }
}

internal fun ParsingContext?.authenticatorFor(relatesToJsonPath: String?): IdxAuthenticator? {
    if (this == null || relatesToJsonPath == null) return null
    return authenticatorPathPairMap[relatesToJsonPath]
}

internal data class AuthenticatorPathPair(val path: String, val authenticator: IdxAuthenticator)

internal fun IdxAuthenticator.toPathPair(path: String): AuthenticatorPathPair {
    return AuthenticatorPathPair(path, this)
}
