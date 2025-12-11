package com.okta.directauth.app.model

import androidx.compose.runtime.saveable.Saver
import com.okta.directauth.model.OobChannel
import com.okta.directauth.model.PrimaryFactor
import com.okta.directauth.model.SecondaryFactor

sealed class AuthMethod(val label: String) {
    open fun asFactor(passCode: String = ""): PrimaryFactor = when (this) {
        Password -> PrimaryFactor.Password(passCode)
        Mfa.Otp -> PrimaryFactor.Otp(passCode)
        Mfa.OktaVerify -> PrimaryFactor.Oob(OobChannel.PUSH)
        Mfa.Sms -> PrimaryFactor.Oob(OobChannel.SMS)
        Mfa.Voice -> PrimaryFactor.Oob(OobChannel.VOICE)
        Mfa.Passkeys -> PrimaryFactor.WebAuthn
    }

    // MFA capable Auth methods
    sealed class Mfa(label: String) : AuthMethod(label) {
        override fun asFactor(passCode: String): SecondaryFactor = when (this) {
            Otp -> PrimaryFactor.Otp(passCode)
            OktaVerify -> PrimaryFactor.Oob(OobChannel.PUSH)
            Sms -> PrimaryFactor.Oob(OobChannel.SMS)
            Voice -> PrimaryFactor.Oob(OobChannel.VOICE)
            Passkeys -> PrimaryFactor.WebAuthn
        }

        data object Otp : Mfa("OTP")
        data object OktaVerify : Mfa("Okta Verify")
        data object Sms : Mfa("SMS")
        data object Voice : Mfa("Voice")
        data object Passkeys : Mfa("Passkeys")
    }

    data object Password : AuthMethod("Password")

}

val mfaMethodSaver = Saver<AuthMethod.Mfa, String>(
    save = { it.label },
    restore = { label ->
        when (label) {
            "OTP" -> AuthMethod.Mfa.Otp
            "Okta Verify" -> AuthMethod.Mfa.OktaVerify
            "SMS" -> AuthMethod.Mfa.Sms
            "Voice" -> AuthMethod.Mfa.Voice
            "Passkeys" -> AuthMethod.Mfa.Passkeys
            else -> throw IllegalArgumentException("Unknown AuthMethod label: $label")
        }
    }
)

val AuthMethodSaver = Saver<AuthMethod, String>(
    save = { it.label },
    restore = { label ->
        when (label) {
            "Password" -> AuthMethod.Password
            "OTP" -> AuthMethod.Mfa.Otp
            "Okta Verify" -> AuthMethod.Mfa.OktaVerify
            "SMS" -> AuthMethod.Mfa.Sms
            "Voice" -> AuthMethod.Mfa.Voice
            "Passkeys" -> AuthMethod.Mfa.Passkeys
            else -> throw IllegalArgumentException("Unknown AuthMethod label: $label")
        }
    }
)