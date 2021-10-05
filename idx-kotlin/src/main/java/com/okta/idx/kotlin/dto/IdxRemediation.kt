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

import okhttp3.HttpUrl

/**
 * Represents a collection of remediation options.
 */
class IdxRemediationCollection internal constructor(
    private val remediations: List<IdxRemediation>,
) : List<IdxRemediation> by remediations {
    /**
     * Returns a remediation based on its name.
     */
    operator fun get(name: String): IdxRemediation? {
        return remediations.firstOrNull { it.name == name }
    }

    /**
     * Returns a remediation based on its type.
     */
    operator fun get(type: IdxRemediation.Type): IdxRemediation? {
        return remediations.firstOrNull { it.type == type }
    }
}

/**
 * Instances of `IdxRemediation` describe choices the user can make to proceed through the authentication workflow.
 *
 * Either simple or complex authentication scenarios consist of a set of steps that may be followed, but at some times the user may have a choice in what they use to verify their identity. For example, a user may have multiple choices in verifying their account, such as:
 * 1. Password
 * 2. Security Questions
 * 3. Email verification
 * 4. Other customizable verification steps.
 *
 * Each of the remediation options includes details about what form values should be collected from the user, and a description of the resulting request that should be sent to Okta to proceed to the next step.
 */
class IdxRemediation internal constructor(
    /** The type of this remediation, which is used for keyed subscripting from a `IdxClient.RemediationCollection`. */
    val type: Type,

    /** The string name for this type. */
    val name: String,

    /** A description of the form values that this remediation option supports and expects. */
    val form: Form,

    /** The set of authenticators associated with this remediation. */
    val authenticators: IdxAuthenticatorCollection,

    /** The [IdxTraitCollection] associated with this remediation. */
    val traits: IdxTraitCollection<Trait>,

    internal val method: String,
    internal val href: HttpUrl,
    internal val accepts: String?,
    internal val relatesTo: List<String>?,
) {
    /**
     * Returns the field within this remediation with the given name or key-path.
     *
     * To retrieve nested fields, keyPath "." notation can be used to select fields within child forms.
     */
    operator fun get(name: String): Form.Field? {
        return form[name]
    }

    /**
     * Marker interface for [IdxRemediation] traits.
     */
    interface Trait

    /**
     * Enumeration describing the possible remediation types. This is expanded from the possible option values that may be present in the `name` property.
     */
    enum class Type {
        UNKNOWN,
        ISSUE,
        IDENTIFY,
        IDENTIFY_RECOVERY,
        SELECT_IDENTIFY,
        SELECT_ENROLL_PROFILE,
        CANCEL,
        ACTIVATE_FACTOR,
        SEND_CHALLENGE,
        RESEND_CHALLENGE,
        SELECT_FACTOR_AUTHENTICATE,
        SELECT_FACTOR_ENROLL,
        CHALLENGE_FACTOR,
        SELECT_AUTHENTICATOR_AUTHENTICATE,
        SELECT_AUTHENTICATOR_ENROLL,
        SELECT_ENROLLMENT_CHANNEL,
        AUTHENTICATOR_VERIFICATION_DATA,
        AUTHENTICATOR_ENROLLMENT_DATA,
        ENROLLMENT_CHANNEL_DATA,
        CHALLENGE_AUTHENTICATOR,
        POLL,
        ENROLL_POLL,
        RECOVER,
        ENROLL_FACTOR,
        ENROLL_AUTHENTICATOR,
        REENROLL_AUTHENTICATOR,
        REENROLL_AUTHENTICATOR_WARNING,
        RESET_AUTHENTICATOR,
        ENROLL_PROFILE,
        PROFILE_ATTRIBUTES,
        SELECT_IDP,
        SELECT_PLATFORM,
        FACTOR_POLL_VERIFICATION,
        QR_REFRESH,
        DEVICE_CHALLENGE_POLL,
        CANCEL_POLLING,
        DEVICE_APPLE_SSO_EXTENSION,
        LAUNCH_AUTHENTICATOR,
        REDIRECT,
        REDIRECT_IDP,
        CANCEL_TRANSACTION,
        SKIP,
        CHALLENGE_POLL,
    }

    /**
     * Object that represents a form of fields associated with a remediation.
     */
    class Form internal constructor(
        internal val allFields: List<Field>
    ) {
        /**
         * The list of ordered user-visible fields within this form. Each field may also contain nested forms for collections of related fields.
         */
        val visibleFields: List<Field> = allFields.filter { it.hasVisibleFields }

        /**
         * Returns the field within this form with the given name or key-path.
         *
         * To retrieve nested fields, keyPath "." notation can be used to select fields within child forms.
         */
        operator fun get(name: String): Field? {
            val components = name.split(".").toMutableList()

            val componentName = components.removeFirst()
            var result = visibleFields.firstOrNull { it.name == componentName }
            if (result != null && components.isNotEmpty()) {
                result = result.form?.get(components.joinToString(separator = "."))
            }

            return result
        }

        /**
         * Returns the field within this form with the given index.
         */
        operator fun get(index: Int): Field {
            return visibleFields[index]
        }

        /**
         * Describes an individual field within a form, used to collect and submit information from the user to proceed through the authentication workflow.
         */
        class Field internal constructor(
            /** The programmatic name for this form value. */
            val name: String?,
            /** The user-readable label describing this form value. */
            val label: String?,
            /** The type of value expected from the client. */
            val type: String,

            /** This is the backing field, without the restrictions of the public field. */
            @Volatile private var _value: Any?,

            /** Indicates whether or not the form value is read-only. */
            val isMutable: Boolean,
            /** Indicates whether or not the form value is required to successfully proceed through this remediation option. */
            val isRequired: Boolean,
            /** Indicates whether or not the value supplied in this form value should be considered secret, and not presented to the user. */
            val isSecret: Boolean,
            /** For composite form fields, this contains the nested array of form values to group together. */
            val form: Form?,
            /** For form fields that have specific options the user can choose from (e.g. security question, passcode, etc), this indicates the different form options that should be displayed to the user. */
            val options: List<Field>?,
            /**
             * The list of messages sent from the server.
             *
             * Messages reported from the server at the FormValue level should be considered relevant to the individual form field, and as a result should be displayed to the user alongside any UI elements associated with it.
             */
            val messages: IdxMessageCollection,
            /** Relates this field to an authenticator, when a field is used to represent an authenticator. For example, when a field is used within a series of `options` to identify which authenticator to select. */
            val authenticator: IdxAuthenticator?,

            internal val isVisible: Boolean,
        ) {
            /**
             * Returns the nested `form` field with the given name.
             */
            operator fun get(name: String): Field? {
                return form?.get(name)
            }

            /** The value to send, if a default is provided from the Identity Engine. */
            var value: Any?
                set(value) {
                    if (!isMutable) {
                        throw IllegalStateException("Field is not mutable.")
                    }
                    _value = value
                }
                get() {
                    return _value
                }

            /**
             * Allows a developer to set the selected option for a field that contains multiple `options`.
             *
             * This will update the `isSelectedOption` on all relevant fields.
             */
            @Volatile var selectedOption: Field? = null

            internal val hasVisibleFields: Boolean by lazy {
                computeHasVisibleFields()
            }

            private fun computeHasVisibleFields(): Boolean {
                if (isVisible) return true
                if (form != null) {
                    return form.allFields.any { it.hasVisibleFields }
                }
                if (options != null) {
                    return options.any { it.hasVisibleFields }
                }
                return false
            }
        }
    }
}
