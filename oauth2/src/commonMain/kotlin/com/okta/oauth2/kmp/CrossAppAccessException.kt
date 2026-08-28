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

/**
 * Names which authorization server rejected a Cross App Access exchange.
 *
 * Both steps of the exchange ultimately call the same underlying token-request machinery, which
 * fails with the same generic transport error regardless of which server answered. Without this
 * type, a caller can only tell an IdP-side denial apart from a target-side rejection by parsing
 * the failure's message text. The original transport failure is always attached as [Throwable.cause],
 * so the server-provided error code and description remain reachable there.
 */
sealed class CrossAppAccessException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause) {
    /**
     * The first step failed at the **IdP authorization server** while exchanging the subject
     * assertion for an ID-JAG — for example, no trusted connection is configured between the
     * requesting app and the resource app, or the request was otherwise rejected before an
     * ID-JAG could be issued.
     */
    class IdpExchangeFailed internal constructor(
        message: String,
        cause: Throwable?,
    ) : CrossAppAccessException(message, cause)

    /**
     * The second step failed at the **resource authorization server** while redeeming an ID-JAG
     * for a resource access token — for example, the ID-JAG expired or was rejected, or the
     * target's discovery metadata could not be retrieved.
     */
    class TargetRedemptionFailed internal constructor(
        message: String,
        cause: Throwable?,
    ) : CrossAppAccessException(message, cause)
}
