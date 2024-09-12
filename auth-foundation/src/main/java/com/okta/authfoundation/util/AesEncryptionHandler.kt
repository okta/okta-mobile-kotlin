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
import android.security.keystore.KeyProperties
import android.util.Base64
import com.okta.authfoundation.InternalAuthFoundationApi
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

@InternalAuthFoundationApi
class AesEncryptionHandler(
    private val encryptionKeySpec: KeyGenParameterSpec = defaultEncryptionKeySpec
) {
    companion object {
        private const val ENCRYPTION_KEY_ALIAS = "com.authfoundation.preferences.datastore.aesKey"
        private const val SEPARATOR = ","
        private val defaultEncryptionKeySpec by lazy {
            KeyGenParameterSpec.Builder(
                ENCRYPTION_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        }
    }

    fun encryptString(string: String): String {
        val aesKey = AndroidKeystoreUtil.getOrCreateAesKey(encryptionKeySpec)
        val cipher = getCipher().apply { init(Cipher.ENCRYPT_MODE, aesKey) }
        val encryptedStringByteArray = cipher.doFinal(string.toByteArray())
        val encryptedString = Base64.encodeToString(encryptedStringByteArray, Base64.NO_WRAP)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR + encryptedString
    }

    fun decryptString(encryptedString: String): Result<String> = runCatching {
        val aesKey = AndroidKeystoreUtil.getOrCreateAesKey(encryptionKeySpec)
        val (ivBase64, encryptedAesStringBase64) = encryptedString.split(SEPARATOR, limit = 2)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val encryptedAesString = Base64.decode(encryptedAesStringBase64, Base64.NO_WRAP)
        val cipher = getCipher().apply { init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv)) }
        cipher.doFinal(encryptedAesString).decodeToString()
    }

    fun resetEncryptionKey() {
        AndroidKeystoreUtil.deleteKey(encryptionKeySpec.keystoreAlias)
    }

    private fun getCipher(): Cipher = Cipher.getInstance(
        KeyProperties.KEY_ALGORITHM_AES + "/" +
            KeyProperties.BLOCK_MODE_GCM + "/" +
            KeyProperties.ENCRYPTION_PADDING_NONE
    )
}
