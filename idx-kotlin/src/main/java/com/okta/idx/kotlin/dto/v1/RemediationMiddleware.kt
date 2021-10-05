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
import com.okta.idx.kotlin.dto.IdxIdpTrait
import com.okta.idx.kotlin.dto.IdxAuthenticatorCollection
import com.okta.idx.kotlin.dto.IdxMessageCollection
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxRemediationCollection
import com.okta.idx.kotlin.dto.IdxTraitCollection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

internal fun Response.toIdxRemediationCollection(
    json: Json,
    parsingContext: ParsingContext,
): IdxRemediationCollection {
    val remediations = mutableListOf<IdxRemediation>()

    if (remediation?.value != null) {
        for (form in remediation.value) {
            remediations += form.toIdxRemediation(json, parsingContext)
        }
    }

    if (cancel != null) {
        remediations += cancel.toIdxRemediation(json, parsingContext)
    }

    if (successWithInteractionCode != null) {
        remediations += successWithInteractionCode.toIdxRemediation(json, parsingContext)
    }

    return IdxRemediationCollection(remediations)
}

internal fun Form.toIdxRemediation(
    json: Json,
    parsingContext: ParsingContext? = null
): IdxRemediation {
    val form = IdxRemediation.Form(value?.map { it.toIdxField(json, parsingContext, null) } ?: emptyList())
    val remediationType = name.toRemediationType()
    val traits = mutableSetOf<IdxRemediation.Trait>()

    toIdxIdpTrait()?.let { traits += it }

    val authenticatorsList = mutableListOf<IdxAuthenticator>()
    relatesTo?.let {
        for (relatesToElement in it) {
            parsingContext.authenticatorFor(relatesToElement)?.let { authenticator ->
                authenticatorsList += authenticator
            }
        }
    }

    return IdxRemediation(
        type = remediationType,
        name = name,
        form = form,
        authenticators = IdxAuthenticatorCollection(authenticatorsList),
        traits = IdxTraitCollection(traits),
        method = method,
        href = href,
        accepts = accepts,
        relatesTo = relatesTo
    )
}

private fun Form.toIdxIdpTrait(): IdxIdpTrait? {
    val v1Idp = idp ?: return null
    val id = v1Idp["id"]
    val name = v1Idp["name"]
    if (id != null && name != null) {
        return IdxIdpTrait(
            id = id,
            name = name,
            redirectUrl = href,
        )
    }
    return null
}

private fun FormValue.toIdxField(
    json: Json,
    parsingContext: ParsingContext?,
    parentFormValue: FormValue?
): IdxRemediation.Form.Field {
    // Fields default to visible, except there are circumstances where
    // fields (such as `id`) don't properly include a `visible: false`. As a result,
    // we need to infer visibility from other values.
    var actualVisibility = visible ?: true
    if (mutable == false && value != null) {
        actualVisibility = false
    }

    var valueAsForm: CompositeFormValue? = null
    var actualValue = value
    if (value is JsonObject) {
        val serializer = json.serializersModule.serializer<CompositeFormValue?>()
        valueAsForm = json.decodeFromJsonElement(serializer, value["form"]!!)
        if (valueAsForm != null) {
            actualValue = null
        } else {
            actualValue = value
        }
    }

    return IdxRemediation.Form.Field(
        name = name ?: parentFormValue?.name,
        label = label ?: parentFormValue?.label,
        type = type ?: "string",
        _value = actualValue,
        isVisible = actualVisibility,
        isMutable = mutable ?: true,
        isRequired = required ?: false,
        isSecret = secret ?: false,
        form = (form ?: valueAsForm)?.value?.toForm(json, parsingContext, this),
        options = options?.map { it.toIdxField(json, parsingContext, this) },
        messages = IdxMessageCollection(messages?.value?.map { it.toIdxMessage() } ?: emptyList()),
        authenticator = parsingContext.authenticatorFor(relatesTo),
    )
}

