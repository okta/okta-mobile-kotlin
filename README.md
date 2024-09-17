[![Support](https://img.shields.io/badge/support-Developer%20Forum-blue.svg)][devforum]
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta Mobile Kotlin
> _”Make the easy things simple and make the hard things possible.”_

The Okta Mobile SDKs are a suite of libraries that intends to replace our legacy mobile SDKs, with the aim to streamline development, ease maintenance and feature development, and enable new use cases that were previously difficult or impractical to implement. We are building a platform to support the development of many SDKs, allowing application developers to choose which SDKs they need.

The Okta Mobile Kotlin SDK is intended to be used on the Android platform.

## SDK Overview

This SDK consists of several different libraries, each with detailed documentation.

- [AuthFoundation](auth-foundation) -- Common classes for managing credentials and used as a foundation for other libraries.
- [OktaOAuth2](oauth2) -- OAuth2 authentication capabilities for authenticating users.
- [WebAuthenticationUI](web-authentication-ui) -- Authenticate users using web-based OIDC flows.

The use of this SDK enables you to build or support a myriad of different authentication flows and approaches.

## Support Policy

### Legacy okta-oidc-android support

We intend to support okta-oidc-android with critical bug and security fixes for the foreseeable future. Once the Kotlin Mobile SDK is generally available, all new features will be built on top of okta-mobile-kotlin and will replace okta-oidc-android.

## Unlocking use cases
Okta is busy adding new functionality to its identity platform. We're excited to unlock these new capabilities for Android. These SDKs are built on top of [Kotlin](https://kotlinlang.org/), [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html), and [OkHttp](https://github.com/square/okhttp). We are doubling down on our developer experience, providing seamless ways to log in, store, and access OAuth tokens. We are building an initial set of functionality unlocking new OAuth flows that were not possible before, including:
* [Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
* [Device Authorization Grant](https://datatracker.ietf.org/doc/html/rfc8628)
* [Okta Identity Engine](https://github.com/okta/okta-idx-android)

# Installation

Add the `Okta Mobile Kotlin` dependencies to your `build.gradle` file:

```gradle
// Ensure all dependencies are compatible using the Bill of Materials (BOM).
implementation(platform('com.okta.kotlin:bom:2.0.2'))

// Add the dependencies to your project.
implementation('com.okta.kotlin:auth-foundation')
implementation('com.okta.kotlin:oauth2')
implementation('com.okta.kotlin:web-authentication-ui')
```

See the [CHANGELOG](CHANGELOG.md) for the most recent changes.

If you're migrating from [okta-oidc-android](https://github.com/okta/okta-oidc-android) see [migrate.md](migrate.md) for more information.

# Okta Mobile SDK for Kotlin

## Release status

This library uses semantic versioning and follows Okta's [Library Version Policy][okta-library-versioning].

The latest release can always be found on the [releases page][github-releases].

## Need help?

If you run into problems using the SDK, you can:

* Ask questions on the [Okta Developer Forums][devforum]
* Post [issues][github-issues] here on GitHub (for code errors)

## Getting Started

To get started, you will need:

* An Okta account, called an _organization_ (sign up for a free [developer organization](https://developer.okta.com/signup) if you need one).
* An Okta Application, configured as a Native App. This is done from the Okta Developer Console. When following the wizard, use the default properties. They are designed to work with our sample applications.
* Android Studio

## Usage Guide

This SDK consists of several different libraries, each with their own detailed documentation.

SDKs are split between two primary use cases:
- Minting tokens (authentication)
  - Okta supports many OAuth flows, our Android SDKs support the following: Authorization Code, Interaction Code, Refresh Token, Resource Owner Password, Device Authorization, and Token Exchange.
- Managing the token lifecycle (refresh, storage, validation, etc)

### Auto backup rules

This SDK uses on-device encryption keys to store data. Because of this, the SDK files should not be backed up. This SDK provides backup rules to exclude files automatically.
But, if your application provides its own backup rules by specifying `android:dataExtractionRules` or `android:fullBackupContent`, please include SDK backup rules as specified in [data_extraction_rules](auth-foundation/src/main/res/xml/data_extraction_rules.xml) and [full_backup_content](auth-foundation/src/main/res/xml/full_backup_content.xml).

### Kotlin Coroutines
[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) are used extensively throughout the SDKs. All methods can be used via any thread (including the main thread), and will switch to a background thread internally when performing network IO or expensive computation.

### Web Authentication using OIDC redirect

The simplest way to integrate authentication in your app is with OIDC through a web browser, using the Authorization Code Flow grant.

#### Configure your OAuth Settings

Before authenticating your user, you need to set your default `OidcConfiguration` using the settings defined in your application in the Okta Developer Console.

```kotlin
import android.content.Context
import com.okta.authfoundation.AuthFoundation
import com.okta.authfoundation.client.OidcConfiguration

val context: Context = TODO("Supplied by the developer.")
AuthFoundation.initializeAndroidContext(context)
OidcConfiguration.default = OidcConfiguration(
  clientId = "{clientId}",
  defaultScope = "openid email profile offline_access",
  issuer = "https://{yourOktaOrg}.okta.com/oauth2/default"
)
```

#### Create a Web Authentication Client

We will create a `WebAuthentication` and use it to perform authentication.

This launches a [Chrome Custom Tab](https://developer.chrome.com/docs/android/custom-tabs/) to display the login form, and once complete, redirects back to the application.

```kotlin
import android.content.Context
import com.okta.authfoundation.credential.Credential
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.webauthenticationui.WebAuthentication

val context: Context = TODO("Supplied by the developer.")
val credential: Credential
val redirectUrl: String = TODO("signInRedirectUri supplied by the developer.")
val auth = WebAuthentication()
when (val result = auth.login(context, redirectUrl)) {
    is OAuth2ClientResult.Error -> {
        // Timber.e(result.exception, "Failed to login.")
        // TODO: Display an error to the user.
    }
    is OAuth2ClientResult.Success -> {
      credential = Credential.store(token = result.result)
      Credential.setDefaultCredential(credential)
      // The credential instance is now initialized! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
    }
}
```

Next we need to be sure our application handles the redirect. Add the following snippet to your `build.gradle`:

Note: you will need to replace the `{redirectUriScheme}` with your applications redirect scheme. For example, a `signInRedirectUri` of `com.okta.sample.android:/login` would mean replacing `{redirectUriScheme}` with `com.okta.sample.android`.

```groovy
android {
  defaultConfig {
    manifestPlaceholders = [
      "webAuthenticationRedirectScheme": "{redirectUriScheme}"
    ]
  }
}
```

### Device Authorization Flow

[DeviceAuthorizationFlow](oauth2/src/main/java/com/okta/oauth2/DeviceAuthorizationFlow.kt) can be used to perform [OAuth 2.0 Device Authorization Grant](https://datatracker.ietf.org/doc/html/rfc8628).

The Device Authorization Flow is designed for Internet connected devices that either lack a browser to perform a user-agent based authorization or are input constrained to the extent that requiring the user to input text in order to authenticate during the authorization flow is impractical.

```kotlin
import com.okta.authfoundation.credential.Credential
import com.okta.oauth2.DeviceAuthorizationFlow

val credential: Credential
val deviceAuthorizationFlow = DeviceAuthorizationFlow()

when (val result = deviceAuthorizationFlow.start()) {
  is OAuth2ClientResult.Error -> {
    // Timber.e(result.exception, "Failed to login.")
    // TODO: Display an error to the user.
  }
  is OAuth2ClientResult.Success -> {
    val flowContext: DeviceAuthorizationFlow.Context = result.result
    // TODO: Show the user the code and uri to complete the login via `flowContext.userCode` and `flowContext.verificationUri`.

    // Poll the Authorization Server. When the user completes their login, this will complete.
    when (val resumeResult = deviceAuthorizationFlow.resume(flowContext)) {
      is OAuth2ClientResult.Error -> {
        // Timber.e(resumeResult.exception, "Failed to login.")
        // TODO: Display an error to the user.
      }
      is OAuth2ClientResult.Success -> {
        credential = Credential.store(token = result.result)
        Credential.setDefaultCredential(credential)
        // The credential instance is now initialized! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
      }
    }
  }
}
```

### Token Exchange Flow

[TokenExchangeFlow](oauth2/src/main/java/com/okta/oauth2/TokenExchangeFlow.kt) can be used to perform [OIDC Native SSO](https://openid.net/specs/openid-connect-native-sso-1_0.html).

The Token Exchange Flow exchanges an ID Token and a Device Secret for a new set of tokens.

```kotlin
import com.okta.authfoundation.client.OAuth2Client
import com.okta.authfoundation.credential.Credential
import com.okta.oauth2.TokenExchangeFlow

val tokenExchangeFlow = TokenExchangeFlow()
when (val result = tokenExchangeFlow.start(idToken, deviceSecret)) {
  is OAuth2ClientResult.Error -> {
      // Timber.e(result.exception, "Failed to login.")
      // TODO: Display an error to the user.
  }
  is OAuth2ClientResult.Success -> {
    val tokenExchangeCredential = Credential.store(result.result)
    // The credential instance is now initialized! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
  }
}
```

> Note: You'll want to ensure you have 2 *DIFFERENT* `Credential`s. The first needs to have the `idToken`, and `deviceSecret` minted via a `WebAuthenticationClient`. The second will be used in the `TokenExchangeFlow`.

### Logout

There are multiple terms that might be confused when logging a user out.

- `Credential.delete` - Clears the in memory reference to the `Token` and removes the information from `TokenStorage`, the `Credential` can no longer be used.
- `Credential.revokeAllTokens` - Revokes all available tokens from the Authorization Server.
- `Credential.revokeToken`/`OAuth2Client.revokeToken` - Revokes the specified `RevokeTokenType` from the Authorization Server.
- `WebAuthenticationClient.logoutOfBrowser` - Removes the Okta session if the user was logged in via the OIDC Browser redirect flow. Also revokes the associated `Token`(s) minted via this flow.

> Notes:
> - `Credential.delete` does not revoke a token
> - `Credential.revokeToken`/`Credential.revokeAllTokens`/`OAuth2Client.revokeToken` does not remove the `Token` from memory, or `TokenStorage`. It also does not invalidate the browser session if the `Token` was minted via the OIDC Browser redirect flow.
> - `WebAuthenticationClient.logoutOfBrowser` revokes the `Token`, but does not remove it from memory or `TokenStorage`
> - Revoking a `RevokeTokenType.ACCESS_TOKEN` does not revoke the associated `Token.refreshToken` or `Token.deviceSecret`
> - Revoking a `RevokeTokenType.DEVICE_SECRET` does not revoke the associated `Token.accessToken` or `Token.refreshToken`
> - Revoking a `RevokeTokenType.REFRESH_TOKEN` *DOES* revoke the associated `Token.accessToken` AND `Token.refreshToken`

### Using a Credential to determine user authentication status
There are a few options to determine the status of a user authentication. Each option has unique pros and cons and should be chosen based on the needs of your use case.

- Non null default credential: `Credential.default != null`
- Non empty credential allIds: `Credential.allIds.isNotEmpty()`
- getValidAccessToken: `Credential.default?.getValidAccessToken() != null`
- Custom implementation: `Credential.default?.token`, `Credential.default?.refresh()`, and `Credential.default?.getAccessTokenIfValid()`

Details on each approach are below.

#### Determine authentication status via non null Credential
`Credential`s require a `Token`. If there are no `Credential`s present, then no `Token` has been stored. Note that `Credential.default` can throw a `BiometricInvocationException` if the `Credential` was stored using `Credential.Security.Biometric<Strong/StrongOrDeviceCredential>`.

#### Determine authentication by checking if any Credentials exist
`Credential.allIds` lists list of all ids of stored `Credential`s. If it returns an empty list, there are no stored `Credential`s.

#### Determine authentication status via getValidAccessToken
`Credential` has a method called `getValidAccessToken` which checks to see if the credential has a token, and has a valid access token. If the access token is expired, and a refresh token exists, a `refresh` is implicitly called on the `Credential`. If the implicit `refresh` is successful, `getValidAccessToken` returns the new access token. There are two main down sides to this approach. First, it's a `suspend fun` and could make network calls. Second, the failure is not returned, an error could occur due to a network error, a missing token, a missing refresh token, or a configuration error.

#### Determine authentication status via custom implementation
If your use case requires insight into errors and the current state of the `Credential`, you can use implement it to your needs with the primitives `Credential` provides. See the documentation for the associated properties and methods: `Credential.token`, `Credential.refresh()`, `Credential.getAccessTokenIfValid()`.

### Biometric Credentials
The SDK has built-in support for handling Biometric encryption. To set the default token encryption as Biometric, `Credential.Security.standard` can be set to `Credential.Security.BiometricStrong` or `Credential.Security.BiometricStrongOrDeviceCredential`. Biometric encryption also requires setting `Credential.Security.promptInfo`.

> Notes:
> - The SDK does not check which biometrics are enrolled on the user's device. Please check this using https://developer.android.com/reference/android/hardware/biometrics/BiometricManager#canAuthenticate(int) before setting the appropriate security level
> - The SDK automatically deletes Token entries stored using invalidated biometric keys.
> - Biometric Credentials should only be fetched using async APIs in `Credential` class, otherwise `BiometricInvocationException` will be thrown.

#### Setting Biometric security for new Credentials globally

```kotlin
Credential.Security.standard = Credential.Security.BiometricStrong()
Credential.Security.promptInfo = BiometricPrompt.PromptInfo.Builder()
  .setTitle("Title")
  .setNegativeButtonText("Cancel Button")
  .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG) // Verify the authenticator is supported by device using BiometricManager.canAuthenticate
  .build()
```

#### Setting Biometric security for a single Credential

```kotlin
val token = TODO("Supplied by user")
val credential = Credential.store(token, security = Credential.Security.BiometricStrong())
```

#### Auth-per-use Biometric keys
The SDK uses Biometric keys with a timeout of 5 seconds by default. This allows apps to invoke Biometrics once, and perform operations on multiple Biometric `Credential`s. Auth-per-use Biometric `Credential`s are also supported using the following:

```kotlin
// Globally
Credential.Security.standard = Credential.Security.BiometricStrong(userAuthenticationTimeout = 0)
// or per-Credential
val token = TODO("Supplied by user")
val credential = Credential.store(token, security = Credential.Security.BiometricStrong(userAuthenticationTimeout = 0))
```

#### Biometric exceptions
Android `BiometricPrompt` can fail due to `AuthenticationCallback.onAuthenticationFailed` and `AuthenticationCallback.onAuthenticationError`. See this relevant Android developer doc: [BiometricPrompt.AuthenticationCallback](https://developer.android.com/reference/kotlin/androidx/biometric/BiometricPrompt.AuthenticationCallback)

`AuthenticationCallback.onAuthenticationError` can return error codes to recover from different Biometric situations, as listed here: https://developer.android.com/reference/kotlin/androidx/biometric/BiometricPrompt#ERROR_CANCELED()

When using Biometric security, `Credential` fetching functions can throw `BiometricAuthenticationException`, and the relevant errors can be queried as follows:

```kotlin
val credential = try {
    Credential.getDefaultAsync()
} catch (ex: BiometricAuthenticationException) {
    when (val details = ex.biometricExceptionDetails) {
      is BiometricExceptionDetails.OnAuthenticationFailed -> {
        TODO("onAuthenticationFailed has no error codes or messages")
      }
      is BiometricExceptionDetails.OnAuthenticationError -> {
        val errorMessage = details.errString
        // Error code from https://developer.android.com/reference/kotlin/androidx/biometric/BiometricPrompt#constants_1
        val errorCode = details.errorCode
      }
    }
}
```

### Networking customization

The Okta Mobile Kotlin SDKs should provide all the required networking by default, however, if you would like to customize networking
behavior, that is also possible.

The SDK uses [OkHttp](https://github.com/square/okhttp) as the API for performing network requests.
The SDK also uses OkHttp as the default implementation for performing network requests.
If you intent to customize networking behavior, there are a few options:
- Add an [Interceptor](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-interceptor/) to the `OkHttpClient` you provide to
`AuthFoundationDefaults.okHttpClientFactory`
- Return a custom implementation of `Call.Factory` when initializing the SDK in `AuthFoundationDefaults.okHttpClientFactory`

#### OkHttp Interceptor

Configuring the `OkHttpClient` with an `Interceptor` is the recommend approach to customizing the networking behavior.
Adding an interceptor allows you to listen for requests and responses, customize requests before they are sent, and customize responses
before they are processed by the SDK.

#### Custom Call Factory

Providing a custom call factory is an advanced use case, and is not recommended. The possibilities are endless, including the ability to
replace the engine that executes the HTTP requests.

### Rate Limit Handling

The Okta API will return 429 responses if too many requests are made within a given time. Please see [Rate Limiting at Okta] for a complete
list of which endpoints are rate limited. This SDK automatically retries requests on 429 errors. The default configuration is as follows:

| Configuration Option | Description |
| ---------------------- | -------------- |
| maxRetries         | The number of times to retry. The default value is `3`. |
| minDelaySeconds    | The minimum amount of time to wait between each retry. The default value is `1` second. |

#### Configuring retry parameters

To configure retry parameters, an `EventHandler` must be registered before creating an `OidcConfiguration`. In the `EventHandler`,
`RateLimitExceededEvent` events will be emitted any time a request receives a response with 429 status code. `minDelaySeconds` and
`maxRetries` can be adjusted based on details provided by the event.

```kotlin
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.events.RateLimitExceededEvent
import com.okta.authfoundation.events.EventCoordinator
import com.okta.authfoundation.events.EventHandler

AuthFoundationDefaults.eventCoordinator = EventCoordinator(
  object : EventHandler {
    override fun onEvent(event: Any) {
      when (event) {
        is RateLimitExceededEvent -> {
          // Event info
          val retriedRequest = event.request // Request that triggered the event
          val responseWith429Code = event.response// 429 response to the request. Note: Only access the response body using response.peekBody
          val retryCount = event.retryCount // Number of retries for this request so far

          // User configurable flags
          event.minDelaySeconds = 1L // User configurable delay, in seconds, for retrying the request again
          event.maxRetries = 3 // User configurable max retries for this request
        }
      }
    }
  }
)

val oidcConfiguration = OidcConfiguration(
  clientId = "{clientId}",
  defaultScope = "openid email profile offline_access",
)
```

## Migrating from okta-mobile-kotlin 1.x to 2.x

### Token migration

The process for Token migration varies based on use of custom `TokenStorage` or encryption spec when creating `CredentialDataSource` in 1.x. Token migration is handled automatically in the simplest case without user intervention:

```kotlin
client.createCredentialDataSource(context)
```

#### Migration with custom KeyGenParameterSpec

1.x:
```kotlin
val keyGenParameterSpec: KeyGenParameterSpec = TODO("Supplied by user")
client.createCredentialDataSource(context, keyGenParameterSpec)
```
2.x:
```kotlin
val keyGenParameterSpec: KeyGenParameterSpec = TODO("Supplied by user")
V1ToV2StorageMigrator.legacyKeyGenParameterSpec = keyGenParameterSpec
```

#### Migration with custom TokenStorage

1.x:
```kotlin
val customTokenStorage: TokenStorage = TODO("Supplied by user")
client.createCredentialDataSource(customTokenStorage)
```

2.x:
```kotlin
// Convert custom TokenStorage implementation to LegacyTokenStorage
val legacyTokenStorage: LegacyTokenStorage = TODO("Supplied by user")
V1ToV2StorageMigrator.legacyStorage = legacyTokenStorage
```

### API migration

#### Credential changes

`CredentialBootstrap`, `CredentialDataSource`, and `Credential` contain several changes over 1.x. `Credential`s can no longer contain a null `Token`. Because of this change from 1.x, the flow for creating `Credential` without `Token`, followed by calling `Credential.storeToken` can no longer be used.
When creating a new `Credential` with `Credential.store`, a `Token` must be provided.

1.x would create a new `Credential` with null `Token` if no default `Credential` existed when calling `CredentialBootstrap.defaultCredential()`. In 2.x, default `Credential` can be fetched using `Credential.default` or `Credential.getDefaultAsync()`. Both of those have a type of `Credential?` instead of `Credential`, and return `null` if no default `Credential` exists.

1.x contained `Credential` handling APIs in `CredentialBootstrap` and `CredentialDataSource`. All `Credential` management calls have been moved to `Credential` in 2.x. `CredentialBootstrap` has been deleted, and `CredentialDataSource` is private in 2.x.

1.x provided `suspend` functions for handling creation and management of any `Credential`s. 2.x provides synchronous `Credential` management functions in addition to `suspend` functions.

#### Initialization changes

The SDK initialization calls in 1.x were as follows:

```kotlin
val context: Context = TODO("Supplied by the developer.")
val oidcConfiguration = OidcConfiguration(
    clientId = "{clientId}",
    defaultScope = "openid email profile offline_access",
)
val client = OidcClient.createFromDiscoveryUrl(
    oidcConfiguration,
    "https://{yourOktaOrg}.okta.com/oauth2/default/.well-known/openid-configuration".toHttpUrl(),
)
CredentialBootstrap.initialize(client.createCredentialDataSource(context))
```

In 2.x, this is changed to:
```kotlin
val context: Context = TODO("Supplied by the developer.")
AuthFoundation.initializeAndroidContext(context)
OidcConfiguration.default = OidcConfiguration(
  clientId = "{clientId}",
  defaultScope = "openid email profile offline_access",
  issuer = "https://{yourOktaOrg}.okta.com/oauth2/default" // Note that 1.x required .well-known/openid-configuration link. 2.x automatically handles this
)
```

#### OAuth flows

In 1.x, OAuth flows were created as follows:

```kotlin
val oauthFlow = CredentialBootstrap.oidcClient.createWebAuthenticationClient() // or createTokenExchangeFlow, createDeviceAuthorizationFlow etc
```

In 2.x, this has been changed to:

```kotlin
val oauthFlow = WebAuthentication() // or TokenExchangeFlow, SessionTokenFlow, DeviceAuthorizationFlow, or AuthorizationCodeFlow
```

By default, all OAuth flows use `OAuth2Client` associated with `OidcConfiguration.default`. Custom `OAuth2Client` or `OidcConfiguration` can be passed into OAuth flows as follows:

```kotlin
// Custom OidcConfiguration
val oidcConfiguration: OidcConfiguration = TODO("Supplied by user")
val oauthFlow = WebAuthentication(oidcConfiguration)

// Custom OAuth2Client
val client: OAuth2Client = TODO("Supplied by user")
val oauthFlow = WebAuthentication(client)
```

#### WebAuthenticationUi

`WebAuthenticationClient` has been renamed to `WebAuthentication`.

## Troubleshooting

- java.lang.NoClassDefFoundError: Failed resolution of: Ljava/time/Instant;
  - Fix: configure [Core Library Desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)

### FlowCancelledException

FlowCancelledException is supposed to be thrown in cases where the user has decided to cancel the login flow, usually by quitting the browser login window. It can sometimes be incorrectly thrown in the following cases:
- Using the Android system webview while logging out. The webview doesn't store the session after a successful login, so logging out never redirects, and the user is forced to cancel logout process
- Deleting the browser cache after logging in, then attempting to log out. Similar to the above, it is important for browser to store the login state to logout successfully, otherwise the browser can not provide the logout redirect.
- Browser providing empty redirect results, followed by well-defined results. This has been observed in some older devices and browsers. This problem can be worked around by setting AuthFoundationDefaults.loginCancellationDebounceTime

## Running the sample

The sample is designed to show what is possible when using the SDK.

### Configuring the sample

Update the `okta.properties` file in the root directory of the project with the contents created from the Okta admin dashboard:
```
issuer=https://YOUR_ORG.okta.com/oauth2/default
clientId=test-client-id
signInRedirectUri=com.okta.sample.android:/login
signOutRedirectUri=com.okta.sample.android:/logout
legacySignInRedirectUri=com.okta.sample.android.legacy:/login
legacySignOutRedirectUri=com.okta.sample.android.legacy:/logout
```

> Notes:
> - issuer - is your authorization server, usually https://your_okta_domain.okta.com/oauth2/default, but custom authorization servers are supported. See https://your_okta_domain.okta.com/admin/oauth2/as for available authorization servers.
> - clientId - is your applications client id, created in your Okta admin dashboard
> - signInRedirectUri - is used for browser redirect, and should follow the format of reverse domain name notation + /login, ie: com.okta.sample.android:/login
> - signOutRedirectUri - is used for browser redirect, and should follow the format of reverse domain name notation + /logout, ie: com.okta.sample.android:/logout

### Launching the sample

You can open this sample in Android Studio or build it using Gradle.

```
./gradlew :app:assembleDebug
```

## Contributing

We are happy to accept contributions and PRs! Please see the [contribution guide](CONTRIBUTING.md) to understand how to structure a contribution.

[devforum]: https://devforum.okta.com/
[github-issues]: https://github.com/okta/okta-mobile-kotlin/issues
[github-releases]: https://github.com/okta/okta-mobile-kotlin/releases
[Rate Limiting at Okta]: https://developer.okta.com/docs/api/getting_started/rate-limits
[okta-library-versioning]: https://developer.okta.com/code/library-versions
[Rate Limiting at Okta]: https://developer.okta.com/docs/api/getting_started/rate-limits
