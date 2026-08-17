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
import com.okta.directauth.DirectAuthenticationFlowBuilder
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

private const val TAG = "LocalTestClientAuth"
private const val CLIENT_SECRET_PROPERTY = "clientSecret"
private const val CLIENT_ASSERTION_PEM_PROPERTY = "clientAssertionPrivateKeyPem"
private const val JWT_BEARER_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
private val CLIENT_ASSERTION_LIFETIME = 5.minutes
private const val MAX_PARENT_DIRECTORIES_TO_SEARCH = 8

/**
 * JVM Desktop implementation: reads a client secret or private_key_jwt signing key from
 * `local.properties` at runtime. See the KDoc on the `expect` declaration for the security
 * rationale — this is for local testing only, and specifically not the build-time-baked
 * [com.okta.directauth.app.AppConfig] pattern used for the rest of this sample's config.
 *
 * For private_key_jwt, this registers a [ClientAssertionProvider] that the SDK invokes fresh for
 * every token/PAR request, signing a new JWT scoped to the exact [audience][ClientAssertionProvider.provide]
 * each time — required for spec compliance (a `jti` may only be used once, and `aud` must match
 * the endpoint actually being called; see
 * https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth).
 */
actual fun OAuth2ClientBuilder.configureClientAuthentication(clientId: String) {
    applyLocalClientAuthentication(
        clientId = clientId,
        setClientSecret = { this.clientSecret = it },
        setClientAssertionProvider = { this.clientAssertionProvider = it }
    )
}

/**
 * JVM Desktop implementation for the Direct Authentication demo. Same source
 * (`local.properties`) and precedence as [OAuth2ClientBuilder.configureClientAuthentication] —
 * see its KDoc for the full security rationale.
 */
actual fun DirectAuthenticationFlowBuilder.configureClientAuthentication(clientId: String) {
    applyLocalClientAuthentication(
        clientId = clientId,
        setClientSecret = { this.clientSecret = it },
        setClientAssertionProvider = { this.clientAssertionProvider = it }
    )
}

/**
 * Reads a client secret or private_key_jwt signing key from `local.properties` and applies
 * whichever is present via [setClientSecret]/[setClientAssertionProvider], shared by both
 * [OAuth2ClientBuilder] and [DirectAuthenticationFlowBuilder].
 */
private fun applyLocalClientAuthentication(
    clientId: String,
    setClientSecret: (String) -> Unit,
    setClientAssertionProvider: (ClientAssertionProvider) -> Unit,
) {
    val localProperties = findLocalProperties()
    val clientSecret = localProperties.getProperty(CLIENT_SECRET_PROPERTY, "").trim()
    val clientAssertionPem = localProperties.getProperty(CLIENT_ASSERTION_PEM_PROPERTY, "").trim()

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
            setClientAssertionProvider(
                ClientAssertionProvider { audience ->
                    ClientAssertion(
                        type = JWT_BEARER_CLIENT_ASSERTION_TYPE,
                        assertion = buildClientAssertionJwt(clientId, audience, privateKey)
                    )
                }
            )
            AppLogger.write(
                TAG,
                "Using a private_key_jwt client assertion provider from local.properties (testing only; " +
                    "a fresh JWT is signed for every request)."
            )
        }

        clientSecret.isNotEmpty() -> {
            setClientSecret(clientSecret)
            AppLogger.write(TAG, "Using a client_secret from local.properties (testing only).")
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
