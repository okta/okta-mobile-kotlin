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
package com.okta.authfoundation.jwt

/**
 * Verifies an EC signature using the given public key coordinates and curve.
 *
 * @param x the X coordinate of the EC public key (decoded from JWK base64url `x` field).
 * @param y the Y coordinate of the EC public key (decoded from JWK base64url `y` field).
 * @param crv the curve name from the JWK `crv` field (e.g. `"P-256"`, `"P-384"`, `"P-521"`).
 * @param data the signed data (the JWT header.payload bytes).
 * @param signature the raw P1363-encoded ECDSA signature (decoded from JWT signature field).
 * @return `true` if the signature is valid, `false` otherwise.
 */
internal expect fun verifyEcSignature(
    x: ByteArray,
    y: ByteArray,
    crv: String,
    data: ByteArray,
    signature: ByteArray,
): Boolean
