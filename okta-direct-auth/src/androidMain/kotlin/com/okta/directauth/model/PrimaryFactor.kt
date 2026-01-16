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
package com.okta.directauth.model

/**
 * Represents an authentication factor that can be used as a secondary factor in an MFA flow.
 * This excludes primary-only factors like passwords.
 */
sealed interface SecondaryFactor : PrimaryFactor

/**
 * Represents an authentication factor used in a Direct Authentication flow.
 *
 * Factors are categorized as either Primary or Secondary to support multi-stage
 * authentication workflows.
 */
sealed interface PrimaryFactor {
    /**
     * A password factor, which can only be used as a primary factor.
     *
     * @param password The user's password.
     */
    data class Password(
        val password: String,
    ) : PrimaryFactor

    /**
     * An OTP (One-Time Passcode) factor.
     *
     * @param passCode The one-time passcode provided by the user.
     */
    data class Otp(
        val passCode: String,
    ) : SecondaryFactor

    /**
     * An OOB (Out-of-Band) factor, such as a push notification.
     *
     * @param channel The channel through which the OOB challenge should be sent.
     */
    data class Oob(
        val channel: OobChannel,
    ) : SecondaryFactor

    /**
     * A WebAuthn factor, used for phishing-resistant authentication with hardware
     * authenticators or biometrics.
     */
    data object WebAuthn : SecondaryFactor
}
