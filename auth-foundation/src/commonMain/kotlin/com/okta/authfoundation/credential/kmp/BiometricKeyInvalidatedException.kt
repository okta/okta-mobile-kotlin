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
 * Thrown when the biometric key protecting a stored token has been permanently invalidated —
 * for example, after the user enrolls a new biometric on the device.
 *
 * On the Android platform, this is surfaced as [Result.failure] from
 * [TokenStorage.getToken] instead of the platform-specific
 * `KeyPermanentlyInvalidatedException`.
 *
 * On Android, the invalidated token is **auto-deleted by default** for backward compatibility
 * (matching the legacy `BiometricTokenInvalidatedEvent.deleteInvalidatedToken = true` default).
 * Callers can inspect [tokenId] and [keyAlias] for logging or custom logic; deletion is no
 * longer required unless the app has suppressed the legacy event's default behavior.
 *
 * @param tokenId the ID of the token whose biometric key was invalidated.
 * @param keyAlias the Android keystore alias of the invalidated key.
 */
class BiometricKeyInvalidatedException(
    val tokenId: String,
    val keyAlias: String,
) : Exception("Biometric key invalidated for token $tokenId (alias: $keyAlias)")
