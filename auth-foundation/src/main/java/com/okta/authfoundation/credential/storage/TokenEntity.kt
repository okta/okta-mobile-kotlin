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
    val payloadData: JsonObject,
    val keyAlias: String,
    val tokenEncryptionType: EncryptionType,
    val isDefault: Boolean,
    val encryptionExtras: Map<String, String>
) {
    internal enum class EncryptionType {
        NON_BIO,
        BIO_ONLY,
        BIO_AND_PIN
    }

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
        if (isDefault != other.isDefault) return false
        return encryptionExtras == other.encryptionExtras
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedToken.contentHashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + payloadData.hashCode()
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + tokenEncryptionType.hashCode()
        result = 31 * result + isDefault.hashCode()
        result = 31 * result + encryptionExtras.hashCode()
        return result
    }
}