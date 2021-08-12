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

import java.util.Date

/**
 * Describes the response from an Okta Identity Engine workflow stage.
 * This is used to determine the current state of the workflow, the set of available remediation steps to proceed through the workflow,
 * actions that can be performed, and other information relevant to the authentication of a user.
 */
class IdxResponse internal constructor(
    /** The date at which this stage of the workflow expires, after which the authentication process should be restarted. */
    val expiresAt: Date?,

    /** A string describing the intent of the workflow, e.g. "LOGIN". */
    val intent: Intent,

    /** An object describing the sort of remediation steps available to the user. */
    val remediations: IdxRemediationCollection,

    /** Contains information about the available authenticators. */
    val authenticators: IdxAuthenticatorCollection,

    /** Returns information about the application, if available. */
    val app: IdxApplication?,

    /** Returns information about the user authenticating, if available. */
    val user: IdxUser?,

    /**
     * The list of messages sent from the server.
     * Messages reported from the server are usually errors, but may include other information relevant to the user.
     * They should be displayed to the user in the context of the remediation form itself.
     */
    val messages: IdxMessageCollection,

    /** Indicates whether or not the user has logged in successfully. If this is `true`, this response object should be exchanged for access tokens utilizing the `exchangeCode` method. */
    val isLoginSuccessful: Boolean,

    /** Indicates whether or not the response can be cancelled. */
    val canCancel: Boolean,
) {
    /**
     * The intent of the authentication workflow, as returned from the server.
     */
    enum class Intent {
        ENROLL_NEW_USER,
        LOGIN,
        CREDENTIAL_ENROLLMENT,
        CREDENTIAL_UNENROLLMENT,
        CREDENTIAL_RECOVERY,
        CREDENTIAL_MODIFY,
        UNKNOWN,
    }
}
