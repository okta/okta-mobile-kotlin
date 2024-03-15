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

    fun getOrCreateAesKey(keyGenParameterSpec: KeyGenParameterSpec): Key {
        if (keyStore.containsAlias(keyGenParameterSpec.keystoreAlias)) {
            return keyStore.getKey(keyGenParameterSpec.keystoreAlias, null)
        }

        val aesKeyGenerator = getAesKeyGenerator()
        aesKeyGenerator.init(keyGenParameterSpec)
        return aesKeyGenerator.generateKey()
    }
}
