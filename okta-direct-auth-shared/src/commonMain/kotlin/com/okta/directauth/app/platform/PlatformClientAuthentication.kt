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

import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.directauth.DirectAuthenticationFlowBuilder

/**
 * Applies a client secret or private_key_jwt [ClientAssertionProvider][com.okta.authfoundation.client.ClientAssertionProvider]
 * to [this] builder, so the Browser Sign-In (PAR) demo can be tried against a confidential
 * OAuth2 client.
 *
 * The private_key_jwt path registers a provider, which the SDK invokes fresh for every
 * token/PAR request — required for spec compliance, since a `jti` may only be used once and
 * `aud` must match the exact endpoint being called (see
 * https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth), neither of which a
 * single statically-built assertion could satisfy across more than one request.
 *
 * SECURITY — local testing only, on both platforms:
 * - On JVM Desktop, this reads `clientSecret`/`clientAssertionPrivateKeyPem` directly from
 *   `local.properties` at runtime, deliberately not via the [com.okta.directauth.app.AppConfig]
 *   pattern used for the rest of this sample's config, which bakes `local.properties` values into
 *   generated build-time constants.
 * - On Android, an installed app has no access to the developer machine's `local.properties` at
 *   runtime, so this instead reads the same two values baked into [com.okta.directauth.app.AppConfig]
 *   by the `generateAppConfig` Gradle task — i.e. embedded in the compiled APK.
 *
 * Confidential-client authentication (a client secret or a private key) must never ship in a real
 * mobile app or any other public/untrusted client: anything embedded in a distributed binary can
 * always be extracted from it, no matter which of the two mechanisms above is used to get it
 * there. This exists purely to make the confidential-client and PAR code paths runnable locally
 * for testing.
 *
 * For real deployments, load the secret from a proper secrets manager or KMS/HSM-backed signer
 * (e.g. AWS Secrets Manager, HashiCorp Vault, Google Secret Manager, or your cloud provider's KMS
 * for a private_key_jwt signer) — never from a checked-out properties file. For an
 * enterprise-managed mobile deployment (not a public app-store app), an MDM's managed app
 * configuration (Android Enterprise managed configurations, or an iOS/iPadOS managed app
 * configuration) can push the secret to the device at runtime instead of baking it into the
 * APK/IPA, and lets the secret be rotated or revoked centrally without a new app build. It only
 * raises the bar rather than removing the exposure, though: the secret still lands in the app's
 * sandbox on an end-user (if corporate-owned) device, so it can still be extracted by an attacker
 * who compromises that device — unlike a secret that only ever lives server-side.
 *
 * @param clientId the OAuth 2.0 client ID, used as the `iss`/`sub` claims of a private_key_jwt
 *   assertion
 */
expect fun OAuth2ClientBuilder.configureClientAuthentication(clientId: String)

/**
 * Applies a client secret or private_key_jwt [ClientAssertionProvider][com.okta.authfoundation.client.ClientAssertionProvider]
 * to [this] builder, so the Direct Authentication demo (username/password, OTP, OOB, WebAuthn)
 * can be tried against a confidential OAuth2 client.
 *
 * Same source, precedence, and security rationale as
 * [OAuth2ClientBuilder.configureClientAuthentication] — see its KDoc. The private_key_jwt path
 * is especially relevant here: Direct Authentication issues more than one client-authenticated
 * request per flow (e.g. an OOB poll makes repeated `/token` calls), and a
 * [ClientAssertionProvider][com.okta.authfoundation.client.ClientAssertionProvider] is invoked
 * fresh for each one, unlike a single statically-built assertion which could only ever satisfy
 * the first request.
 *
 * @param clientId the OAuth 2.0 client ID, used as the `iss`/`sub` claims of a private_key_jwt
 *   assertion
 */
expect fun DirectAuthenticationFlowBuilder.configureClientAuthentication(clientId: String)
