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
package com.okta.directauth.app.model

import com.okta.directauth.model.OobChannel
import com.okta.directauth.model.PrimaryFactor
import com.okta.directauth.model.SecondaryFactor

sealed class AuthMethod(
    val label: String,
) {
    open fun asFactor(passCode: String = ""): PrimaryFactor =
        when (this) {
            Password -> PrimaryFactor.Password(passCode)
            Mfa.Otp -> PrimaryFactor.Otp(passCode)
            Mfa.OktaVerify -> PrimaryFactor.Oob(OobChannel.PUSH)
            Mfa.Sms -> PrimaryFactor.Oob(OobChannel.SMS)
            Mfa.Voice -> PrimaryFactor.Oob(OobChannel.VOICE)
            Mfa.Passkeys -> PrimaryFactor.WebAuthn
        }

    // MFA capable Auth methods
    sealed class Mfa(
        label: String,
    ) : AuthMethod(label) {
        override fun asFactor(passCode: String): SecondaryFactor =
            when (this) {
                Otp -> PrimaryFactor.Otp(passCode)
                OktaVerify -> PrimaryFactor.Oob(OobChannel.PUSH)
                Sms -> PrimaryFactor.Oob(OobChannel.SMS)
                Voice -> PrimaryFactor.Oob(OobChannel.VOICE)
                Passkeys -> PrimaryFactor.WebAuthn
            }

        data object Otp : Mfa("OTP")

        data object OktaVerify : Mfa("Okta Verify")

        data object Sms : Mfa("SMS")

        data object Voice : Mfa("Voice")

        data object Passkeys : Mfa("Passkeys")
    }

    data object Password : AuthMethod("Password")

    companion object {
        /**
         * Internal mapping function from string label to AuthMethod.
         * Returns null for unknown labels.
         */
        internal fun stringToAuthMethodInternal(label: String): AuthMethod? =
            when (label) {
                "Password" -> Password
                "OTP" -> Mfa.Otp
                "Okta Verify" -> Mfa.OktaVerify
                "SMS" -> Mfa.Sms
                "Voice" -> Mfa.Voice
                "Passkeys" -> Mfa.Passkeys
                else -> null
            }

        /**
         * Converts a string label to an AuthMethod.
         * Throws IllegalArgumentException if the label is unknown.
         */
        fun fromString(label: String): AuthMethod =
            stringToAuthMethodInternal(label)
                ?: throw IllegalArgumentException("Unknown AuthMethod label: $label")
    }
}

/**
 * Extension function to convert a string to an AuthMethod.
 * Returns null for unknown labels.
 */
fun String.toAuthMethod(): AuthMethod? = AuthMethod.stringToAuthMethodInternal(this)
