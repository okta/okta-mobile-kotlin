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
 * Represents a collection of messages.
 */
class IdxMessageCollection internal constructor(
    /**
     * The collection of messages at the form level.
     */
    val messages: List<IdxMessage>,
) : List<IdxMessage> by messages

/**
 * Represents messages sent from the server to indicate error or warning conditions related to responses or form values.
 */
class IdxMessage internal constructor(
    /** The type of message received from the server. */
    val type: Severity,

    /**
     * A localization key representing this message.
     *
     * This allows the text represented by this message to be customized or localized as needed.
     */
    val localizationKey: String?,

    /** The default text for this message. */
    val message: String,
) {
    /**
     * Enumeration describing the type of message.
     */
    enum class Severity {
        ERROR,
        INFO,
        UNKNOWN,
    }
}
