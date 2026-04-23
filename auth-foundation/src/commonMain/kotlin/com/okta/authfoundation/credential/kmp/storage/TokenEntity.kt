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
package com.okta.authfoundation.credential.kmp.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.okta.authfoundation.credential.kmp.storage.typeconverters.JsonObjectTypeConverter
import com.okta.authfoundation.credential.kmp.storage.typeconverters.StringStringMapTypeConverter
import kotlinx.serialization.json.JsonObject

@Entity(tableName = "CommonTokenEntity")
@TypeConverters(
    StringStringMapTypeConverter::class,
    JsonObjectTypeConverter::class
)
internal data class TokenEntity(
    @PrimaryKey
    val id: String,
    val clientId: String,
    val issuerUrl: String,
    val tokenType: String,
    val expiresIn: Int,
    val accessToken: ByteArray,
    val scope: String?,
    val refreshToken: String?,
    val idToken: String?,
    val deviceSecret: String?,
    val issuedTokenType: String?,
    val issuedAt: Long,
    val tags: Map<String, String>,
    val payloadData: JsonObject?,
    val encryptionExtras: Map<String, String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenEntity) return false
        return id == other.id &&
            clientId == other.clientId &&
            issuerUrl == other.issuerUrl &&
            tokenType == other.tokenType &&
            expiresIn == other.expiresIn &&
            accessToken.contentEquals(other.accessToken) &&
            scope == other.scope &&
            refreshToken == other.refreshToken &&
            idToken == other.idToken &&
            deviceSecret == other.deviceSecret &&
            issuedTokenType == other.issuedTokenType &&
            issuedAt == other.issuedAt &&
            tags == other.tags &&
            payloadData == other.payloadData &&
            encryptionExtras == other.encryptionExtras
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + clientId.hashCode()
        result = 31 * result + issuerUrl.hashCode()
        result = 31 * result + tokenType.hashCode()
        result = 31 * result + expiresIn
        result = 31 * result + accessToken.contentHashCode()
        result = 31 * result + (scope?.hashCode() ?: 0)
        result = 31 * result + (refreshToken?.hashCode() ?: 0)
        result = 31 * result + (idToken?.hashCode() ?: 0)
        result = 31 * result + (deviceSecret?.hashCode() ?: 0)
        result = 31 * result + (issuedTokenType?.hashCode() ?: 0)
        result = 31 * result + issuedAt.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (payloadData?.hashCode() ?: 0)
        result = 31 * result + encryptionExtras.hashCode()
        return result
    }
}
