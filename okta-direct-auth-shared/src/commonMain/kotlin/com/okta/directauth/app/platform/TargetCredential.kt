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
package com.okta.directauth.app.platform

import com.okta.authfoundation.client.ClientAssertionProvider

/**
 * A Cross App Access client credential — either the resource app target's (resolved by
 * [crossAppAccessTargetCredential]) or the dedicated requesting-app IdP client's (resolved by
 * [crossAppAccessIdpCredential]). Only the target strictly requires one — the SDK enforces it
 * there — while the IdP client's is optional, since Okta's own "AI agent" registration supports a
 * credential-less "Client ID only" requesting app. Both are resolved the same way when present, so
 * they share this one type rather than two near-identical ones.
 *
 * Carries an already-built [ClientAssertionProvider] for [Assertion] rather than a raw PEM string,
 * because parsing a PKCS#8 private key requires JVM/Android crypto APIs unavailable in
 * `commonMain` — the actual signing work stays inside the platform actual, alongside the identical
 * pattern already used for this sample's primary client credential.
 */
sealed class TargetCredential {
    /** A client secret to authenticate with at the target. */
    class Secret(
        val value: String,
    ) : TargetCredential()

    /** A private_key_jwt provider to authenticate with at the target. */
    class Assertion(
        val provider: ClientAssertionProvider,
    ) : TargetCredential()

    /** Neither a secret nor a private-key PEM was configured. */
    data object None : TargetCredential()
}

/**
 * Resolves the Cross App Access resource app target's credential from `xaaTargetClientSecret` /
 * `xaaTargetClientAssertionPrivateKeyPem`, sourced per platform exactly like this sample's primary
 * client credential (see `PlatformClientAuthentication.kt`):
 * - JVM Desktop reads `local.properties` at runtime.
 * - Android reads the build-time-baked [com.okta.directauth.app.AppConfig] constants, since an
 *   installed app cannot read the developer machine's `local.properties`.
 *
 * SECURITY — developer testing only, on both platforms. Never embed a client secret or private
 * key in a shipped application; anything baked into a distributed binary can be extracted from
 * it. This exists purely to make the Cross App Access sample runnable locally. The returned value
 * is never displayed or logged — only *which* mechanism was chosen is logged, never the value.
 */
expect fun crossAppAccessTargetCredential(): TargetCredential

/**
 * Resolves the Cross App Access requesting app's OWN (IdP-side) credential from `xaaIdpClientSecret`
 * / `xaaIdpClientAssertionPrivateKeyPem`, sourced per platform identically to
 * [crossAppAccessTargetCredential] — see that function's KDoc for the full security rationale,
 * which applies here without exception.
 */
expect fun crossAppAccessIdpCredential(): TargetCredential
