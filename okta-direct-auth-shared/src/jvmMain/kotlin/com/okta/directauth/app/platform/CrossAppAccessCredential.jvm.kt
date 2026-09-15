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
import java.io.File
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.Properties
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private const val TAG = "CrossAppAccessCredential"
private const val TARGET_CLIENT_SECRET_PROPERTY = "xaaTargetClientSecret"
private const val TARGET_CLIENT_ASSERTION_PEM_PROPERTY = "xaaTargetClientAssertionPrivateKeyPem"
private const val TARGET_CLIENT_ASSERTION_KID_PROPERTY = "xaaTargetClientAssertionKid"
private const val TARGET_CLIENT_ID_PROPERTY = "xaaTargetClientId"
private const val IDP_CLIENT_SECRET_PROPERTY = "xaaIdpClientSecret"
private const val IDP_CLIENT_ASSERTION_PEM_PROPERTY = "xaaIdpClientAssertionPrivateKeyPem"
private const val IDP_CLIENT_ASSERTION_KID_PROPERTY = "xaaIdpClientAssertionKid"
private const val IDP_CLIENT_ID_PROPERTY = "xaaIdpClientId"
private const val JWT_BEARER_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
private val CLIENT_ASSERTION_LIFETIME = 5.minutes
private const val MAX_PARENT_DIRECTORIES_TO_SEARCH = 8

/**
 * JVM Desktop implementation: reads the Cross App Access target credential from
 * `local.properties` at runtime — deliberately not the build-time-baked [AppConfig] pattern used
 * for the rest of this sample's config. See the KDoc on the `expect` declaration for the full
 * security rationale.
 */
actual fun crossAppAccessTargetCredential(): TargetCredential {
    val localProperties = findLocalProperties()
    return resolveCredential(
        localProperties = localProperties,
        secretProperty = TARGET_CLIENT_SECRET_PROPERTY,
        pemProperty = TARGET_CLIENT_ASSERTION_PEM_PROPERTY,
        kidProperty = TARGET_CLIENT_ASSERTION_KID_PROPERTY,
        assertionClientId = localProperties.getProperty(TARGET_CLIENT_ID_PROPERTY, "").trim().ifBlank { AppConfig.CLIENT_ID }
    )
}

/**
 * JVM Desktop implementation: reads the Cross App Access requesting app's own credential from
 * `local.properties` at runtime. See [crossAppAccessTargetCredential] for the shared rationale.
 */
actual fun crossAppAccessIdpCredential(): TargetCredential {
    val localProperties = findLocalProperties()
    return resolveCredential(
        localProperties = localProperties,
        secretProperty = IDP_CLIENT_SECRET_PROPERTY,
        pemProperty = IDP_CLIENT_ASSERTION_PEM_PROPERTY,
        kidProperty = IDP_CLIENT_ASSERTION_KID_PROPERTY,
        assertionClientId = localProperties.getProperty(IDP_CLIENT_ID_PROPERTY, "").trim()
    )
}

/** Shared credential-selection logic for [crossAppAccessTargetCredential] and [crossAppAccessIdpCredential]. */
private fun resolveCredential(
    localProperties: Properties,
    secretProperty: String,
    pemProperty: String,
    kidProperty: String,
    assertionClientId: String,
): TargetCredential {
    val clientSecret = localProperties.getProperty(secretProperty, "").trim()
    val clientAssertionPem = localProperties.getProperty(pemProperty, "").trim()
    val clientAssertionKid = localProperties.getProperty(kidProperty, "").trim()

    return when {
        clientAssertionPem.isNotEmpty() -> {
            if (clientSecret.isNotEmpty()) {
                AppLogger.write(
                    TAG,
                    "Both $secretProperty and $pemProperty are set in local.properties; using the " +
                        "private_key_jwt assertion and ignoring $secretProperty."
                )
            }
            val privateKey = parsePkcs8PrivateKey(clientAssertionPem)
            AppLogger.write(
                TAG,
                "Using a private_key_jwt client assertion provider from local.properties (testing only; " +
                    "a fresh JWT is signed for every request)."
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
            AppLogger.write(TAG, "Using a client_secret from local.properties (testing only; $secretProperty).")
            TargetCredential.Secret(clientSecret)
        }

        else -> {
            AppLogger.write(TAG, "No $secretProperty or $pemProperty in local.properties; unconfigured.")
            TargetCredential.None
        }
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

/**
 * Searches for `local.properties` starting at the current working directory and walking up
 * parent directories, since the app may be launched from the repo root (Gradle `run`) or from an
 * unpacked distribution directory.
 */
private fun findLocalProperties(): Properties {
    var directory: File? = File(System.getProperty("user.dir")).absoluteFile
    repeat(MAX_PARENT_DIRECTORIES_TO_SEARCH) {
        val candidate = File(directory, "local.properties")
        if (candidate.isFile) {
            val properties = Properties()
            runCatching { candidate.inputStream().use { properties.load(it) } }
                .onFailure { AppLogger.write(TAG, "Failed to read $candidate: ${it.message}") }
            return properties
        }
        directory = directory?.parentFile
    }
    return Properties()
}
