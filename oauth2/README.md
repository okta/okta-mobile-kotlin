# Okta OAuth2

Standard OAuth2 authentication flows for Kotlin Multiplatform (Android + JVM), including Resource Owner Password, Device Authorization, Authorization Code with PKCE, Token Exchange (Native SSO), Session Token, Redirect End Session, and Cross App Access.

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Getting Started](#getting-started)
  - [Creating an OAuth2Client](#creating-an-oauth2client)
  - [Pushed Authorization Requests (PAR)](#pushed-authorization-requests-par)
- [Authentication Flows](#authentication-flows)
  - [Resource Owner Flow](#resource-owner-flow)
  - [Device Authorization Flow](#device-authorization-flow)
  - [Authorization Code Flow (Browser Sign-In)](#authorization-code-flow-browser-sign-in)
  - [Token Exchange Flow](#token-exchange-flow)
  - [Session Token Flow](#session-token-flow)
  - [Redirect End Session Flow](#redirect-end-session-flow)
  - [Cross App Access](#cross-app-access)
- [Complete Example](#complete-example)
- [Java Usage (CompletableFuture API)](#java-usage-completablefuture-api)
- [Sample Applications](#sample-applications)
- [Additional Resources](#additional-resources)

## Overview

This module provides KMP flow classes for standard OAuth2 grant types. All flows live in the `com.okta.oauth2.kmp` package, return Kotlin `Result` types, and require an `OAuth2Client` from the `auth-foundation` module.

Each flow follows a consistent pattern:
- **Single-step flows** (`ResourceOwnerFlow`, `TokenExchangeFlow`, `SessionTokenFlow`) — call `start()` and get a `Result<TokenInfo>`.
- **Two-step flows** (`DeviceAuthorizationFlow`, `AuthorizationCodeFlow`, `RedirectEndSessionFlow`) — call `start()` to get a context object, then `resume()` to complete the flow.
- **Cross App Access** (`CrossAppAccessFlow`) — the exception to the pattern above: it spans two authorization servers, so it is obtained from `CrossAppAccessFlow.create(idpClient, target)` (which returns `Result<CrossAppAccessFlow>`) rather than constructed, and its second step is `redeem()` rather than `resume()`.

## Requirements

- Android API 26+ or JVM (Java 11+)
- Okta org with the relevant OAuth2 grant types enabled
- Client application configured with the appropriate grant types on your authorization server

## Installation

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:3.1.0"))
    implementation("com.okta.kotlin:auth-foundation")
    implementation("com.okta.kotlin:oauth2")
}
```

## Migrating from Android-only APIs to KMP APIs

The older Android-only OAuth2 APIs remain available for compatibility, but new code should use the KMP packages in `com.okta.oauth2.kmp.*` and the explicit KMP `OAuth2Client` from `auth-foundation`.

#### Flow classes

Android-only:

```kotlin
import com.okta.oauth2.ResourceOwnerFlow

val flow = ResourceOwnerFlow()
```

KMP:

```kotlin
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.oauth2.kmp.ResourceOwnerFlow

val client = OAuth2ClientBuilder.create(
    issuerUrl = "https://your-org.okta.com",
    clientId = "your-client-id",
    scope = listOf("openid", "profile")
).getOrThrow()

val flow = ResourceOwnerFlow(client)
```

The same rename applies to `DeviceAuthorizationFlow`, `SessionTokenFlow`, `TokenExchangeFlow`, `AuthorizationCodeFlow`, and `RedirectEndSessionFlow`. Prefer `com.okta.oauth2.kmp.*` imports, pass an explicit KMP `OAuth2Client`, and use `com.okta.oauth2.kmp.jvm.*` for the Java wrappers.

#### Browser redirect handling

- Android: use `web-authentication-ui` for browser-based redirect flows.
- JVM: use `LocalhostBrowserRedirectHandler`.

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

### Pushed Authorization Requests (PAR)

PAR is opt-in and disabled by default. Set `enablePushedAuthorizationRequests = true` and
`AuthorizationCodeFlow` uses PAR whenever the discovered authorization server metadata advertises a
`pushed_authorization_request_endpoint` — this applies to the org authorization server as well as
custom ones, and is not gated on `authorizationServerId`. Independently of this setting, a server
that advertises `require_pushed_authorization_requests` always uses PAR.

- If PAR is supported, `start()` pushes the authorization parameters to PAR and returns a browser URL
  containing `request_uri`.
- If PAR is optional and unavailable/fails, `start()` fails with `PushedAuthorizationRequestException`
  by default (fail-closed). Set `allowPushedAuthorizationRequestFallback = true` to instead fall back
  to the classic authorization URL — the underlying PAR failure isn't otherwise surfaced (no logging
  or event) when that fallback succeeds.
- If PAR is required by server metadata (`require_pushed_authorization_requests=true`) and cannot
  be completed, `start()` fails with `PushedAuthorizationRequiredException` regardless of
  `allowPushedAuthorizationRequestFallback`.

You can control this behavior in `OAuth2ClientBuilder`:

```kotlin
val client = OAuth2ClientBuilder.create(
    issuerUrl = "https://your-org.okta.com",
    clientId = "your-client-id",
    scope = listOf("openid", "profile")
) {
    authorizationServerId = "default"
    enablePushedAuthorizationRequests = true
    // Optional: fall back to the classic authorization URL if PAR fails and isn't required,
    // instead of failing start() with PushedAuthorizationRequestException.
    // allowPushedAuthorizationRequestFallback = true
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
    scope = listOf("openid", "profile", "email", "offline_access")
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
val context = flow.start(scope = listOf("openid", "profile", "email", "offline_access")).getOrThrow()

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
    scope = listOf("openid", "profile", "email", "offline_access")
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
    scope = listOf("openid", "profile", "email", "offline_access")
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
    scope = listOf("openid", "profile", "email", "offline_access")
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

### Cross App Access

> [Okta Cross App Access concepts](https://developer.okta.com/docs/concepts/xaa/) · [draft-ietf-oauth-identity-assertion-authz-grant](https://datatracker.ietf.org/doc/draft-ietf-oauth-identity-assertion-authz-grant/)

Cross App Access (XAA) lets a signed-in user's session in a *requesting app* call a *resource app*'s API in a different security domain — with no second consent prompt and no static API key. It uses the Identity Assertion JWT Authorization Grant (ID-JAG), an OAuth 2.0 authorization-chaining extension, in two steps:

1. `start()` presents the user's assertion (ID token, access token, or refresh token) to the **IdP authorization server** and receives a short-lived **ID-JAG** assertion.
2. `redeem()` presents that ID-JAG to the **resource authorization server** and receives a short-lived, scoped **resource access token**.

**Prerequisite:** an administrator must configure a trusted connection between the requesting app and the resource app at the identity provider, with the scopes that connection allows. Without it, the first step fails with the server's denial reported through a `CrossAppAccessException.IdpExchangeFailed`.

**When not to use it:** Cross App Access requires an active, signed-in human user — it is not a substitute for machine-to-machine or background-job authentication with no user session. It also requires a confidential client at both steps (a credential to authenticate with); `create()` enforces this for the target client, while a public primary client is instead rejected remotely at the first step. A public client that cannot hold a credential should use the [Authorization Code Flow](#authorization-code-flow-browser-sign-in) instead.

```kotlin
import com.okta.oauth2.kmp.CrossAppAccessFlow
import com.okta.oauth2.kmp.CrossAppAccessTarget
import com.okta.oauth2.kmp.SubjectAssertion

// idpClient is the OAuth2Client already used to sign the user in.
val target = CrossAppAccessTarget.forIssuer("https://resource.example.com") {
    scope = listOf("chat.read", "chat.history")
    clientSecret = "target-app-client-secret" // or clientAssertionProvider for private_key_jwt
}

val flow = CrossAppAccessFlow.create(idpClient, target).getOrThrow()

flow.exchange(SubjectAssertion.idToken(idToken)).fold(
    onSuccess = { resourceToken ->
        // Use resourceToken.accessToken as a Bearer token against the resource app's API.
    },
    onFailure = { error ->
        // A CrossAppAccessException.IdpExchangeFailed or .TargetRedemptionFailed
        // names which server rejected the exchange; error.cause carries the
        // server's error code and description.
    }
)
```

A target reached through a custom authorization server in your own org can instead be named by its authorization server identifier:

```kotlin
val target = CrossAppAccessTarget.forAuthorizationServerId("default") {
    scope = listOf("chat.read")
    clientSecret = "target-app-client-secret"
}
```

**Target client identity and extras.** By default the target client reuses the primary client's client ID; set `clientId` when the target app is registered separately (it is only ever used at the second step — the first step always sends the primary client's ID). `resource` sends an RFC 8707 resource indicator with the first step, and `endpointOverrides` bypasses discovery for the target authorization server. For any target-client setting this builder does not name directly — a clock, a cache, an executor — use `clientBuildAction`, which is applied to the target's `OAuth2ClientBuilder` last and therefore wins on conflict (so keep credentials in `clientSecret`/`clientAssertionProvider`, not in there).

**Starting from a stored credential.** If you already hold a `Credential`, skip pulling the raw subject assertion out by hand:

```kotlin
import com.okta.oauth2.kmp.crossAppAccessToken

val resourceToken = credential.crossAppAccessToken(idpClient, target).getOrThrow()
```

`idpClient` must be the client that manages this credential — its configured issuer and client ID must match the credential's token, or the call fails with `IllegalArgumentException` before any network request. Use `credential.crossAppAccessSubject(type)` if you want the `SubjectAssertion` alone (for example to reuse it across several `start()` calls). Neither call mutates, replaces, or invalidates the credential.

**Subject assertion forms.** `SubjectAssertion.idToken(...)` and `SubjectAssertion.refreshToken(...)` are defined by the governing specification and are the portable choices across authorization servers. `SubjectAssertion.accessToken(...)` is accepted by Okta as a deployment extension — via an administrator-configured delegation link — but is not part of the specification itself; verify it against your own org before depending on it. An ID token is the safest default.

**Scopes are effectively required.** The specification marks the exchange's `scope` parameter optional, but Okta rejects a request that omits it. `start()` therefore fails locally, before any network request, when neither the target nor the call itself supplies a scope — converting what would otherwise be a remote `invalid_scope` rejection into an immediate, actionable local error.

**Renewing tokens.** An ID-JAG is reusable, not single-use — it stands in for a refresh token at the resource authorization server:
- When the resource access token expires, call `redeem()` again with the same `IdJagAssertion` — no second trip to the IdP is needed.
- When the ID-JAG itself expires (`idJag.isExpired(idpClient.configuration.clock)`), call `start()` again with the original subject assertion.
- If the subject assertion has also expired, obtain a fresh one without an interactive sign-in: either present the org refresh token directly as the subject (`SubjectAssertion.refreshToken(...)`, where that form is available), or refresh to a new ID token and call `start()` with it. The ID-token route is the portable one; the refresh-token route is shorter where the deployment accepts it.
- Across a process restart, persist the assertion's fields and rebuild it with `IdJagAssertion.restore(value, audience, expiresIn, issuedAt, scope, issuedTokenType)`, then `redeem()` it. Pass the *original* `issuedAt` you recorded at issuance — a later value overstates the assertion's remaining lifetime. An ID-JAG is bearer-equivalent at the target: persist it with the same care as a refresh token.

**Requested vs. granted scope.** `start(scope = ...)` and `CrossAppAccessTarget.scope` both take a `List<String>` of discrete values — a per-call value takes precedence, with the target's configured value as the default. The scope the server actually *granted* is reported on `IdJagAssertion.scope` as a single, space-delimited `String` (matching `TokenInfo.scope` elsewhere in this SDK), and may legitimately be narrower than what was requested.

**Externally-signed client credentials.** If your target client authenticates with a credential this SDK cannot re-sign itself, configure a `clientAssertionProvider` instead of a `clientSecret`. It is invoked fresh for every request with the exact target endpoint as the audience, and your signer is responsible for producing `iss`, `sub`, `aud`, `exp`, and a fresh `jti` on each call.

**Observability.** Cross App Access has no bespoke listener interface — it surfaces through the client's existing `events` stream. Collect `idpClient.events` for the ID-JAG issuance and `flow.targetClient.events` for the resource-token issuance; both arrive as `TokenCreatedEvent`. Distinguish the intermediate ID-JAG issuance from an ordinary sign-in by checking `tokenInfo.issuedTokenType == "urn:ietf:params:oauth:token-type:id-jag"` — a consumer that does not check this field will not tell the two apart.

## Complete Example

Here's a complete ViewModel example managing the Resource Owner, Device Authorization, Token Exchange, and Session Token flows:

```kotlin
import com.okta.directauth.app.AppConfig

class OAuth2ViewModel : ViewModel() {

    private val client = OAuth2ClientBuilder
        .create(
            issuerUrl = AppConfig.ISSUER,
            clientId = AppConfig.CLIENT_ID,
            scope = listOf("openid", "profile", "email", "offline_access")
        ) {
            authorizationServerId = AppConfig.AUTHORIZATION_SERVER_ID
        }.getOrThrow()

    private val _flowState = MutableStateFlow<OAuth2FlowState>(OAuth2FlowState.Idle)
    val flowState = _flowState.asStateFlow()

    private var activeJob: Job? = null

    fun startResourceOwner(username: String, password: String) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = ResourceOwnerFlow(client)
            flow.start(username, password, listOf("openid", "profile", "email", "offline_access")).fold(
                onSuccess = { _flowState.value = OAuth2FlowState.Authenticated(it) },
                onFailure = { _flowState.value = OAuth2FlowState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun startDeviceAuthorization() {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = DeviceAuthorizationFlow(client)
            val context = flow.start(listOf("openid", "profile", "email", "offline_access")).getOrElse { error ->
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
            flow.start(idToken, deviceSecret, scope = listOf("openid", "profile", "email", "offline_access")).fold(
                onSuccess = { _flowState.value = OAuth2FlowState.Authenticated(it) },
                onFailure = { _flowState.value = OAuth2FlowState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun startSessionToken(sessionToken: String) {
        cancelAndLaunch {
            _flowState.value = OAuth2FlowState.Loading
            val flow = SessionTokenFlow(client)
            flow.start(sessionToken, AppConfig.SIGN_IN_REDIRECT_URI, scope = listOf("openid", "profile", "email", "offline_access")).fold(
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
import com.okta.authfoundation.client.jvm.OAuth2ClientBuilder;
import com.okta.authfoundation.client.kmp.OAuth2Client;

OAuth2Client client =
    new OAuth2ClientBuilder(
        "https://your-org.okta.com",
        "your-client-id",
        java.util.List.of("openid", "profile", "email", "offline_access"))
        .setAuthorizationServerId("default")
        .build()
        .getOrThrow();
```

### Resource Owner Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.ResourceOwnerFlow;

ResourceOwnerFlow flow = new ResourceOwnerFlow(client);
flow.start("user@example.com", "user-password", java.util.List.of("openid", "profile", "email", "offline_access"))
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
flow.start(java.util.List.of("openid", "profile", "email", "offline_access"))
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
flow.start("http://localhost:8080/callback", handler,
        java.util.List.of("openid", "profile", "email", "offline_access"),
        java.util.Collections.emptyMap())
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Token Exchange Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.TokenExchangeFlow;

TokenExchangeFlow flow = new TokenExchangeFlow(client);
flow.start("existing-id-token", "existing-device-secret",
        java.util.List.of("openid", "profile", "email", "offline_access"),
        null)
    .thenAccept(tokenInfo -> {
        String accessToken = tokenInfo.getAccessToken();
    });
flow.close();
```

### Session Token Flow (Java)

```java
import com.okta.oauth2.kmp.jvm.SessionTokenFlow;

SessionTokenFlow flow = new SessionTokenFlow(client);
flow.start("session-token-from-authn-api", "http://localhost:8080/callback",
        java.util.List.of("openid", "profile", "email", "offline_access"),
        java.util.Collections.emptyMap())
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

### Cross App Access (Java)

```java
import com.okta.oauth2.kmp.CrossAppAccessTarget;
import com.okta.oauth2.kmp.SubjectAssertion;
import com.okta.oauth2.kmp.jvm.CrossAppAccessFlow;
import com.okta.oauth2.kmp.jvm.CrossAppAccessTargetBuilder;
import com.okta.authfoundation.client.jvm.AuthFoundationResult;
import com.okta.authfoundation.client.TokenInfo;

CrossAppAccessTarget target = CrossAppAccessTargetBuilder.forIssuer("https://resource.example.com")
    .setScope(java.util.List.of("chat.read", "chat.history"))
    .setClientSecret("target-app-client-secret")
    .build();

// client is the OAuth2Client already used to sign the user in.
AuthFoundationResult<CrossAppAccessFlow> result = CrossAppAccessFlow.create(client, target);
CrossAppAccessFlow flow = result.getOrThrow();

TokenInfo resourceToken = flow.exchange(SubjectAssertion.idToken(idToken)).join();
String accessToken = resourceToken.getAccessToken();
flow.close();
```

For deep target-client customization — a shared executor, a custom cache — either use `setClientBuildAction`, or build the target client directly with `OAuth2ClientBuilder` and wrap it. The wrapping route is usually more comfortable from Java, since `setClientBuildAction` hands you the Kotlin `OAuth2ClientBuilder` rather than this module's Java wrapper:

```java
import com.okta.authfoundation.client.jvm.OAuth2ClientBuilder;
import com.okta.authfoundation.client.kmp.OAuth2Client;

OAuth2Client targetClient = new OAuth2ClientBuilder(
        "https://resource.example.com", "target-client-id", java.util.List.of("chat.read"))
    .setClientSecret("target-app-client-secret")
    .build()
    .getOrThrow();

CrossAppAccessTarget target = CrossAppAccessTarget.wrapping(targetClient, null, java.util.List.of("chat.read"));
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

### Java CLI

The `okta-direct-auth-java-cli-sample` module is a pure Java CLI sample that demonstrates the Java-friendly `oauth2` wrappers alongside direct authentication.

See the [Java CLI sample README](../okta-direct-auth-java-cli-sample/README.md) for setup and usage details.

## Additional Resources

- [API Documentation](https://okta.github.io/okta-mobile-kotlin/oauth2/index.html)
- [Resource Owner Password Grant](https://developer.okta.com/docs/guides/implement-grant-type/ropassword/main/)
- [Authorization Code with PKCE](https://developer.okta.com/docs/guides/implement-grant-type/authcodepkce/main/)
- [Device Authorization Grant](https://developer.okta.com/docs/guides/device-authorization-grant/main/)
- [Configure Native SSO (Token Exchange)](https://developer.okta.com/docs/guides/configure-native-sso/main/)
- [Authentication API (Session Tokens)](https://developer.okta.com/docs/reference/api/authn/)
- [Cross App Access concepts](https://developer.okta.com/docs/concepts/xaa/)
- [draft-ietf-oauth-identity-assertion-authz-grant (ID-JAG specification)](https://datatracker.ietf.org/doc/draft-ietf-oauth-identity-assertion-authz-grant/)
