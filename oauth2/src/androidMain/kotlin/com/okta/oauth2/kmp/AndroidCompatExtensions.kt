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
package com.okta.oauth2.kmp

import com.okta.authfoundation.InternalAuthFoundationApi
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.kmp.OAuth2Client as KmpOAuth2Client

/**
 * Creates a KMP [DeviceAuthorizationFlow] backed by a new KMP OAuth2Client
 * configured from this Android [OAuth2Client]'s settings.
 *
 * @param client the Android [OAuth2Client] whose configuration is used.
 * @return a [DeviceAuthorizationFlow] using the KMP HTTP stack.
 */
@OptIn(InternalAuthFoundationApi::class)
fun DeviceAuthorizationFlow(client: OAuth2Client): DeviceAuthorizationFlow {
    val issuerUrl = client.configuration.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
    val kmpClient: KmpOAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = issuerUrl,
                clientId = client.configuration.clientId,
                scope = client.configuration.defaultScope.split(" ")
            ).getOrThrow()
    return DeviceAuthorizationFlow(kmpClient)
}

/**
 * Creates a KMP [ResourceOwnerFlow] backed by a new KMP OAuth2Client
 * configured from this Android [OAuth2Client]'s settings.
 *
 * @param client the Android [OAuth2Client] whose configuration is used.
 * @return a [ResourceOwnerFlow] using the KMP HTTP stack.
 */
@OptIn(InternalAuthFoundationApi::class)
fun ResourceOwnerFlow(client: OAuth2Client): ResourceOwnerFlow {
    val issuerUrl = client.configuration.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
    val kmpClient: KmpOAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = issuerUrl,
                clientId = client.configuration.clientId,
                scope = client.configuration.defaultScope.split(" ")
            ).getOrThrow()
    return ResourceOwnerFlow(kmpClient)
}

/**
 * Creates a KMP [TokenExchangeFlow] backed by a new KMP OAuth2Client
 * configured from this Android [OAuth2Client]'s settings.
 *
 * @param client the Android [OAuth2Client] whose configuration is used.
 * @return a [TokenExchangeFlow] using the KMP HTTP stack.
 */
@OptIn(InternalAuthFoundationApi::class)
fun TokenExchangeFlow(client: OAuth2Client): TokenExchangeFlow {
    val issuerUrl = client.configuration.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
    val kmpClient: KmpOAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = issuerUrl,
                clientId = client.configuration.clientId,
                scope = client.configuration.defaultScope.split(" ")
            ).getOrThrow()
    return TokenExchangeFlow(kmpClient)
}

/**
 * Creates a KMP [SessionTokenFlow] backed by a new KMP OAuth2Client
 * configured from this Android [OAuth2Client]'s settings.
 *
 * @param client the Android [OAuth2Client] whose configuration is used.
 * @return a [SessionTokenFlow] using the KMP HTTP stack.
 */
@OptIn(InternalAuthFoundationApi::class)
fun SessionTokenFlow(client: OAuth2Client): SessionTokenFlow {
    val issuerUrl = client.configuration.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
    val kmpClient: KmpOAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = issuerUrl,
                clientId = client.configuration.clientId,
                scope = client.configuration.defaultScope.split(" ")
            ).getOrThrow()
    return SessionTokenFlow(kmpClient)
}

/**
 * Creates a KMP [AuthorizationCodeFlow] backed by a new KMP OAuth2Client
 * configured from this Android [OAuth2Client]'s settings.
 *
 * @param client the Android [OAuth2Client] whose configuration is used.
 * @return a [AuthorizationCodeFlow] using the KMP HTTP stack.
 */
@OptIn(InternalAuthFoundationApi::class)
fun AuthorizationCodeFlow(client: OAuth2Client): AuthorizationCodeFlow {
    val issuerUrl = client.configuration.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
    val kmpClient: KmpOAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = issuerUrl,
                clientId = client.configuration.clientId,
                scope = client.configuration.defaultScope.split(" ")
            ).getOrThrow()
    return AuthorizationCodeFlow(kmpClient)
}

/**
 * Creates a KMP [RedirectEndSessionFlow] backed by a new KMP OAuth2Client
 * configured from this Android [OAuth2Client]'s settings.
 *
 * @param client the Android [OAuth2Client] whose configuration is used.
 * @return a [RedirectEndSessionFlow] using the KMP HTTP stack.
 */
@OptIn(InternalAuthFoundationApi::class)
fun RedirectEndSessionFlow(client: OAuth2Client): RedirectEndSessionFlow {
    val issuerUrl = client.configuration.discoveryUrl.removeSuffix("/.well-known/openid-configuration")
    val kmpClient: KmpOAuth2Client =
        OAuth2ClientBuilder
            .create(
                issuerUrl = issuerUrl,
                clientId = client.configuration.clientId,
                scope = client.configuration.defaultScope.split(" ")
            ).getOrThrow()
    return RedirectEndSessionFlow(kmpClient)
}

/**
 * Resumes the Authorization Code flow using an Android [android.net.Uri].
 *
 * @param uri the redirect [android.net.Uri] received from the browser.
 * @param flowContext the [AuthorizationCodeFlowContext] returned by [AuthorizationCodeFlow.start].
 * @return a [Result] containing [com.okta.authfoundation.client.TokenInfo] on success.
 */
suspend fun AuthorizationCodeFlow.resume(
    uri: android.net.Uri,
    flowContext: AuthorizationCodeFlowContext,
): Result<com.okta.authfoundation.client.TokenInfo> = resume(uri.toString(), flowContext)

/**
 * Resumes the Redirect End Session flow using an Android [android.net.Uri].
 *
 * @param uri the post-logout redirect [android.net.Uri] received from the browser.
 * @param flowContext the [RedirectEndSessionFlowContext] returned by [RedirectEndSessionFlow.start].
 * @return a [Result] containing [Unit] on success.
 */
fun RedirectEndSessionFlow.resume(
    uri: android.net.Uri,
    flowContext: RedirectEndSessionFlowContext,
): Result<Unit> = resume(uri.toString(), flowContext)
