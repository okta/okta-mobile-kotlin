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
package com.okta.authfoundation.credential.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.storage.typeconverters.JsonObjectTypeConverter
import com.okta.authfoundation.credential.storage.typeconverters.StringStringMapTypeConverter
import kotlinx.serialization.json.JsonObject

@Entity
@TypeConverters(
    StringStringMapTypeConverter::class,
    JsonObjectTypeConverter::class
)
internal data class TokenEntity(
    @PrimaryKey
    val id: String,
    val encryptedToken: ByteArray,
    val tags: Map<String, String>,
    val payloadData: JsonObject?,
    val keyAlias: String,
    val tokenEncryptionType: EncryptionType,
    val biometricTimeout: Int?,
    val encryptionExtras: Map<String, String>
) {
    internal enum class EncryptionType {
        DEFAULT,
        BIO_ONLY,
        BIO_AND_PIN;

        internal fun toSecurity(keyAlias: String, biometricTimeout: Int?): Credential.Security {
            return when (this) {
                DEFAULT -> Credential.Security.Default(keyAlias)
                BIO_ONLY -> {
                    if (biometricTimeout == null) throw IllegalStateException("BIO_ONLY TokenEntity stored without timeout")
                    Credential.Security.BiometricStrong(biometricTimeout, keyAlias)
                }
                BIO_AND_PIN -> {
                    if (biometricTimeout == null) throw IllegalStateException("BIO_AND_PIN TokenEntity stored without timeout")
                    Credential.Security.BiometricStrongOrDeviceCredential(biometricTimeout, keyAlias)
                }
            }
        }

        internal companion object {
            internal fun fromSecurity(security: Credential.Security): EncryptionType {
                return when (security) {
                    is Credential.Security.Default -> DEFAULT
                    is Credential.Security.BiometricStrong -> BIO_ONLY
                    is Credential.Security.BiometricStrongOrDeviceCredential -> BIO_AND_PIN
                }
            }
        }
    }

    val security: Credential.Security
        get() = tokenEncryptionType.toSecurity(keyAlias, biometricTimeout)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenEntity

        if (id != other.id) return false
        if (!encryptedToken.contentEquals(other.encryptedToken)) return false
        if (tags != other.tags) return false
        if (payloadData != other.payloadData) return false
        if (keyAlias != other.keyAlias) return false
        if (tokenEncryptionType != other.tokenEncryptionType) return false
        if (encryptionExtras != other.encryptionExtras) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedToken.contentHashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (payloadData?.hashCode() ?: 0)
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + tokenEncryptionType.hashCode()
        result = 31 * result + encryptionExtras.hashCode()
        return result
    }
}
