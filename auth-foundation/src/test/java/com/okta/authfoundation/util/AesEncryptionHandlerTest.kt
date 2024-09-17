/*
 * Copyright 2024-Present Okta, Inc.
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
package com.okta.authfoundation.util

import android.security.keystore.KeyGenParameterSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

@RunWith(AndroidJUnit4::class)
class AesEncryptionHandlerTest {
    private lateinit var aesEncryptionHandler: AesEncryptionHandler
    private lateinit var mockEncryptionKeySpec: KeyGenParameterSpec
    private lateinit var keyGenerator: KeyGenerator

    @Before
    fun setUp() {
        mockkObject(AndroidKeystoreUtil)
        keyGenerator = KeyGenerator.getInstance("AES")
        every { AndroidKeystoreUtil.getOrCreateAesKey(any()) } returns keyGenerator.generateKey()
        mockEncryptionKeySpec = mockk(relaxed = true)

        aesEncryptionHandler = AesEncryptionHandler(mockEncryptionKeySpec)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `encrypt then decrypt string successfully`() {
        val testString = "testString"
        val encryptedString = aesEncryptionHandler.encryptString(testString)
        assertThat(encryptedString).isNotEqualTo(testString)

        val decryptedString = aesEncryptionHandler.decryptString(encryptedString)
        assertThat(decryptedString).isEqualTo(Result.success(testString))
    }

    @Test
    fun `encrypt then decrypt string with exception`() {
        val testString = "testString"
        val encryptedString = aesEncryptionHandler.encryptString(testString)
        assertThat(encryptedString).isNotEqualTo(testString)

        every { AndroidKeystoreUtil.getOrCreateAesKey(any()) } returns keyGenerator.generateKey() // wrong key
        val decryptedString = aesEncryptionHandler.decryptString(encryptedString)
        assertThat(decryptedString.isFailure).isTrue()
        assertThat(decryptedString.exceptionOrNull()).isInstanceOf(AEADBadTagException::class.java)
    }
}
