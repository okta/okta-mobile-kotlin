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
package com.okta.authfoundation

/**
 * Represents a failed, errored, or cancelled biometric authentication challenge.
 *
 * This type is safe to catch, construct, and inspect from shared (`commonMain`) code. When surfaced
 * from [com.okta.authfoundation.credential.BiometricAuthenticator], it is always delivered as the
 * failure of a [Result] — that interface never throws this (or any other) exception for expected
 * failure/error/cancellation outcomes.
 *
 * On JVM and Android targets this class extends `java.security.GeneralSecurityException` so it is
 * compatible with existing catch-clauses that catch `GeneralSecurityException`.
 */
expect class BiometricAuthenticationException(
    message: String,
    biometricExceptionDetails: BiometricExceptionDetails,
) : Exception {
    val biometricExceptionDetails: BiometricExceptionDetails
}

/**
 * Additional details describing why a biometric authentication challenge failed.
 */
sealed interface BiometricExceptionDetails {
    /**
     * The biometric authentication challenge failed (e.g. an unrecognized fingerprint/face), and the
     * caller is expected to retry.
     */
    data object OnAuthenticationFailed : BiometricExceptionDetails

    /**
     * The biometric authentication challenge encountered an unrecoverable error and will not be retried
     * automatically.
     */
    data class OnAuthenticationError(
        val errorCode: Int,
        val errString: CharSequence,
    ) : BiometricExceptionDetails
}
