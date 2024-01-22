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
package com.okta.authfoundation.credential

import com.okta.authfoundation.AuthFoundationDefaults
import kotlinx.serialization.Serializable

@Serializable
sealed interface SecurityOptions {
    val keyAlias: String
    val encryptionAlgorithm: String
    val userAuthenticationRequired: Boolean

    data class Default(
        override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias,
        override val encryptionAlgorithm: String = AuthFoundationDefaults.Encryption.algorithm
    ) : SecurityOptions {
        override val userAuthenticationRequired: Boolean
            get() = false
    }

    data class BiometricStrong(
        override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrong",
        override val encryptionAlgorithm: String = AuthFoundationDefaults.Encryption.algorithm
    ) : SecurityOptions {
        override val userAuthenticationRequired: Boolean
            get() = true
    }

    data class BiometricsStrongOrDeviceCredential(
        override val keyAlias: String = AuthFoundationDefaults.Encryption.keyAlias + ".biometricStrongOrDeviceCredential",
        override val encryptionAlgorithm: String = AuthFoundationDefaults.Encryption.algorithm
    ) : SecurityOptions {
        override val userAuthenticationRequired: Boolean
            get() = true
    }
}
