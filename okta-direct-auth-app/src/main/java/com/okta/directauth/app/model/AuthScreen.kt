package com.okta.directauth.app.model

import com.okta.directauth.model.DirectAuthenticationState

sealed class AuthScreen(val username: String) {
    class UsernameInput(username: String) : AuthScreen(username)
    class SelectAuthenticator(username: String) : AuthScreen(username)
    class PasswordAuthenticator(username: String) : AuthScreen(username)
    class OktaVerify(username: String, val mfaRequired: DirectAuthenticationState.MfaRequired?) : AuthScreen(username)
    class Otp(username: String, val mfaRequired: DirectAuthenticationState.MfaRequired?) : AuthScreen(username)
    class Passkeys(username: String, val mfaRequired: DirectAuthenticationState.MfaRequired?) : AuthScreen(username)
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
    class MfaRequired(username: String, val mfaRequired: DirectAuthenticationState.MfaRequired) : AuthScreen(username)
}
