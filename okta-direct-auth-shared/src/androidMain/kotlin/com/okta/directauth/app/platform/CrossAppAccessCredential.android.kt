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

private const val TAG = "CrossAppAccessCredential"
private const val JWT_BEARER_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
private val CLIENT_ASSERTION_LIFETIME = 5.minutes

/**
 * Android implementation: reads the Cross App Access target credential from
 * [AppConfig.XAA_TARGET_CLIENT_SECRET] / [AppConfig.XAA_TARGET_CLIENT_ASSERTION_PRIVATE_KEY_PEM],
 * generated at build time from `local.properties` — since an installed app cannot read the
 * developer machine's `local.properties` at runtime. See the KDoc on the `expect` declaration for
 * the full security rationale.
 */
actual fun crossAppAccessTargetCredential(): TargetCredential =
    resolveCredential(
        clientSecret = AppConfig.XAA_TARGET_CLIENT_SECRET.trim(),
        clientAssertionPem = AppConfig.XAA_TARGET_CLIENT_ASSERTION_PRIVATE_KEY_PEM.trim(),
        clientAssertionKid = AppConfig.XAA_TARGET_CLIENT_ASSERTION_KID.trim(),
        assertionClientId = AppConfig.XAA_TARGET_CLIENT_ID.trim().ifBlank { AppConfig.CLIENT_ID },
        secretPropertyName = "xaaTargetClientSecret",
        pemPropertyName = "xaaTargetClientAssertionPrivateKeyPem"
    )

/**
 * Android implementation: reads the Cross App Access requesting app's own credential from
 * [AppConfig.XAA_IDP_CLIENT_SECRET] / [AppConfig.XAA_IDP_CLIENT_ASSERTION_PRIVATE_KEY_PEM]. See
 * [crossAppAccessTargetCredential] for the shared rationale.
 */
actual fun crossAppAccessIdpCredential(): TargetCredential =
    resolveCredential(
        clientSecret = AppConfig.XAA_IDP_CLIENT_SECRET.trim(),
        clientAssertionPem = AppConfig.XAA_IDP_CLIENT_ASSERTION_PRIVATE_KEY_PEM.trim(),
        clientAssertionKid = AppConfig.XAA_IDP_CLIENT_ASSERTION_KID.trim(),
        assertionClientId = AppConfig.XAA_IDP_CLIENT_ID.trim(),
        secretPropertyName = "xaaIdpClientSecret",
        pemPropertyName = "xaaIdpClientAssertionPrivateKeyPem"
    )

/** Shared credential-selection logic for [crossAppAccessTargetCredential] and [crossAppAccessIdpCredential]. */
private fun resolveCredential(
    clientSecret: String,
    clientAssertionPem: String,
    clientAssertionKid: String,
    assertionClientId: String,
    secretPropertyName: String,
    pemPropertyName: String,
): TargetCredential =
    when {
        clientAssertionPem.isNotEmpty() -> {
            if (clientSecret.isNotEmpty()) {
                AppLogger.write(
                    TAG,
                    "Both $secretPropertyName and $pemPropertyName are set; using the private_key_jwt " +
                        "assertion and ignoring $secretPropertyName."
                )
            }
            val privateKey = parsePkcs8PrivateKey(clientAssertionPem)
            AppLogger.write(
                TAG,
                "Using a private_key_jwt client assertion provider baked from local.properties " +
                    "(testing only; a fresh JWT is signed for every request)."
            )
            TargetCredential.Assertion(
                ClientAssertionProvider { audience ->
                    ClientAssertion(
                        type = JWT_BEARER_CLIENT_ASSERTION_TYPE,
                        assertion = buildClientAssertionJwt(assertionClientId, audience, privateKey, clientAssertionKid)
                    )
                }
            )
        }

        clientSecret.isNotEmpty() -> {
            AppLogger.write(TAG, "Using a client_secret baked from local.properties (testing only; $secretPropertyName).")
            TargetCredential.Secret(clientSecret)
        }

        else -> {
            AppLogger.write(TAG, "No $secretPropertyName or $pemPropertyName; unconfigured.")
            TargetCredential.None
        }
    }

private fun buildClientAssertionJwt(
    clientId: String,
    audience: String,
    privateKey: PrivateKey,
    kid: String,
): String {
    val now = Date()
    val expiration = Date(now.time + CLIENT_ASSERTION_LIFETIME.inWholeMilliseconds)
    val builder =
        Jwts
            .builder()
            .issuer(clientId)
            .subject(clientId)
            .audience()
            .add(audience)
            .and()
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiration(expiration)
    if (kid.isNotEmpty()) {
        builder.header().keyId(kid).and()
    }
    return builder.signWith(privateKey).compact()
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
        "xaaTargetClientAssertionPrivateKeyPem must be a PKCS#8 RSA or EC private key (-----BEGIN PRIVATE KEY-----)."
    )
}
