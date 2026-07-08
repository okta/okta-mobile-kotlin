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
package com.okta.authfoundation.credential

import com.okta.authfoundation.BiometricAuthenticationException
import com.okta.authfoundation.BiometricExceptionDetails
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BiometricAuthenticatorCommonTest {
    private class FakeBiometricAuthenticator(
        private val unlockResult: Result<Unit> = Result.success(Unit),
        private val decryptResult: Result<ByteArray> = Result.success(ByteArray(0)),
        private val decryptWithIvResult: Result<ByteArray> = Result.success(ByteArray(0)),
    ) : BiometricAuthenticator {
        var lastKeyAlias: String? = null
        var lastIv: ByteArray? = null

        override suspend fun unlock(): Result<Unit> = unlockResult

        override suspend fun decrypt(
            encryptedData: ByteArray,
            keyAlias: String,
        ): Result<ByteArray> {
            lastKeyAlias = keyAlias
            return decryptResult
        }

        override suspend fun decrypt(
            encryptedData: ByteArray,
            keyAlias: String,
            iv: ByteArray,
        ): Result<ByteArray> {
            lastKeyAlias = keyAlias
            lastIv = iv
            return decryptWithIvResult
        }
    }

    @Test
    fun unlock_ReturnsSuccessResult() =
        runTest {
            val authenticator: BiometricAuthenticator = FakeBiometricAuthenticator()
            val result = authenticator.unlock()
            assertTrue(result.isSuccess)
        }

    @Test
    fun decrypt_WithoutIv_DispatchesToNoIvOverload() =
        runTest {
            val authenticator = FakeBiometricAuthenticator()
            val result = authenticator.decrypt(ByteArray(0), "keyAlias")
            assertTrue(result.isSuccess)
            assertEquals("keyAlias", authenticator.lastKeyAlias)
            assertEquals(null, authenticator.lastIv)
        }

    @Test
    fun decrypt_WithIv_DispatchesToIvOverload() =
        runTest {
            val authenticator = FakeBiometricAuthenticator()
            val iv = byteArrayOf(1, 2, 3)
            val result = authenticator.decrypt(ByteArray(0), "keyAlias", iv)
            assertTrue(result.isSuccess)
            assertEquals("keyAlias", authenticator.lastKeyAlias)
            assertEquals(iv, authenticator.lastIv)
        }

    @Test
    fun unlock_ReturnsFailureResultOnFailure() =
        runTest {
            val exception =
                BiometricAuthenticationException(
                    "failed",
                    BiometricExceptionDetails.OnAuthenticationFailed
                )
            val authenticator = FakeBiometricAuthenticator(unlockResult = Result.failure(exception))
            val result = authenticator.unlock()
            assertTrue(result.isFailure)
        }

    @Test
    fun biometricAuthenticationException_IsConstructibleAndInspectableFromCommonCode() {
        val failedResult: Result<Unit> =
            Result.failure(
                BiometricAuthenticationException(
                    "Unexpected Biometric error",
                    BiometricExceptionDetails.OnAuthenticationFailed
                )
            )

        val exception = failedResult.exceptionOrNull()
        assertIs<BiometricAuthenticationException>(exception)
        assertEquals(BiometricExceptionDetails.OnAuthenticationFailed, exception.biometricExceptionDetails)
    }

    @Test
    fun onAuthenticationError_DetailsAreAccessibleAsPlainTypes() {
        val failedResult: Result<ByteArray> =
            Result.failure(
                BiometricAuthenticationException(
                    "Failed biometric authentication",
                    BiometricExceptionDetails.OnAuthenticationError(7, "lockout")
                )
            )

        val exception = failedResult.exceptionOrNull()
        assertIs<BiometricAuthenticationException>(exception)
        val details = assertIs<BiometricExceptionDetails.OnAuthenticationError>(exception.biometricExceptionDetails)
        val errorCode: Int = details.errorCode
        val errString: CharSequence = details.errString
        assertEquals(7, errorCode)
        assertEquals("lockout", errString)
    }
}
