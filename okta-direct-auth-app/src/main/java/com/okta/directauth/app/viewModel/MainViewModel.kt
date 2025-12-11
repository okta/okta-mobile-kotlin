package com.okta.directauth.app.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.ChallengeGrantType
import com.okta.authfoundation.GrantType
import com.okta.directauth.DirectAuthenticationFlowBuilder
import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.app.BuildConfig
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.model.Continuation
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val scopes = listOf("openid", "profile", "email")

    val directAuth: DirectAuthenticationFlow = DirectAuthenticationFlowBuilder.create(BuildConfig.ISSUER, BuildConfig.CLIENT_ID, scopes) {
        authorizationServerId = BuildConfig.AUTHORIZATION_SERVER_ID
    }.getOrThrow()

    fun signIn(username: String, password: String, authMethod: AuthMethod): Job {
        println("Username: $username, Password: $password, AuthMethod: ${authMethod.label}")
        return viewModelScope.launch { directAuth.start(username, authMethod.asFactor(password)) }
    }

    fun resume(otp: String, mfaMethod: AuthMethod.Mfa, mfaRequired: DirectAuthenticationState.MfaRequired): Job {
        val mfaFactor = mfaMethod.asFactor(otp)
        val challengeGrantType = when (mfaFactor) {
            is PrimaryFactor.Oob -> ChallengeGrantType.OobMfa
            is PrimaryFactor.Otp -> ChallengeGrantType.OtpMfa
            PrimaryFactor.WebAuthn -> ChallengeGrantType.WebAuthnMfa
        }
        return viewModelScope.launch { mfaRequired.resume(mfaFactor, listOf(challengeGrantType)) }
    }

    fun pendingOob(oobPending: Continuation.OobPending): Job {
        return viewModelScope.launch { oobPending.proceed() }
    }

    fun prompt(prompt: Continuation.Prompt, code: String): Job {
        return viewModelScope.launch { prompt.proceed(code) }
    }

    fun transfer(transfer: Continuation.Transfer): Job {
        return viewModelScope.launch { transfer.proceed() }
    }

    fun reset() {
        directAuth.reset()
    }
}
