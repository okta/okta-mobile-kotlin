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
package sample.okta.android

import com.okta.authfoundation.client.OAuth2ClientConfiguration
import com.okta.authfoundation.client.TokenInfo
import com.okta.authfoundation.credential.kmp.TokenData

internal object SampleHelper {
    const val CREDENTIAL_NAME_TAG_KEY: String = "sample.okta.android.credential.name"
    const val DEFAULT_SCOPE: String = "openid email profile offline_access"
}

/**
 * The KMP flows (and [WebAuthentication][com.okta.webauthenticationui.WebAuthentication], via the legacy
 * Android [com.okta.authfoundation.credential.Token], which also implements [TokenInfo]) return a
 * [TokenInfo] whose concrete runtime type is not [TokenData]. This app stores credentials via the KMP
 * [com.okta.authfoundation.credential.kmp.TokenCredentialManager], which requires a [TokenData] snapshot,
 * so every freshly-minted token must be converted explicitly rather than cast.
 *
 * [issuedAt] defaults to "now" since this is always called immediately after a token is minted.
 */
internal fun TokenInfo.toTokenData(configuration: OAuth2ClientConfiguration): TokenData =
    TokenData(
        id = id,
        tokenType = tokenType,
        expiresIn = expiresIn,
        accessToken = accessToken,
        scope = scope,
        refreshToken = refreshToken,
        idToken = idToken,
        deviceSecret = deviceSecret,
        issuedTokenType = issuedTokenType,
        configuration = configuration,
        issuedAt = configuration.clock.currentTimeEpochSecond()
    )
