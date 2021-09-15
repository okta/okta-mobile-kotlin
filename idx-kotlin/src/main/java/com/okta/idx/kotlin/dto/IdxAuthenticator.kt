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
package com.okta.idx.kotlin.dto

/**
 * Container that represents a collection of authenticators, providing conveniences for quickly accessing relevant objects.
 */
class IdxAuthenticatorCollection internal constructor(
    internal val authenticators: List<IdxAuthenticator>,
) : List<IdxAuthenticator> by authenticators {
    /**
     * The current authenticator, if one is actively being enrolled or authenticated.
     */
    val current: IdxAuthenticator?
        get() {
            return authenticators.firstOrNull {
                it.state == IdxAuthenticator.State.AUTHENTICATING || it.state == IdxAuthenticator.State.ENROLLING
            }
        }

    /**
     * The array of currently-enrolled authenticators.
     */
    val enrolled: List<IdxAuthenticator>
        get() {
            return authenticators.filter {
                it.state == IdxAuthenticator.State.ENROLLED
            }
        }

    /**
     * Access authenticators based on their type.
     */
    operator fun get(kind: IdxAuthenticator.Kind): IdxAuthenticator? {
        return authenticators.firstOrNull { it.type == kind }
    }
}

/**
 * Represents information describing the available authenticators and enrolled authenticators.
 */
class IdxAuthenticator internal constructor(
    /** Unique identifier for this enrollment. */
    val id: String?,

    /** The user-visible name to use for this authenticator enrollment. */
    val displayName: String?,

    /** The type of this authenticator, or `unknown` if the type isn't represented by this enumeration. */
    val type: Kind,

    /** The key name for the authenticator. */
    val key: String?,

    /** Indicates the state of this authenticator, either being an available authenticator, an enrolled authenticator, authenticating, or enrolling. */
    val state: State,

    /** Describes the various methods this authenticator can perform. */
    val methods: List<Method>?,

    /** Describes the various methods this authenticator can perform, as string values. */
    val methodNames: List<String>?,

    /** The [IdxTraitCollection] associated with this authenticator. */
    val traits: IdxTraitCollection,
) {
    /**
     * The state of an authenticator.
     */
    enum class State {
        NORMAL,
        ENROLLED,
        AUTHENTICATING,
        ENROLLING,
        RECOVERY,
    }

    /**
     * The type of authenticator.
     */
    enum class Kind {
        UNKNOWN,
        APP,
        EMAIL,
        PHONE,
        PASSWORD,
        SECURITY_QUESTION,
        DEVICE,
        SECURITY_KEY,
        FEDERATED,
    }

    /**
     * The method, or sub-type, of an authenticator.
     */
    enum class Method {
        UNKNOWN,
        SMS,
        VOICE,
        EMAIL,
        PUSH,
        CRYPTO,
        SIGNED_NONCE,
        TOTP,
        PASSWORD,
        WEB_AUTHN,
        SECURITY_QUESTION,
    }
}
