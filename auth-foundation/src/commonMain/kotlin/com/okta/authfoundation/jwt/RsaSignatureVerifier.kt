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
 * Verifies an RS256 signature using RSA public key components.
 *
 * @param modulus the RSA modulus bytes.
 * @param exponent the RSA exponent bytes.
 * @param data the signed data bytes.
 * @param signature the signature bytes to verify.
 * @return true if the signature is valid, false otherwise.
 */
internal expect fun verifyRs256Signature(
    modulus: ByteArray,
    exponent: ByteArray,
    data: ByteArray,
    signature: ByteArray,
): Boolean
