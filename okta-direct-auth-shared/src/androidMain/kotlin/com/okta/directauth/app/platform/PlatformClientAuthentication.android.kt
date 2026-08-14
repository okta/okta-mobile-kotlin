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

import com.okta.authfoundation.client.ClientAssertion
import com.okta.authfoundation.client.ClientAssertionProvider
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.directauth.app.AppConfig
import com.okta.directauth.app.util.AppLogger
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private const val TAG = "LocalTestClientAuth"
private const val JWT_BEARER_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
private val CLIENT_ASSERTION_LIFETIME = 5.minutes

/**
 * Android implementation: reads a client secret or private_key_jwt signing key from
 * [AppConfig.CLIENT_SECRET]/[AppConfig.CLIENT_ASSERTION_PRIVATE_KEY_PEM], generated at build
 * time from `local.properties` by the `generateAppConfig` Gradle task.
 *
 * SECURITY — local testing only. Unlike the JVM Desktop actual (which reads `local.properties`
 * at runtime and never bakes it into the build), this DOES embed the secret into the compiled
 * APK: an installed Android app has no access to the developer machine's `local.properties` at
 * runtime, so build-time baking is the only way to make this demo runnable on-device. This exists
 * purely to exercise the confidential-client and PAR code paths locally. Never ship a client
 * secret or private key in a real app — anything embedded in a distributed APK can be extracted
 * from it. See the KDoc on the `expect` declaration for the full rationale and recommended
 * alternatives.
 *
 * For private_key_jwt, this registers a [ClientAssertionProvider] that the SDK invokes fresh for
 * every token/PAR request, signing a new JWT scoped to the exact [audience][ClientAssertionProvider.provide]
 * each time — required for spec compliance (a `jti` may only be used once, and `aud` must match
 * the endpoint actually being called; see
 * https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth).
 */
actual fun OAuth2ClientBuilder.configureClientAuthentication(clientId: String) {
    val clientSecret = AppConfig.CLIENT_SECRET.trim()
    val clientAssertionPem = AppConfig.CLIENT_ASSERTION_PRIVATE_KEY_PEM.trim()

    when {
        clientAssertionPem.isNotEmpty() -> {
            if (clientSecret.isNotEmpty()) {
                AppLogger.write(
                    TAG,
                    "Both clientSecret and clientAssertionPrivateKeyPem are set in local.properties; " +
                        "using the private_key_jwt assertion and ignoring clientSecret."
                )
            }
            val privateKey = parsePkcs8PrivateKey(clientAssertionPem)
            this.clientAssertionProvider =
                ClientAssertionProvider { audience ->
                    ClientAssertion(
                        type = JWT_BEARER_CLIENT_ASSERTION_TYPE,
                        assertion = buildClientAssertionJwt(clientId, audience, privateKey)
                    )
                }
            AppLogger.write(
                TAG,
                "Using a private_key_jwt client assertion provider baked from local.properties (testing " +
                    "only; a fresh JWT is signed for every request)."
            )
        }

        clientSecret.isNotEmpty() -> {
            this.clientSecret = clientSecret
            AppLogger.write(TAG, "Using a client_secret baked from local.properties (testing only).")
        }

        else -> {
            AppLogger.write(
                TAG,
                "No clientSecret or clientAssertionPrivateKeyPem in local.properties; continuing as a public client."
            )
        }
    }
}

private fun buildClientAssertionJwt(
    clientId: String,
    audience: String,
    privateKey: PrivateKey,
): String {
    val now = Date()
    val expiration = Date(now.time + CLIENT_ASSERTION_LIFETIME.inWholeMilliseconds)
    return Jwts
        .builder()
        .issuer(clientId)
        .subject(clientId)
        .audience()
        .add(audience)
        .and()
        .id(UUID.randomUUID().toString())
        .issuedAt(now)
        .expiration(expiration)
        .signWith(privateKey)
        .compact()
}

/**
 * Parses a PKCS#8 PEM-encoded private key (RSA or EC). PKCS#1 keys (headers of the form
 * `-----BEGIN RSA PRIVATE KEY-----`) are not supported; convert with
 * `openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem` first.
 */
private fun parsePkcs8PrivateKey(pem: String): PrivateKey {
    val base64 = pem.replace(Regex("-----(BEGIN|END)[^-]*-----"), "").replace(Regex("\\s"), "")
    val der = Base64.getDecoder().decode(base64)
    val spec = PKCS8EncodedKeySpec(der)
    listOf("RSA", "EC").forEach { algorithm ->
        try {
            return KeyFactory.getInstance(algorithm).generatePrivate(spec)
        } catch (_: InvalidKeySpecException) {
            // Not this key type; try the next one.
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unsupported key algorithm: $algorithm", e)
        }
    }

    throw IllegalStateException(
        "clientAssertionPrivateKeyPem must be a PKCS#8 RSA or EC private key (-----BEGIN PRIVATE KEY-----)."
    )
}