private fun String.toRemediationType(): IdxRemediation.Type {
    return when (this) {
        "issue" -> IdxRemediation.Type.ISSUE
        "identify" -> IdxRemediation.Type.IDENTIFY
        "identify-recovery" -> IdxRemediation.Type.IDENTIFY_RECOVERY
        "select-identify" -> IdxRemediation.Type.SELECT_IDENTIFY
        "select-enroll-profile" -> IdxRemediation.Type.SELECT_ENROLL_PROFILE
        "cancel" -> IdxRemediation.Type.CANCEL
        "activate-factor" -> IdxRemediation.Type.ACTIVATE_FACTOR
        "send" -> IdxRemediation.Type.SEND_CHALLENGE
        "resend" -> IdxRemediation.Type.RESEND_CHALLENGE
        "select-factor-authenticate" -> IdxRemediation.Type.SELECT_FACTOR_AUTHENTICATE
        "select-factor-enroll" -> IdxRemediation.Type.SELECT_FACTOR_ENROLL
        "challenge-factor" -> IdxRemediation.Type.CHALLENGE_FACTOR
        "select-authenticator-authenticate" -> IdxRemediation.Type.SELECT_AUTHENTICATOR_AUTHENTICATE
        "select-authenticator-enroll" -> IdxRemediation.Type.SELECT_AUTHENTICATOR_ENROLL
        "select-enrollment-channel" -> IdxRemediation.Type.SELECT_ENROLLMENT_CHANNEL
        "authenticator-verification-data" -> IdxRemediation.Type.AUTHENTICATOR_VERIFICATION_DATA
        "authenticator-enrollment-data" -> IdxRemediation.Type.AUTHENTICATOR_ENROLLMENT_DATA
        "enrollment-channel-data" -> IdxRemediation.Type.ENROLLMENT_CHANNEL_DATA
        "challenge-authenticator" -> IdxRemediation.Type.CHALLENGE_AUTHENTICATOR
        "poll" -> IdxRemediation.Type.POLL
        "enroll-poll" -> IdxRemediation.Type.ENROLL_POLL
        "recover" -> IdxRemediation.Type.RECOVER
        "enroll-factor" -> IdxRemediation.Type.ENROLL_FACTOR
        "enroll-authenticator" -> IdxRemediation.Type.ENROLL_AUTHENTICATOR
        "reenroll-authenticator" -> IdxRemediation.Type.REENROLL_AUTHENTICATOR
        "reenroll-authenticator-warning" -> IdxRemediation.Type.REENROLL_AUTHENTICATOR_WARNING
        "reset-authenticator" -> IdxRemediation.Type.RESET_AUTHENTICATOR
        "enroll-profile" -> IdxRemediation.Type.ENROLL_PROFILE
        "profile-attributes" -> IdxRemediation.Type.PROFILE_ATTRIBUTES
        "select-idp" -> IdxRemediation.Type.SELECT_IDP
        "select-platform" -> IdxRemediation.Type.SELECT_PLATFORM
        "factor-poll-verification" -> IdxRemediation.Type.FACTOR_POLL_VERIFICATION
        "qr-refresh" -> IdxRemediation.Type.QR_REFRESH
        "device-challenge-poll" -> IdxRemediation.Type.DEVICE_CHALLENGE_POLL
        "cancel-polling" -> IdxRemediation.Type.CANCEL_POLLING
        "device-apple-sso-extension" -> IdxRemediation.Type.DEVICE_APPLE_SSO_EXTENSION
        "launch-authenticator" -> IdxRemediation.Type.LAUNCH_AUTHENTICATOR
        "redirect" -> IdxRemediation.Type.REDIRECT
        "redirect-idp" -> IdxRemediation.Type.REDIRECT_IDP
        "cancel-transaction" -> IdxRemediation.Type.CANCEL_TRANSACTION
        "skip" -> IdxRemediation.Type.SKIP
        "challenge-poll" -> IdxRemediation.Type.CHALLENGE_POLL
        else -> IdxRemediation.Type.UNKNOWN
    }
}

private fun List<FormValue>?.toForm(
    json: Json,
    parsingContext: ParsingContext?,
    parentFormValue: FormValue,
): IdxRemediation.Form? {
    if (isNullOrEmpty()) {
        return null
    }
    return IdxRemediation.Form(
        map { it.toIdxField(json, parsingContext, parentFormValue) }
    )
}
