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
package com.okta.directauth.app.model

import com.okta.directauth.model.DirectAuthenticationState

sealed class AuthScreen(
    val username: String,
) {
    class UsernameInput(
        username: String,
    ) : AuthScreen(username)

    class SelectAuthenticator(
        username: String,
    ) : AuthScreen(username)

    class PasswordAuthenticator(
        username: String,
    ) : AuthScreen(username)

    class OktaVerify(
        username: String,
        val mfaRequired: DirectAuthenticationState.MfaRequired?,
    ) : AuthScreen(username)

    class Otp(
        username: String,
        val mfaRequired: DirectAuthenticationState.MfaRequired?,
    ) : AuthScreen(username)

    class Passkeys(
        username: String,
        val mfaRequired: DirectAuthenticationState.MfaRequired?,
    ) : AuthScreen(username)

    class Sms(
        username: String,
        val mfaRequired: DirectAuthenticationState.MfaRequired?,
        val codeSent: Boolean = false,
    ) : AuthScreen(username)

    class Voice(
        username: String,
        val mfaRequired: DirectAuthenticationState.MfaRequired?,
        val codeSent: Boolean = false,
    ) : AuthScreen(username)

    class MfaRequired(
        username: String,
        val mfaRequired: DirectAuthenticationState.MfaRequired,
    ) : AuthScreen(username)
}
