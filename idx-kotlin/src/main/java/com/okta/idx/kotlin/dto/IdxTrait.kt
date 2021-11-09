/*
 * Copyright 2021-Present Okta, Inc.
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
package com.okta.idx.kotlin.dto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.okta.idx.kotlin.client.IdxClient
import com.okta.idx.kotlin.client.IdxClientResult
import com.okta.idx.kotlin.dto.IdxRemediation.Type
import kotlinx.coroutines.delay
import okhttp3.HttpUrl
import okio.ByteString.Companion.decodeBase64

/**
 * Represents a collection of traits.
 */
class IdxTraitCollection<T> internal constructor(
    private val traits: Set<T>,
) : Set<T> by traits {
    /**
     * Returns a trait based on its type.
     */
    inline fun <reified Trait : T> get(): Trait? {
        val matched = firstOrNull { it is Trait } ?: return null
        return matched as Trait
    }
}

/** Describes the IdP associated with a remediation of type [Type.REDIRECT_IDP]. */
class IdxIdpTrait internal constructor(
    /** The IdPs id. */
    val id: String,
    /** The IdPs name. */
    val name: String,
    /** The IdPs redirectUrl. */
    val redirectUrl: HttpUrl,
) : IdxRemediation.Trait

/** Describes the recover action associated with an [IdxAuthenticator]. */
class IdxRecoverTrait internal constructor(
    /** The [IdxRemediation] associated with the recover action. */
    val remediation: IdxRemediation,
) : IdxAuthenticator.Trait

/** Describes the send action associated with an [IdxAuthenticator]. */
class IdxSendTrait internal constructor(
    /** The [IdxRemediation] associated with the send action. */
    val remediation: IdxRemediation,
) : IdxAuthenticator.Trait

/** Describes the resend action associated with an [IdxAuthenticator]. */
class IdxResendTrait internal constructor(
    /** The [IdxRemediation] associated with the resend action. */
    val remediation: IdxRemediation,
) : IdxAuthenticator.Trait

/** Describes the poll action associated with an [IdxAuthenticator]. */
class IdxPollTrait internal constructor(
    /** The [IdxRemediation] associated with the poll action. */
    internal val remediation: IdxRemediation,
    /** The wait between each poll in milliseconds. */
    internal val wait: Int,
    /** The id of the authenticator */
    internal val authenticatorId: String?,
) : IdxAuthenticator.Trait {
    /** Available to allow testing without a real delay. */
    internal var delayFunction: suspend (Long) -> Unit = ::delay

    /**
     * Poll the IDX APIs with the configuration provided from the [IdxAuthenticator].
     *
     * All polling delay/retry logic is handled internally.
     *
     * @return the [IdxClientResult] when the state changes.
     */
    suspend fun poll(client: IdxClient): IdxClientResult<IdxResponse> {
        var result: IdxClientResult<IdxResponse>
        var currentAuthenticatorId: String?
        var currentWait = wait
        var currentRemediation = remediation
        do {
            delayFunction(currentWait.toLong())
            result = client.proceed(currentRemediation)
            if (result is IdxClientResult.Error) {
                return result
            }
            val successResult = result as? IdxClientResult.Success<IdxResponse>
            val currentAuthenticator = successResult?.result?.authenticators?.current ?: return result
            currentAuthenticatorId = currentAuthenticator.id
            val pollTrait = currentAuthenticator.traits.get<IdxPollTrait>() ?: return result
            currentWait = pollTrait.wait
            currentRemediation = pollTrait.remediation
        } while (authenticatorId == currentAuthenticatorId)
        return result
    }
}

/** Describes the profile information associated with an [IdxAuthenticator]. */
class IdxProfileTrait internal constructor(
    /** Profile information describing the authenticator. This usually contains redacted information relevant to display to the user. */
    val profile: Map<String, String>,
) : IdxAuthenticator.Trait

/** Describes the TOTP information associated with an [IdxAuthenticator]. */
class IdxTotpTrait internal constructor(
    /** The base64 encoded image data associated with the QR code. */
    val imageData: String,

    /** The shared secret associated with the authenticator used for setup without a QR code. */
    val sharedSecret: String?
) : IdxAuthenticator.Trait {
    /** The [Bitmap] associated with the QR code TOTP registration information. */
    fun asImage(): Bitmap? {
        val bytes = imageData.substringAfter("data:image/png;base64,").decodeBase64()?.toByteArray() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
