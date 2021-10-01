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

import com.okta.idx.kotlin.dto.IdxApplication
import com.okta.idx.kotlin.dto.IdxRemediation
import com.okta.idx.kotlin.dto.IdxResponse
import com.okta.idx.kotlin.dto.IdxUser
import com.okta.idx.kotlin.dto.IdxMessageCollection
import com.okta.idx.kotlin.dto.TokenResponse
import kotlinx.serialization.json.Json

internal fun Response.toIdxResponse(json: Json): IdxResponse {
    val parsingContext = ParsingContext.create(json, this)
    val remediations = toIdxRemediationCollection(json, parsingContext)
    val topLevelMessages = messages?.value?.map { it.toIdxMessage() } ?: emptyList()
    val messageCollection = IdxMessageCollection(topLevelMessages)
    return IdxResponse(
        expiresAt = expiresAt,
        intent = intent.toIdxResponseIntent(),
        remediations = remediations,
        authenticators = parsingContext.authenticatorCollection,
        app = app?.value?.toIdxApplication(),
        user = user?.value?.toIdxUser(),
        messages = messageCollection,
        isLoginSuccessful = successWithInteractionCode != null,
        canCancel = remediations[IdxRemediation.Type.CANCEL] != null,
    )
}

private fun App.toIdxApplication(): IdxApplication {
    return IdxApplication(
        id = id,
        label = label,
        name = name,
    )
}

private fun User.toIdxUser(): IdxUser? {
    if (id == null) {
        return null
    }
    return IdxUser(id)
}

private fun String?.toIdxResponseIntent(): IdxResponse.Intent {
    return when (this) {
        "ENROLL_NEW_USER" -> IdxResponse.Intent.ENROLL_NEW_USER
        "LOGIN" -> IdxResponse.Intent.LOGIN
        "CREDENTIAL_ENROLLMENT" -> IdxResponse.Intent.CREDENTIAL_ENROLLMENT
        "CREDENTIAL_UNENROLLMENT" -> IdxResponse.Intent.CREDENTIAL_UNENROLLMENT
        "CREDENTIAL_RECOVERY" -> IdxResponse.Intent.CREDENTIAL_RECOVERY
        "CREDENTIAL_MODIFY" -> IdxResponse.Intent.CREDENTIAL_MODIFY
        else -> IdxResponse.Intent.UNKNOWN
    }
}

internal fun Token.toIdxResponse(): TokenResponse {
    return TokenResponse(
        accessToken = accessToken,
        expiresIn = expiresIn,
        refreshToken = refreshToken,
        idToken = idToken,
        scope = scope,
        tokenType = tokenType,
    )
}
