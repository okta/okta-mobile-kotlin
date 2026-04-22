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
package com.okta.authfoundation.crypto

/**
 * Generates cryptographically secure random bytes.
 *
 * @param size the number of random bytes to generate.
 * @return a [ByteArray] of the specified size filled with secure random bytes.
 */
expect fun secureRandomBytes(size: Int): ByteArray

/**
 * Computes the SHA-256 digest of the given data.
 *
 * @param data the input bytes to hash.
 * @return a [ByteArray] containing the 32-byte SHA-256 digest.
 */
expect fun sha256Digest(data: ByteArray): ByteArray
