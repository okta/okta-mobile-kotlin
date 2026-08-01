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
package com.okta.authfoundation.credential.kmp

/**
 * Platform-agnostic signal thrown by a [TokenEncryptionHandler.decrypt] implementation when the key
 * protecting the ciphertext has been permanently invalidated (for example, an Android Keystore key
 * invalidated by new biometric enrollment).
 *
 * [RoomTokenStorage][com.okta.authfoundation.credential.kmp.storage.RoomTokenStorage] catches this in
 * [TokenStorage.getToken] and rethrows it as [BiometricKeyInvalidatedException] with the token ID, so
 * platform-specific invalidation exceptions never leak through the KMP [TokenEncryptionHandler] contract.
 *
 * @param keyAlias the platform-specific alias of the invalidated key.
 */
class TokenEncryptionKeyInvalidatedException(
    val keyAlias: String,
) : Exception("Encryption key invalidated (alias: $keyAlias)")
