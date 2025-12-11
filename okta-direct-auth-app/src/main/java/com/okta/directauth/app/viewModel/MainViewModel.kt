package com.okta.directauth.app.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.okta.authfoundation.ChallengeGrantType
import com.okta.directauth.DirectAuthenticationFlowBuilder
import com.okta.directauth.api.DirectAuthenticationFlow
import com.okta.directauth.app.BuildConfig
import com.okta.directauth.app.model.AuthMethod
import com.okta.directauth.model.Continuation
import com.okta.directauth.model.DirectAuthenticationState
import com.okta.directauth.model.PrimaryFactor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Main ViewModel that manages the Direct Authentication flow.
 *
 * This ViewModel handles all authentication-related operations including:
 * - Starting authentication with username and primary factor (password, OTP, etc.)
 * - Resuming authentication with MFA when required
 * - Handling continuation states (OOB polling, prompts, transfers)
 * - Resetting the authentication flow
 *
 * The ViewModel maintains a reference to the DirectAuthenticationFlow SDK instance
 * and exposes methods that UI screens can call to progress through the auth flow.
 */
class MainViewModel : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val scopes = listOf("openid", "profile", "email")

    /**
     * The Direct Authentication SDK flow instance.
     *
     * This provides access to:
     * - authenticationState: StateFlow of the current authentication state
     * - start(): Initiates authentication with a username and factor
     * - reset(): Resets the authentication flow to idle state
     */
    val directAuth: DirectAuthenticationFlow = DirectAuthenticationFlowBuilder.create(BuildConfig.ISSUER, BuildConfig.CLIENT_ID, scopes) {
        authorizationServerId = BuildConfig.AUTHORIZATION_SERVER_ID
    }.getOrThrow()

    /**
     * Initiates authentication with a username and authentication method.
     *
     * This is the primary entry point for authentication. Called when:
     * - User enters password on PasswordScreen
     * - User submits OTP code on CodeEntryScreen
     * - User initiates Okta Verify push on OktaVerifyScreen
     * - User initiates SMS/Voice authentication
     *
     * The method converts the AuthMethod to a PrimaryFactor and starts the authentication flow.
     * The flow will emit state updates through directAuth.authenticationState.
     *
     * @param username The username to authenticate
     * @param password The password or code (empty string for push-based auth)
     * @param authMethod The authentication method (Password, OTP, SMS, Voice, OktaVerify, Passkeys)
     * @return Job that can be cancelled if the user navigates away
     */
    fun signIn(username: String, password: String, authMethod: AuthMethod): Job {
        Log.i(TAG, "signIn() called - username: $username, authMethod: ${authMethod.label}")
        return viewModelScope.launch {
            Log.d(TAG, "Starting authentication with ${authMethod.label}")
            directAuth.start(username, authMethod.asFactor(password))
        }
    }

    /**
     * Resumes authentication with an MFA (Multi-Factor Authentication) method.
     *
     * Called when the user needs to provide a second factor after successful primary authentication.
     * This happens when:
     * - Password authentication succeeds but MFA is required by policy
     * - User previously selected an initial auth method and now needs to verify with MFA
     *
     * The method:
     * 1. Converts the MFA method to a SecondaryFactor
     * 2. Determines the appropriate challenge grant type (OobMfa, OtpMfa, WebAuthnMfa)
     * 3. Resumes the MfaRequired state with the second factor
     *
     * @param otp The verification code (for OTP/SMS/Voice) or empty string (for push)
     * @param mfaMethod The MFA method to use (Otp, SMS, Voice, OktaVerify, Passkeys)
     * @param mfaRequired The MfaRequired state that needs to be resumed
     * @return Job that can be cancelled if the user navigates away
     */
    fun resume(otp: String, mfaMethod: AuthMethod.Mfa, mfaRequired: DirectAuthenticationState.MfaRequired): Job {
        Log.i(TAG, "resume() called - mfaMethod: ${mfaMethod.label}")
        val mfaFactor = mfaMethod.asFactor(otp)
        val challengeGrantType = when (mfaFactor) {
            is PrimaryFactor.Oob -> ChallengeGrantType.OobMfa
            is PrimaryFactor.Otp -> ChallengeGrantType.OtpMfa
            PrimaryFactor.WebAuthn -> ChallengeGrantType.WebAuthnMfa
        }
        Log.d(TAG, "Resuming MFA with ${mfaMethod.label}, challengeGrantType: $challengeGrantType")
        return viewModelScope.launch { mfaRequired.resume(mfaFactor, listOf(challengeGrantType)) }
    }

    /**
     * Polls for Out-of-Band (OOB) authentication completion.
     *
     * Called periodically when waiting for the user to:
     * - Approve a push notification in Okta Verify app
     * - Click a link sent via SMS
     * - Answer a voice call
     *
     * The method calls proceed() on the OobPending continuation, which polls the server
     * to check if the user has completed the OOB challenge on their device.
     *
     * @param oobPending The OobPending continuation state to poll
     * @return Job representing the polling operation
     */
    fun pendingOob(oobPending: Continuation.OobPending): Job {
        Log.i(TAG, "pendingOob() called - polling for OOB completion (expires in ${oobPending.expirationInSeconds}s)")
        return viewModelScope.launch {
            Log.d(TAG, "Proceeding with OOB pending state")
            oobPending.proceed()
        }
    }

    /**
     * Submits a code in response to a server prompt during authentication.
     *
     * Called when the server requires additional input during the authentication flow.
     * This is a Continuation.Prompt state that occurs when the server needs more
     * information from the user (e.g., additional OTP code, recovery code).
     *
     * @param prompt The Prompt continuation state requiring input
     * @param code The code entered by the user
     * @return Job representing the submission operation
     */
    fun prompt(prompt: Continuation.Prompt, code: String): Job {
        Log.i(TAG, "prompt() called - submitting code in response to server prompt")
        return viewModelScope.launch {
            Log.d(TAG, "Proceeding with prompt continuation")
            prompt.proceed(code)
        }
    }

    /**
     * Proceeds with device transfer authentication.
     *
     * Called when authenticating via device transfer (e.g., Okta Verify with binding code).
     * The user verifies the binding code shown on this device with the code on their
     * authenticating device, then approves the request.
     *
     * This method initiates polling to wait for the user to complete the verification
     * on their other device.
     *
     * @param transfer The Transfer continuation state
     * @return Job representing the transfer polling operation
     */
    fun transfer(transfer: Continuation.Transfer): Job {
        Log.i(TAG, "transfer() called - initiating device transfer with binding code: ${transfer.bindingCode}")
        return viewModelScope.launch {
            Log.d(TAG, "Proceeding with transfer continuation (expires in ${transfer.expirationInSeconds}s)")
            transfer.proceed()
        }
    }

    /**
     * Resets the authentication flow to its initial idle state.
     *
     * Called when:
     * - User clicks "Back to sign in" to start over
     * - User signs out after successful authentication
     * - Authentication encounters an unrecoverable error
     * - User cancels an ongoing authentication operation
     *
     * After calling reset(), the app should navigate back to the username entry screen.
     */
    fun reset() {
        Log.i(TAG, "reset() called - resetting authentication flow to idle state")
        directAuth.reset()
    }
}
