# Okta Direct Authentication

Authenticate users using Okta's Direct Authentication API, enabling native sign-in experiences supporting Multi-Factor Authentication (MFA).

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting Started](#getting-started)
  - [Creating a Flow](#creating-a-flow)
  - [Observing Authentication State](#observing-authentication-state)
- [Authentication Flows](#authentication-flows)
  - [Starting Authentication](#starting-authentication)
  - [Handling MFA](#handling-mfa)
  - [Handling Continuations](#handling-continuations)
  - [Handling Errors](#handling-errors)
  - [Resetting the Flow](#resetting-the-flow)
- [Complete Example](#complete-example)
- [Sample Application](#sample-application)
- [Additional Resources](#additional-resources)

## Overview

This library provides the classes and methods necessary to implement native sign-in, directed by the developer, to follow specific workflows to meet your application's user experience.

Unlike browser-based authentication flows, Direct Authentication gives you full control over the UI while leveraging Okta's authentication backend. This enables you to build fully native sign-in experiences that support:

- Password authentication
- One-Time Passcode (OTP)
- Out-of-Band authentication (Push, SMS, Voice)
- WebAuthn/Passkeys (Coming soon)
- Multi-Factor Authentication (MFA)
- Self-Service Password Recovery (SSPR)

## Requirements

- Android API 23+
- Okta org with Direct Authentication enabled
- Client application configured for Direct Authentication grant types

## Installation

**Current Version: 0.0.1**

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.okta.kotlin:okta-direct-auth:0.0.1")
}
```

See the [CHANGELOG](CHANGELOG.md) for release history and migration guides.

## Getting Started

### Creating a Flow

Create a `DirectAuthenticationFlow` instance using the builder. You'll need your Okta issuer URL, client ID, and the scopes you want to request:

```kotlin
import com.okta.directauth.DirectAuthenticationFlowBuilder
import com.okta.directauth.api.DirectAuthenticationFlow

val directAuth: DirectAuthenticationFlow =
    DirectAuthenticationFlowBuilder
        .create(
            issuerUrl = "https://your-org.okta.com",
            clientId = "your-client-id",
            scope = listOf("openid", "profile", "email")
        ) {
            // Optional: specify authorization server ID for custom auth servers
            authorizationServerId = "default"
        }.getOrThrow()
```

For self-service password recovery flows, create a separate flow with the recovery intent:

```kotlin
import com.okta.directauth.model.DirectAuthenticationIntent

val recoveryFlow: DirectAuthenticationFlow =
    DirectAuthenticationFlowBuilder
        .create(
            issuerUrl = "https://your-org.okta.com",
            clientId = "your-client-id",
            scope = listOf("okta.myAccount.password.manage")
        ) {
            directAuthenticationIntent = DirectAuthenticationIntent.RECOVERY
        }.getOrThrow()
```

### Observing Authentication State

The `DirectAuthenticationFlow` exposes a `StateFlow` that emits authentication state changes. Observe this flow in your ViewModel or Activity:

```kotlin
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

viewModelScope.launch {
    directAuth.authenticationState.collect { state ->
        when (state) {
            is DirectAuthenticationState.Idle -> {
                // Ready to start authentication
            }
            is DirectAuthenticationState.MfaRequired -> {
                // Primary auth succeeded, MFA required
            }
            is DirectAuthenticationState.Authenticated -> {
                // Success! Access tokens available
                val accessToken = state.token.accessToken
                val idToken = state.token.idToken
            }
            is DirectAuthenticationState.Canceled -> {
                // Authentication was canceled
            }
            is DirectAuthContinuation.OobPending -> {
                // Waiting for out-of-band verification (push, SMS, voice)
            }
            is DirectAuthContinuation.Prompt -> {
                // Server requires additional input
            }
            is DirectAuthContinuation.Transfer -> {
                // Device transfer with binding code
            }
            is DirectAuthenticationError -> {
                // Handle error
            }
        }
    }
}
```

## Authentication Flows

### Starting Authentication

Start authentication by calling `start()` with a username and primary factor:

#### Password Authentication

```kotlin
import com.okta.directauth.model.PrimaryFactor

directAuth.start(
    username = "user@example.com",
    factor = PrimaryFactor.Password("user-password")
)
```

#### OTP Authentication

```kotlin
directAuth.start(
    username = "user@example.com",
    factor = PrimaryFactor.Otp("123456")
)
```

#### Out-of-Band Authentication (Push, SMS, Voice)

```kotlin
import com.okta.directauth.model.OobChannel

// Okta Verify Push
directAuth.start(
    username = "user@example.com",
    factor = PrimaryFactor.Oob(OobChannel.PUSH)
)

// SMS
directAuth.start(
    username = "user@example.com",
    factor = PrimaryFactor.Oob(OobChannel.SMS)
)

// Voice Call
directAuth.start(
    username = "user@example.com",
    factor = PrimaryFactor.Oob(OobChannel.VOICE)
)
```

#### WebAuthn/Passkeys

```kotlin
directAuth.start(
    username = "user@example.com",
    factor = PrimaryFactor.WebAuthn
)
```

### Handling MFA

When primary authentication succeeds but MFA is required, the flow emits `DirectAuthenticationState.MfaRequired`. Resume the flow with a secondary factor:

```kotlin
import com.okta.authfoundation.ChallengeGrantType

when (val state = directAuth.authenticationState.value) {
    is DirectAuthenticationState.MfaRequired -> {
        // Resume with OTP
        state.resume(
            factor = PrimaryFactor.Otp("123456"),
            grantTypesForChallengeTypes = listOf(ChallengeGrantType.OtpMfa)
        )

        // Or resume with Push notification
        state.resume(
            factor = PrimaryFactor.Oob(OobChannel.PUSH),
            grantTypesForChallengeTypes = listOf(ChallengeGrantType.OobMfa)
        )

        // Or resume with WebAuthn
        state.resume(
            factor = PrimaryFactor.WebAuthn,
            grantTypesForChallengeTypes = listOf(ChallengeGrantType.WebAuthnMfa)
        )
    }
}
```

### Handling Continuations

#### Out-of-Band Pending

When using push notifications, SMS, or voice authentication, the flow enters an `OobPending` state while waiting for the user to complete verification on their device:

```kotlin
when (val state = directAuth.authenticationState.value) {
    is DirectAuthContinuation.OobPending -> {
        // Show polling UI with countdown
        val expiresInSeconds = state.expirationInSeconds

        // Poll for completion
        state.proceed()
    }
}
```

#### Device Transfer

When authenticating with Okta Verify that requires a number challenge, the user must verify a binding code on their registered device:

```kotlin
when (val state = directAuth.authenticationState.value) {
    is DirectAuthContinuation.Transfer -> {
        // Display the binding code to the user
        val bindingCode = state.bindingCode
        val expiresInSeconds = state.expirationInSeconds

        // Poll for completion after user verifies on their device
        state.proceed()
    }
}
```

#### Prompts

When authentication requires additional input during authentication:

```kotlin
when (val state = directAuth.authenticationState.value) {
    is DirectAuthContinuation.Prompt -> {
        // Collect additional code from user and proceed
        state.proceed(code = "user-entered-code")
    }
}
```

### Handling Errors

Authentication errors are emitted as `DirectAuthenticationError`:

```kotlin
when (val state = directAuth.authenticationState.value) {
    is DirectAuthenticationError -> {
        when (state) {
            is DirectAuthenticationError.OAuth2Error -> {
                val errorCode = state.error
                val errorDescription = state.errorDescription
            }
            is DirectAuthenticationError.HttpError -> {
                val statusCode = state.statusCode
            }
            is DirectAuthenticationError.InternalError -> {
                val errorCode = state.errorCode
                val exception = state.exception
            }
        }
    }
}
```

### Resetting the Flow

Reset the authentication flow to start over:

```kotlin
directAuth.reset()
```

Call `reset()` when:
- User wants to start over with a different username
- Recovering from an unrecoverable error
- User cancels an ongoing operation

## Complete Example

Here's a complete ViewModel example demonstrating the authentication flow:

```kotlin
class AuthViewModel : ViewModel() {

    private val directAuth = DirectAuthenticationFlowBuilder
        .create(
            issuerUrl = BuildConfig.ISSUER,
            clientId = BuildConfig.CLIENT_ID,
            scope = listOf("openid", "profile", "email")
        ).getOrThrow()

    val authState = directAuth.authenticationState

    fun signInWithPassword(username: String, password: String) {
        viewModelScope.launch {
            directAuth.start(username, PrimaryFactor.Password(password))
        }
    }

    fun signInWithOtp(username: String, otp: String) {
        viewModelScope.launch {
            directAuth.start(username, PrimaryFactor.Otp(otp))
        }
    }

    fun signInWithPush(username: String) {
        viewModelScope.launch {
            directAuth.start(username, PrimaryFactor.Oob(OobChannel.PUSH))
        }
    }

    fun resumeMfaWithOtp(mfaRequired: DirectAuthenticationState.MfaRequired, otp: String) {
        viewModelScope.launch {
            mfaRequired.resume(
                PrimaryFactor.Otp(otp),
                listOf(ChallengeGrantType.OtpMfa)
            )
        }
    }

    fun resumeMfaWithPush(mfaRequired: DirectAuthenticationState.MfaRequired) {
        viewModelScope.launch {
            mfaRequired.resume(
                PrimaryFactor.Oob(OobChannel.PUSH),
                listOf(ChallengeGrantType.OobMfa)
            )
        }
    }

    fun pollOobPending(oobPending: DirectAuthContinuation.OobPending) {
        viewModelScope.launch {
            oobPending.proceed()
        }
    }

    fun handleTransfer(transfer: DirectAuthContinuation.Transfer) {
        viewModelScope.launch {
            transfer.proceed()
        }
    }

    fun submitPrompt(prompt: DirectAuthContinuation.Prompt, code: String) {
        viewModelScope.launch {
            prompt.proceed(code)
        }
    }

    fun reset() {
        directAuth.reset()
    }
}
```

## Sample Application

For a complete working example, see the `okta-direct-auth-app` module in this repository. It demonstrates:

- Username/password authentication
- MFA with multiple factors (OTP, Push, SMS, Voice)
- Out-of-band polling with countdown timers
- Device transfer with binding codes
- Self-service password recovery
- Error handling and recovery

## Additional Resources

- [CHANGELOG](CHANGELOG.md) - Release history and migration guides
- [API Documentation](https://okta.github.io/okta-mobile-kotlin/okta-direct-auth/index.html)
- [Okta Developer Documentation](https://developer.okta.com/docs/guides/configure-direct-auth-grants/)
