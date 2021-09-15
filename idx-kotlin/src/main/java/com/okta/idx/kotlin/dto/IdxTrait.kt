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

import com.okta.idx.kotlin.dto.IdxRemediation.Type
import okhttp3.HttpUrl

/**
 * Represents a collection of [IdxTrait]s.
 */
class IdxTraitCollection internal constructor(
    private val traits: Set<IdxTrait>,
) : Set<IdxTrait> by traits {
    /**
     * Returns a trait based on its type.
     */
    inline fun <reified T : IdxTrait> get(): T? {
        val matched = firstOrNull { it is T } ?: return null
        return matched as T
    }
}

/**
 * Marker interface for traits.
 */
interface IdxTrait

/** Describes the IdP associated with a remediation of type [Type.REDIRECT_IDP]. */
class IdxIdpTrait internal constructor(
    /** The IdPs id. */
    val id: String,
    /** The IdPs name. */
    val name: String,
    /** The IdPs redirectUrl. */
    val redirectUrl: HttpUrl,
) : IdxTrait

/** Describes the recover action associated with an [IdxAuthenticator]. */
class IdxRecoverTrait internal constructor(
    /** The [IdxRemediation] associated with the recover action. */
    val remediation: IdxRemediation,
) : IdxTrait

/** Describes the send action associated with an [IdxAuthenticator]. */
class IdxSendTrait internal constructor(
    /** The [IdxRemediation] associated with the send action. */
    val remediation: IdxRemediation,
) : IdxTrait

/** Describes the resend action associated with an [IdxAuthenticator]. */
class IdxResendTrait internal constructor(
    /** The [IdxRemediation] associated with the resend action. */
    val remediation: IdxRemediation,
) : IdxTrait

/** Describes the poll action associated with an [IdxAuthenticator]. */
class IdxPollTrait internal constructor(
    /** The [IdxRemediation] associated with the poll action. */
    val remediation: IdxRemediation,
) : IdxTrait

/** Describes the profile information associated with an [IdxAuthenticator]. */
class IdxProfileTrait internal constructor(
    /** Profile information describing the authenticator. This usually contains redacted information relevant to display to the user. */
    val profile: Map<String, String>,
) : IdxTrait
