[![Support](https://img.shields.io/badge/support-Developer%20Forum-blue.svg)][devforum]
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Test Fork. DO NOT MERGE!

# New SDK suite
> _”Make the easy things simple and make the hard things possible.”_

The Okta Mobile SDK represents a suite of libraries that intends to replace our legacy mobile SDKs, with the aim to streamline development, ease maintenance and feature development, and enable new use cases that were previously difficult or impractical to implement. We are building a platform to support the development of many SDKs, allowing application developers to choose which SDKs they need.

The Okta Mobile Kotlin SDK is intended to be used on the Android platform.

## SDK Overview

This SDK consists of several different libraries, each with detailed documentation.

- [AuthFoundation](auth-foundation) -- Common classes for managing credentials and used as a foundation for other libraries.
- [AuthFoundationBootstrap](auth-foundation-bootstrap) -- A simplified API for common use cases.
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
implementation(platform('com.okta.kotlin:bom:1.0.0'))

// Add the dependencies to your project.
implementation('com.okta.kotlin:auth-foundation')
implementation('com.okta.kotlin:auth-foundation-bootstrap')
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

### Kotlin Coroutines
[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) are used extensively throughout the SDKs. All methods can be used via any thread (including the main thread), and will switch to a background thread internally when performing network IO or expensive computation.

### Web Authentication using OIDC redirect

The simplest way to integrate authentication in your app is with OIDC through a web browser, using the Authorization Code Flow grant.

#### Configure your OIDC Settings

Before authenticating your user, you need to create your `OidcClient`, using the settings defined in your application in the Okta Developer Console, and initialize the bootstrap module.

```kotlin
import android.content.Context
import com.okta.authfoundation.AuthFoundationDefaults
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import com.okta.authfoundation.client.SharedPreferencesCache
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource
import com.okta.authfoundationbootstrap.CredentialBootstrap
import okhttp3.HttpUrl.Companion.toHttpUrl

val context: Context = TODO("Supplied by the developer.")
AuthFoundationDefaults.cache = SharedPreferencesCache.create(context)
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

#### Create a Credential

The Credential type handles storage, OAuth conveniences and authorizing requests to your Resource Server after login occurs. Before authenticating, we'll create the credential.

```kotlin
import com.okta.authfoundationbootstrap.CredentialBootstrap

val credential: Credential = CredentialBootstrap.defaultCredential()
```

#### Create a Web Authentication Client

Once you've created your `Credential`, we'll use it to create our `WebAuthenticationClient` and authenticate.

This launches a [Chrome Custom Tab](https://developer.chrome.com/docs/android/custom-tabs/) to display the login form, and once complete, redirects back to the application.

```kotlin
import android.content.Context
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.webauthenticationui.WebAuthenticationClient
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.createWebAuthenticationClient

val context: Context = TODO("Supplied by the developer.")
val credential: Credential = TODO("Available from previous steps.")
val redirectUrl: String = TODO("signInRedirectUri supplied by the developer.")
val webAuthenticationClient = CredentialBootstrap.oidcClient.createWebAuthenticationClient()
when (val result = webAuthenticationClient.login(context, redirectUrl)) {
    is OidcClientResult.Error -> {
        // Timber.e(result.exception, "Failed to login.")
        // TODO: Display an error to the user.
    }
    is OidcClientResult.Success -> {
      credential.storeToken(token = result.result)
      // The credential instance now has a token! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
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
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.oauth2.DeviceAuthorizationFlow
import com.okta.oauth2.DeviceAuthorizationFlow.Companion.createDeviceAuthorizationFlow

val credential: Credential = CredentialBootstrap.defaultCredential()
val deviceAuthorizationFlow: DeviceAuthorizationFlow = CredentialBootstrap.oidcClient.createDeviceAuthorizationFlow()

when (val result = deviceAuthorizationFlow.start()) {
  is OidcClientResult.Error -> {
    // Timber.e(result.exception, "Failed to login.")
    // TODO: Display an error to the user.
  }
  is OidcClientResult.Success -> {
    val flowContext: DeviceAuthorizationFlow.Context = result.result
    // TODO: Show the user the code and uri to complete the login via `flowContext.userCode` and `flowContext.verificationUri`.

    // Poll the Authorization Server. When the user completes their login, this will complete.
    when (val resumeResult = deviceAuthorizationFlow.resume(flowContext)) {
      is OidcClientResult.Error -> {
        // Timber.e(resumeResult.exception, "Failed to login.")
        // TODO: Display an error to the user.
      }
      is OidcClientResult.Success -> {
        credential.storeToken(token = result.result)
        // The credential instance now has a token! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
      }
    }
  }
}
```

### Token Exchange Flow

[TokenExchangeFlow](oauth2/src/main/java/com/okta/oauth2/TokenExchangeFlow.kt) can be used to perform [OIDC Native SSO](https://openid.net/specs/openid-connect-native-sso-1_0.html).

The Token Exchange Flow exchanges an ID Token and a Device Secret for a new set of tokens.

```kotlin
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.CredentialDataSource
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.oauth2.TokenExchangeFlow
import com.okta.oauth2.TokenExchangeFlow.Companion.createTokenExchangeFlow

val credentialDataSource: CredentialDataSource = CredentialBootstrap.defaultCredentialDataSource()
val tokenExchangeCredential: Credential = credentialDataSource.createCredential()
val tokenExchangeFlow: TokenExchangeFlow = CredentialBootstrap.oidcClient.createTokenExchangeFlow()
when (val result = tokenExchangeFlow.start(idToken, deviceSecret)) {
  is OidcClientResult.Error -> {
      // Timber.e(result.exception, "Failed to login.")
      // TODO: Display an error to the user.
  }
  is OidcClientResult.Success -> {
    tokenExchangeCredential.storeToken(token = result.result)
    // The credential instance now has a token! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
  }
}
```

> Note: You'll want to ensure you have 2 *DIFFERENT* `Credential`s. The first needs to have the `idToken`, and `deviceSecret` minted via a `WebAuthenticationClient`. The second will be used in the `TokenExchangeFlow`.

### Logout

There are multiple terms that might be confused when logging a user out.

- `Credential.delete` - Clears the in memory reference to the `Token` and removes the information from `TokenStorage`, the `Credential` can no longer be used.
- `Credential.revokeToken`/`OidcClient.revokeToken` - Revokes the specified `RevokeTokenType` from the Authorization Server.
- `WebAuthenticationClient.logoutOfBrowser` - Removes the Okta session if the user was logged in via the OIDC Browser redirect flow. Also revokes the associated `Token`(s) minted via this flow.

> Notes:
> - `Credential.delete` does not revoke a token
> - `Credential.revokeToken`/`OidcClient.revokeToken` does not remove the `Token` from memory, or `TokenStorage`. It also does not invalidate the browser session if the `Token` was minted via the OIDC Browser redirect flow.
> - `WebAuthenticationClient.logoutOfBrowser` revokes the `Token`, but does not remove it from memory or `TokenStorage`
> - Revoking a `RevokeTokenType.ACCESS_TOKEN` does not revoke the associated `Token.refreshToken` or `Token.deviceSecret`
> - Revoking a `RevokeTokenType.DEVICE_SECRET` does not revoke the associated `Token.accessToken` or `Token.refreshToken`
> - Revoking a `RevokeTokenType.REFRESH_TOKEN` *DOES* revoke the associated `Token.accessToken` AND `Token.refreshToken`

### Using a Credential to determine user authentication status
There are a few options to determine the status of a user authentication. Each option has unique pros and cons and should be chosen based on the needs of your use case.

- Non null token: `CredentialBootstrap.defaultCredential().token != null`
- getValidAccessToken: `CredentialBootstrap.defaultCredential().getValidAccessToken() != null`
- Custom implementation: `CredentialBootstrap.defaultCredential().token`, `CredentialBootstrap.defaultCredential().refresh()`, and `CredentialBootstrap.defaultCredential().getAccessTokenIfValid()`

Details on each approach are below.

#### Determine authentication status via non null token
`Credential`s do not require a `Token`. If `credential.token == null` then it is not possible for a user to be authenticated. The benefit to using this approach is that the `token` property is available in memory, and can be accessed immediately in a non blocking way.

#### Determine authentication status via getValidAccessToken
`Credential` has a method called `getValidAccessToken` which checks to see if the credential has a token, and has a valid access token. If the access token is expired, and a refresh token exists, a `refresh` is implicitly called on the `Credential`. If the implicit `refresh` is successful, `getValidAccessToken` returns the new access token. There are two main down sides to this approach. First, it's a `suspend fun` and could make network calls. Second, the failure is not returned, an error could occur due to a network error, a missing token, a missing refresh token, or a configuration error.

#### Determine authentication status via custom implementation
If your use case requires insight into errors and the current state of the `Credential`, you can use implement it to your needs with the primitives `Credential` provides. See the documentation for the associated properties and methods: `Credential.token`, `Credential.refresh()`, `Credential.getAccessTokenIfValid()`.

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

## Troubleshooting

- java.lang.NoClassDefFoundError: Failed resolution of: Ljava/time/Instant;
  - Fix: configure [Core Library Desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)

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
