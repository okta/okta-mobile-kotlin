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
package com.okta.authfoundation.credential.events

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.okta.authfoundation.credential.Token
import com.okta.authfoundation.events.CredentialEvent

/**
 * Emitted when [KeyPermanentlyInvalidatedException] is thrown when trying to use an invalidated biometric
 * key while fetching a [Token].
 *
 * @deprecated Use [Result.failure] with
 * [com.okta.authfoundation.credential.kmp.BiometricKeyInvalidatedException] returned from
 * [com.okta.authfoundation.credential.kmp.TokenStorage.getToken] instead. On Android the
 * invalidated token is auto-deleted by default. For async notification on the KMP path, observe
 * [com.okta.authfoundation.credential.kmp.TokenCredentialManager.events] and filter for
 * [com.okta.authfoundation.credential.events.TokenStorageAccessErrorEvent] where
 * `exception is` [com.okta.authfoundation.credential.kmp.BiometricKeyInvalidatedException].
 */
@Deprecated(
    message =
        "Use Result.failure(BiometricKeyInvalidatedException) from TokenStorage.getToken() instead. " +
            "On Android, the invalidated token is auto-deleted by default. " +
            "For async notification on the KMP path, observe TokenCredentialManager.events and " +
            "filter for TokenStorageAccessErrorEvent where exception is BiometricKeyInvalidatedException.",
    level = DeprecationLevel.WARNING
)
class BiometricKeyInvalidatedEvent internal constructor(
    /**
     * The key with [keyAlias] that got invalidated.
     */
    val keyAlias: String,
) : CredentialEvent
