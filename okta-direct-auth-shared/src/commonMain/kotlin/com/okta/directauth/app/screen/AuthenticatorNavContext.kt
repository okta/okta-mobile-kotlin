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
package com.okta.directauth.app.screen

/**
 * Carries navigation callbacks shared across the authenticator screen hierarchy.
 *
 * Passed as a context parameter so that [AuthenticatorScreenScaffold] and the individual
 * authenticator screens receive these callbacks implicitly, without threading them through
 * every function signature.
 *
 * @param backToSignIn Navigate back to the username entry screen and reset the auth flow.
 * @param verifyWithSomethingElse Navigate to the authenticator selection screen.
 *   `null` when the screen should not offer a "Verify with something else" option.
 */
class AuthenticatorNavContext(
    val backToSignIn: () -> Unit,
    val verifyWithSomethingElse: (() -> Unit)? = null,
)
