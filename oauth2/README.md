# Okta OAuth2

Standard OAuth2 authentication flows for Kotlin Multiplatform (Android + JVM), including Resource Owner Password, Device Authorization, Authorization Code with PKCE, Token Exchange (Native SSO), Session Token, and Redirect End Session.

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting Started](#getting-started)
  - [Creating an OAuth2Client](#creating-an-oauth2client)
- [Authentication Flows](#authentication-flows)
  - [Resource Owner Flow](#resource-owner-flow)
  - [Device Authorization Flow](#device-authorization-flow)
  - [Authorization Code Flow (Browser Sign-In)](#authorization-code-flow-browser-sign-in)
  - [Token Exchange Flow](#token-exchange-flow)
  - [Session Token Flow](#session-token-flow)
  - [Redirect End Session Flow](#redirect-end-session-flow)
- [Complete Example](#complete-example)
- [Java Usage (CompletableFuture API)](#java-usage-completablefuture-api)
- [Sample Applications](#sample-applications)
- [Additional Resources](#additional-resources)

## Overview

This module provides KMP flow classes for standard OAuth2 grant types. All flows are in the `com.okta.oauth2.kmp` package, return Kotlin `Result` types, and require an `OAuth2Client` from the `auth-foundation` module.

Each flow follows a consistent pattern:
- **Single-step flows** (`ResourceOwnerFlow`, `TokenExchangeFlow`, `SessionTokenFlow`) — call `start()` and get a `Result<TokenInfo>`.
- **Two-step flows** (`DeviceAuthorizationFlow`, `AuthorizationCodeFlow`, `RedirectEndSessionFlow`) — call `start()` to get a context object, then `resume()` to complete the flow.

## Requirements

- Android API 26+ or JVM (Java 11+)
- Okta org with the relevant OAuth2 grant types enabled
- Client application configured with the appropriate grant types on your authorization server

## Installation

**Current Version: 3.0.0**

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.okta.kotlin:oauth2:3.0.0")
}
```

## Getting Started

### Creating an OAuth2Client

Create an `OAuth2Client` using the builder from `auth-foundation`. You'll need your Okta issuer URL, client ID, and the scopes you want to request:

```kotlin
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.kmp.OAuth2Client

val client: OAuth2Client =
    OAuth2ClientBuilder
        .create(
            issuerUrl = "https://your-org.okta.com",
            clientId = "your-client-id",
            scope = listOf("openid", "profile", "email", "offline_access")
        ) {
            // Optional: specify authorization server ID for custom auth servers
            authorizationServerId = "default"
        }.getOrThrow()
```

#### Custom Endpoint Overrides

By default the SDK discovers endpoints from `{issuerUrl}/.well-known/openid-configuration`. Use `OAuth2EndpointOverrides` to override individual endpoints or skip discovery entirely when all 8 fields are provided:

```kotlin
import com.okta.authfoundation.client.OAuth2EndpointOverrides

val client: OAuth2Client =
    OAuth2ClientBuilder
        .create(
            issuerUrl = "https://your-org.okta.com",
            clientId = "your-client-id",
            scope = listOf("openid", "profile", "email", "offline_access")
        ) {
            // Override only the token endpoint (e.g. route through a proxy)
            endpointOverrides = OAuth2EndpointOverrides(
                tokenEndpoint = "https://proxy.example.com/token"
            )
        }.getOrThrow()
```

When all 8 endpoint fields are non-null the SDK skips the discovery HTTP request entirely, reducing startup latency. All override values must be valid HTTPS URLs.

## Authentication Flows

### Resource Owner Flow

> [Okta Developer Guide: Resource Owner Password](https://developer.okta.com/docs/guides/implement-grant-type/ropassword/main/)

Exchange a username and password for tokens using the Resource Owner Password grant:

```kotlin
import com.okta.oauth2.kmp.ResourceOwnerFlow

val flow = ResourceOwnerFlow(client)
flow.start(
    username = "user@example.com",
    password = "user-password",
    scope = "openid profile email offline_access"
).fold(
    onSuccess = { tokenInfo ->
        val accessToken = tokenInfo.accessToken
        val idToken = tokenInfo.idToken
    },
    onFailure = { error ->
        // Handle error
    }
)
```

### Device Authorization Flow

> [Okta Developer Guide: Device Authorization](https://developer.okta.com/docs/guides/device-authorization-grant/main/)

Start a device code flow, display the user code and verification URI, then poll until the user approves:

```kotlin
import com.okta.oauth2.kmp.DeviceAuthorizationFlow

val flow = DeviceAuthorizationFlow(client)

// Step 1: Request a device code
val context = flow.start(scope = "openid profile email offline_access").getOrThrow()

// Step 2: Display the user code and verification URI to the user
println("Go to: ${context.verificationUri}")
println("Enter code: ${context.userCode}")
println("Expires in: ${context.expiresIn} seconds")
// context.verificationUriComplete is also available (URI with code pre-filled)

// Step 3: Poll until the user approves or the code expires
flow.resume(context).fold(
    onSuccess = { tokenInfo ->
        val accessToken = tokenInfo.accessToken
    },
    onFailure = { error ->
        when (error) {
            is DeviceAuthorizationFlow.TimeoutException -> {
                // Device code expired before user approved
            }
            else -> {
                // Handle other errors
            }
        }
    }
)
```

### Authorization Code Flow (Browser Sign-In)

> [Okta Developer Guide: Authorization Code with PKCE](https://developer.okta.com/docs/guides/implement-grant-type/authcodepkce/main/)

Perform Authorization Code + PKCE authentication by opening a browser for Okta authorization and capturing the redirect callback:

```kotlin
import com.okta.oauth2.kmp.AuthorizationCodeFlow

val flow = AuthorizationCodeFlow(client)

// Step 1: Build the authorization URL
val context = flow.start(
    redirectUrl = "your-app-scheme:/callback",
    scope = "openid profile email offline_access"
).getOrThrow()

// Step 2: Open context.url in a browser (platform-specific)
// On Android, use Chrome Custom Tabs via web-authentication-ui
// On Desktop, use LocalhostBrowserRedirectHandler

// Step 3: Capture the redirect URI and exchange for tokens
flow.resume(uri = capturedRedirectUri, flowContext = context).fold(
    onSuccess = { tokenInfo ->
        val accessToken = tokenInfo.accessToken
    },
    onFailure = { error ->
        // Handle error
    }
)
```

### Token Exchange Flow

> [Okta Developer Guide: Configure Native SSO](https://developer.okta.com/docs/guides/configure-native-sso/main/)

Exchange an existing ID token and device secret for new tokens (Native SSO):

```kotlin
import com.okta.oauth2.kmp.TokenExchangeFlow

val flow = TokenExchangeFlow(client)
flow.start(
    idToken = "existing-id-token",
    deviceSecret = "existing-device-secret",
    scope = "openid profile email offline_access"
).fold(
    onSuccess = { tokenInfo ->
        val accessToken = tokenInfo.accessToken
    },
    onFailure = { error ->
        // Handle error
    }
)
```

### Session Token Flow

> [Okta Developer Reference: Authentication API](https://developer.okta.com/docs/reference/api/authn/)

Exchange a session token (obtained from the Okta Authentication API) for OAuth2 tokens via a server-side redirect:

```kotlin
import com.okta.oauth2.kmp.SessionTokenFlow

val flow = SessionTokenFlow(client)
flow.start(
    sessionToken = "session-token-from-authn-api",
    redirectUrl = "your-app-scheme:/callback",
    scope = "openid profile email offline_access"
).fold(
    onSuccess = { tokenInfo ->
        val accessToken = tokenInfo.accessToken
    },
    onFailure = { error ->
        // Handle error
    }
)
```

### Redirect End Session Flow

Perform a browser-based logout by redirecting to the Okta end-session endpoint:

```kotlin
import com.okta.oauth2.kmp.RedirectEndSessionFlow

val flow = RedirectEndSessionFlow(client)

// Step 1: Build the logout URL
val context = flow.start(
    idToken = "current-id-token",
    redirectUrl = "your-app-scheme:/logout-callback"
).getOrThrow()

// Step 2: Open context.url in a browser (platform-specific)

// Step 3: Capture the redirect URI and validate
flow.resume(uri = capturedRedirectUri, flowContext = context).fold(
    onSuccess = {
        // Logout completed
    },
    onFailure = { error ->
        // Handle error
    }
)
```

## Complete Example

Here's a complete ViewModel example managing all OAuth2 flows:

```kotlin
class OAuth2ViewModel : ViewModel() {

    private val client = OAuth2ClientBuilder
        .create(
            issuerUrl = BuildConfig.ISSUER,
            clientId = BuildConfig.CLIENT_ID,
            scope = listOf("openid", "profile", "email", "offline_access")
        ) {
            authorizationServerId = BuildConfig.AUTHORIZATION_SERVER_ID
        }.getOrThrow()

    private val _flowState = MutableStateFlow<OAuth2FlowState>(OAuth2FlowState.Idle)
    val flowState = _flowState.asStateFlow()

    private var activeJob: Job? = null

    fun startResourceOwner(username: String, password: String) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = ResourceOwnerFlow(client)
            flow.start(username, password, "openid profile email offline_access").fold(
                onSuccess = { _flowState.value = OAuth2FlowState.Authenticated(it) },
                onFailure = { _flowState.value = OAuth2FlowState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun startDeviceAuthorization() {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = DeviceAuthorizationFlow(client)
            val context = flow.start("openid profile email offline_access").getOrElse { error ->
                _flowState.value = OAuth2FlowState.Error(error.message ?: "Unknown error")
                return@cancelAndLaunch
            }
            _flowState.value = OAuth2FlowState.DeviceAuthPolling(
                userCode = context.userCode,
                verificationUri = context.verificationUri,
                verificationUriComplete = context.verificationUriComplete,
                expiresIn = context.expiresIn
            )
            flow.resume(context).fold(
                onSuccess = { _flowState.value = OAuth2FlowState.Authenticated(it) },
                onFailure = { _flowState.value = OAuth2FlowState.Error(it.message ?: "Authorization timed out") }
            )
        }
    }

    fun startTokenExchange(idToken: String, deviceSecret: String) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = TokenExchangeFlow(client)
            flow.start(idToken, deviceSecret, scope = "openid profile email offline_access").fold(
                onSuccess = { _flowState.value = OAuth2FlowState.Authenticated(it) },
                onFailure = { _flowState.value = OAuth2FlowState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun startSessionToken(sessionToken: String) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = SessionTokenFlow(client)
            flow.start(sessionToken, BuildConfig.REDIRECT_URI, scope = "openid profile email offline_access").fold(
                onSuccess = { _flowState.value = OAuth2FlowState.Authenticated(it) },
                onFailure = { _flowState.value = OAuth2FlowState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun reset() {
        activeJob?.cancel()
        activeJob = null
        _flowState.value = OAuth2FlowState.Idle
    }

    private fun cancelAndLaunch(block: suspend () -> Unit) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch { block() }
    }
}
```

## Java Usage (CompletableFuture API)

The `oauth2` module provides Java-compatible wrappers using `CompletableFuture`. All JVM wrapper classes are in the `com.okta.oauth2.kmp.jvm` package and must be `close()`d when no longer needed.

### Creating an OAuth2Client (Java)

```java
import com.okta.authfoundation.client.OAuth2ClientBuilder;
import com.okta.authfoundation.client.kmp.OAuth2Client;
import java.util.List;

OAuth2Client client = OAuth2ClientBuilder.Companion
    .create(
        "https://your-org.okta.com",
        "your-client-id",
        List.of("openid", "profile", "email", "offline_access"),
        builder -> {
            builder.setAuthorizationServerId("default");
            return kotlin.Unit.INSTANCE;
        }
    ).getOrThrow();
```

### Resource Owner Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.ResourceOwnerFlow;

ResourceOwnerFlow flow = new ResourceOwnerFlow(client);
flow.start("user@example.com", "user-password", "openid profile email offline_access")
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Device Authorization Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.DeviceAuthorizationFlow;
import com.okta.oauth2.kmp.DeviceAuthorizationFlowContext;

DeviceAuthorizationFlow flow = new DeviceAuthorizationFlow(client);
flow.start("openid profile email offline_access")
    .thenCompose(context -> {
        System.out.println("Go to: " + context.getVerificationUri());
        System.out.println("Enter code: " + context.getUserCode());
        return flow.resume(context);
    })
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Authorization Code Flow (Java)

The Java wrapper combines the start and resume steps using a `BrowserRedirectHandler`:

```java
import com.okta.oauth2.kmp.jvm.AuthorizationCodeFlow;
import com.okta.oauth2.kmp.LocalhostBrowserRedirectHandler;

AuthorizationCodeFlow flow = new AuthorizationCodeFlow(client);
BrowserRedirectHandler handler = new LocalhostBrowserRedirectHandler(8080, "/callback");
flow.start("http://localhost:8080/callback", handler)
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Token Exchange Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.TokenExchangeFlow;

TokenExchangeFlow flow = new TokenExchangeFlow(client);
flow.start("existing-id-token", "existing-device-secret")
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Session Token Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.SessionTokenFlow;

SessionTokenFlow flow = new SessionTokenFlow(client);
flow.start("session-token-from-authn-api", "http://localhost:8080/callback")
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Redirect End Session Flow (Java)

The Java wrapper combines the start and resume steps using a `BrowserRedirectHandler`:

```java
import com.okta.oauth2.kmp.jvm.RedirectEndSessionFlow;
import com.okta.oauth2.kmp.LocalhostBrowserRedirectHandler;

RedirectEndSessionFlow flow = new RedirectEndSessionFlow(client);
BrowserRedirectHandler handler = new LocalhostBrowserRedirectHandler(8080, "/logout-callback");
flow.start("current-id-token", "http://localhost:8080/logout-callback", handler)
    .thenAccept(unit -> {
        System.out.println("Logout completed");
    });
flow.close();
```

## Sample Applications

### Kotlin Multiplatform (Compose)

The `okta-direct-auth-shared` module contains a shared Compose Multiplatform sample with platform runner apps:

- **Android**: `okta-direct-auth-android-app`
- **Desktop (JVM)**: `okta-direct-auth-desktop-app`

The app launches a **Home Menu** where you choose between Direct Authentication and OAuth2 flows:

- **Resource Owner Flow** -- Username + password via OAuth2 Resource Owner Password grant
- **Device Authorization Flow** -- Device code + verification URI with automatic polling
- **Browser Sign-In** -- Authorization Code + PKCE via system browser (Chrome Custom Tabs on Android, localhost redirect on Desktop)
- **Token Exchange Flow** -- Native SSO token exchange using an existing ID token and device secret
- **Session Token Flow** -- Exchange a pre-obtained session token for OAuth2 tokens via server-side redirect

See the [okta-direct-auth-shared README](../okta-direct-auth-shared/README.md) for full setup and configuration instructions.

## Additional Resources

- [API Documentation](https://okta.github.io/okta-mobile-kotlin/oauth2/index.html)
- [Resource Owner Password Grant](https://developer.okta.com/docs/guides/implement-grant-type/ropassword/main/)
- [Authorization Code with PKCE](https://developer.okta.com/docs/guides/implement-grant-type/authcodepkce/main/)
- [Device Authorization Grant](https://developer.okta.com/docs/guides/device-authorization-grant/main/)
- [Configure Native SSO (Token Exchange)](https://developer.okta.com/docs/guides/configure-native-sso/main/)
- [Authentication API (Session Tokens)](https://developer.okta.com/docs/reference/api/authn/)
