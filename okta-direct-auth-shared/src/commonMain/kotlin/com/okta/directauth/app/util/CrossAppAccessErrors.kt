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
package com.okta.directauth.app.util

import com.okta.directauth.app.model.SubjectKind
import com.okta.oauth2.kmp.CrossAppAccessException

/** Which authorization server — if any — is responsible for a failed Cross App Access attempt. */
enum class FailureAttribution {
    /** The IdP authorization server denied the request. */
    IDENTITY_PROVIDER,

    /** The resource authorization server denied the request. */
    RESOURCE_SERVER,

    /** The failure occurred before any network request — a local configuration problem. */
    CONFIGURATION,
}

/** A failure, attributed to a server (or configuration), with a developer-facing message. */
class ClassifiedFailure(
    val attribution: FailureAttribution,
    val message: String,
)

/**
 * Attributes a Cross App Access failure to the server that produced it, without discarding that
 * attribution the way naive root-cause unwrapping would.
 *
 * [com.okta.oauth2.kmp.CrossAppAccessException] exists precisely to name which server rejected an
 * exchange, with the original transport failure attached as its `cause`. A handler that walks
 * straight to the root cause before checking for this type — as this sample's existing OAuth2
 * error handling does — discards the attribution and reports a generic transport message instead.
 * [classify] scans for it *first*, and only then walks to the root cause for the server's own
 * error text.
 */
object CrossAppAccessErrors {
    /**
     * Classifies [error] and produces a developer-facing message.
     *
     * @param subjectKind which subject kind was presented when the failure occurred, if known. When
     *   a non-default kind was used and an identity-provider denial results, the message names
     *   that kind and suggests the identity token instead — the kind most likely to succeed
     *   everywhere. Otherwise, an identity-provider denial names the administrator-configured
     *   trust relationship as the most common cause, since it is the least discoverable from the
     *   server's own error text.
     */
    fun classify(
        error: Throwable,
        subjectKind: SubjectKind? = null,
    ): ClassifiedFailure {
        val found = findCrossAppAccessException(error)

        if (found == null) {
            return ClassifiedFailure(FailureAttribution.CONFIGURATION, rootMessage(error))
        }

        val serverMessage = rootMessage(found)
        return when (found) {
            is CrossAppAccessException.IdpExchangeFailed -> {
                val hint =
                    if (subjectKind != null && subjectKind != SubjectKind.IDENTITY) {
                        "The ${subjectKind.name.lowercase()} token subject may not be accepted by this org's " +
                            "trust configuration — try the identity token instead."
                    } else {
                        "This is commonly caused by a missing administrator-configured trust relationship " +
                            "between the requesting app and the resource app."
                    }
                ClassifiedFailure(
                    FailureAttribution.IDENTITY_PROVIDER,
                    "The identity provider rejected the request: $serverMessage. $hint"
                )
            }

            is CrossAppAccessException.TargetRedemptionFailed -> {
                ClassifiedFailure(
                    FailureAttribution.RESOURCE_SERVER,
                    "The resource authorization server rejected the request: $serverMessage"
                )
            }
        }
    }

    /** Scans the cause chain for a [CrossAppAccessException], before any root-cause unwrapping. */
    private fun findCrossAppAccessException(error: Throwable): CrossAppAccessException? {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            if (current is CrossAppAccessException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    /** Walks to the root cause and returns its message, for the server's own error text. */
    private fun rootMessage(error: Throwable): String {
        val seen = mutableSetOf<Throwable>()
        var current = error
        while (true) {
            val next = current.cause
            if (next == null || !seen.add(current)) break
            current = next
        }
        return current.message ?: current::class.simpleName ?: "Unknown error"
    }
}
