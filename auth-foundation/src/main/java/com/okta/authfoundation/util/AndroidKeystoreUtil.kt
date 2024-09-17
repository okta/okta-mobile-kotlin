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
import com.okta.authfoundation.InternalAuthFoundationApi
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.KeyGenerator

@InternalAuthFoundationApi
object AndroidKeystoreUtil {
    val keyStore: KeyStore by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        ks
    }

    fun getRsaKeyPairGenerator(): KeyPairGenerator {
        return KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )
    }

    private fun getAesKeyGenerator(): KeyGenerator {
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    }

    fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
    }

    fun getOrCreateAesKey(keyGenParameterSpec: KeyGenParameterSpec): Key {
        if (keyStore.containsAlias(keyGenParameterSpec.keystoreAlias)) {
            return keyStore.getKey(keyGenParameterSpec.keystoreAlias, null)
        }

        val aesKeyGenerator = getAesKeyGenerator()
        aesKeyGenerator.init(keyGenParameterSpec)
        return aesKeyGenerator.generateKey()
    }
}
