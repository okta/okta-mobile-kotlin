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
package com.okta.idx.kotlin.dto.v1

import com.okta.idx.kotlin.dto.IdxAuthenticator
import com.okta.idx.kotlin.dto.IdxAuthenticatorCollection
import com.okta.idx.kotlin.dto.IdxPollTrait
import com.okta.idx.kotlin.dto.IdxProfileTrait
import com.okta.idx.kotlin.dto.IdxRecoverTrait
import com.okta.idx.kotlin.dto.IdxResendTrait
import com.okta.idx.kotlin.dto.IdxSendTrait
import com.okta.idx.kotlin.dto.IdxTrait
import com.okta.idx.kotlin.dto.IdxTraitCollection

internal fun Response.toIdxAuthenticatorCollection(): IdxAuthenticatorCollection {
    val result = mutableListOf<IdxAuthenticator>()
    currentAuthenticatorEnrollment?.value?.apply {
        result += toIdxAuthenticator(IdxAuthenticator.State.ENROLLING)
    }
    currentAuthenticator?.value?.apply {
        result += toIdxAuthenticator(IdxAuthenticator.State.AUTHENTICATING)
    }
    recoveryAuthenticator?.value?.apply {
        result += toIdxAuthenticator(IdxAuthenticator.State.RECOVERY)
    }
    authenticatorEnrollments?.value?.let {
        for (authenticator in it) {
            result += authenticator.toIdxAuthenticator(IdxAuthenticator.State.ENROLLED)
        }
    }
    authenticators?.value?.let {
        for (authenticator in it) {
            result += authenticator.toIdxAuthenticator(IdxAuthenticator.State.NORMAL)
        }
    }
    return IdxAuthenticatorCollection(result)
}

private fun Authenticator.toIdxAuthenticator(state: IdxAuthenticator.State): IdxAuthenticator {
    val traits = mutableSetOf<IdxTrait>()

    recover?.toIdxRemediation()?.let { traits += IdxRecoverTrait(it) }
    send?.toIdxRemediation()?.let { traits += IdxSendTrait(it) }
    resend?.toIdxRemediation()?.let { traits += IdxResendTrait(it) }
    poll?.toIdxRemediation()?.let { traits += IdxPollTrait(it) }
    profile?.let { traits += IdxProfileTrait(it) }

    return IdxAuthenticator(
        id = id,
        displayName = displayName,
        type = type.asIdxAuthenticatorType(),
        key = key,
        state = state,
        methods = methods.asIdxAuthenticatorMethods(),
        methodNames = methods.asMethodNames(),
        traits = IdxTraitCollection(traits),
    )
}

private fun String.asIdxAuthenticatorType(): IdxAuthenticator.Kind {
    return when(this) {
        "app" -> IdxAuthenticator.Kind.APP
        "email" -> IdxAuthenticator.Kind.EMAIL
        "phone" -> IdxAuthenticator.Kind.PHONE
        "password" -> IdxAuthenticator.Kind.PASSWORD
        "security_question" -> IdxAuthenticator.Kind.SECURITY_QUESTION
        "device" -> IdxAuthenticator.Kind.DEVICE
        "security_key" -> IdxAuthenticator.Kind.SECURITY_KEY
        "federated" -> IdxAuthenticator.Kind.FEDERATED
        else -> IdxAuthenticator.Kind.UNKNOWN
    }
}

private fun List<Map<String, String>>?.asIdxAuthenticatorMethods(): List<IdxAuthenticator.Method>? {
    if (this == null) return null
    val result = mutableListOf<IdxAuthenticator.Method>()
    for (map in this) {
        val type = map["type"]
        if (type != null) {
            result += type.asIdxAuthenticatorMethod()
        }
    }
    return result
}

private fun String.asIdxAuthenticatorMethod(): IdxAuthenticator.Method {
    return when (this) {
        "sms" -> IdxAuthenticator.Method.SMS
        "voice" -> IdxAuthenticator.Method.VOICE
        "email" -> IdxAuthenticator.Method.EMAIL
        "push" -> IdxAuthenticator.Method.PUSH
        "crypto" -> IdxAuthenticator.Method.CRYPTO
        "signedNonce" -> IdxAuthenticator.Method.SIGNED_NONCE
        "totp" -> IdxAuthenticator.Method.TOTP
        "password" -> IdxAuthenticator.Method.PASSWORD
        "webauthn" -> IdxAuthenticator.Method.WEB_AUTHN
        "security_question" -> IdxAuthenticator.Method.SECURITY_QUESTION
        else -> IdxAuthenticator.Method.UNKNOWN
    }
}

private fun List<Map<String, String>>?.asMethodNames(): List<String>? {
    if (this == null) return null
    val result = mutableListOf<String>()
    for (map in this) {
        val type = map["type"]
        if (type != null) {
            result += type
        }
    }
    return result
}
