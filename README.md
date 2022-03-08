[![Support](https://img.shields.io/badge/support-Developer%20Forum-blue.svg)][devforum]
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta Mobile SDK for Kotlin

## Release status

This library uses semantic versioning and follows Okta's [Library Version Policy][okta-library-versioning].

| Version | Status                             |
| ------- | ---------------------------------- |
| 0.1.0   | ✔️ Beta                             |

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

The following SDKs are present in this repository:
- [AuthFoundation](auth-foundation) -- Common classes for managing credentials, and used as a foundation for other libraries.
- [OktaOAuth2](oauth2) -- OAuth2 authentication capabilities for authenticating users.
- [WebAuthenticationUI](web-authentication-ui) -- Authenticate users using web-based OIDC flows.

The use of this SDK enables you to build or support a myriad of different authentication flows and approaches. To simplify getting started, here are a few samples to demonstrate its usage.

### Kotlin Coroutines
[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) are used extensively throughout the SDKs. All methods are intended to be used via the main thread, and will switch to a background thread internally if performing network IO or expensive computation.

### Web Authentication using OIDC redirect

The simplest way to integrate authentication in your app is with OIDC through a web browser, using the Authorization Code Flow grant.

#### Configure your OIDC Settings

Before authenticating your user, you need to create your `OidcClient`, using the settings defined in your application in the Okta Developer Console.

```kotlin
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.client.OidcClientResult
import com.okta.authfoundation.client.OidcConfiguration
import okhttp3.HttpUrl.Companion.toHttpUrl

val oidcConfiguration = OidcConfiguration(
    clientId = "{clientId}",
    defaultScopes = setOf("openid", "email", "profile", "offline_access"),
    signInRedirectUri = "{signInRedirectUri}",
    signOutRedirectUri = "{signOutRedirectUri}",
)
val client = OidcClient.createFromDiscoveryUrl(
    oidcConfiguration,
    "https://{yourOktaOrg}.okta.com/.well-known/openid-configuration".toHttpUrl(),
)
```

#### Create a Credential

The Credential type handles storage, OAuth conveniences and signing requests to your Resource Server after login occurs. Before authenticating, we'll create the credential.

```kotlin
import android.content.Context
import com.okta.authfoundation.client.OidcClient
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundation.credential.CredentialDataSource.Companion.createCredentialDataSource

val context: Context = TODO("Available from previous steps.")
val credentialDataSource = oidcClient.createCredentialDataSource(context)
val credential: Credential = credentialDataSource.createCredential()
```

#### Create a Web Authentication Client

Once you've created your `Credential`, we'll use it to create our `WebAuthenticationClient` and authenticate.

This launches a [Chrome Custom Tab][https://developer.chrome.com/docs/android/custom-tabs/] to display the login form, and once complete, redirects back to the application.

```kotlin
import android.content.Context
import com.okta.authfoundation.credential.Credential
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.webauthenticationui.WebAuthenticationClient
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.createWebAuthenticationClient

val context: Context = TODO("Available from previous steps.")
val credential: Credential = TODO("Available from previous steps.")
val webAuthenticationClient = credential.oidcClient.createWebAuthenticationClient()
when (val result = webAuthenticationClient.login(context)) {
    is OidcClientResult.Error -> {
        // Timber.e(result.exception, "Failed to start login flow.")
        // TODO: Display an error to the user.
    }
    is OidcClientResult.Success -> {
        // TODO: Store the AuthorizationCodeFlow.Context in an instance variable, and wait for the application redirect to be called.
        // val authorizationCodeFlowContext: AuthorizationCodeFlow.Context = result.result
    }
}
```

Next we need to be sure our application handles the redirect. Add the following `<intent-filter>` to your `AndroidManifest.xml`:

Note: you'll need to replace the `{RedirectActivity}` and `{oktaIdxRedirectScheme}` listed below.

```xml
<activity
    android:name=".{RedirectActivity}"
    android:autoRemoveFromRecents="true"
    android:exported="true"
    android:launchMode="singleInstance">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="{oktaIdxRedirectScheme}" />
    </intent-filter>
</activity>
```

Next once the redirect happens, pass the `Uri` to the SDK to finish the authentication process.

```kotlin
import android.net.Uri
import com.okta.authfoundation.credential.Credential
import com.okta.oauth2.AuthorizationCodeFlow
import com.okta.webauthenticationui.WebAuthenticationClient.Companion.createWebAuthenticationClient

val uri: Uri = TODO("Available from previous steps.")
val credential: Credential = TODO("Available from previous steps.")
val authorizationCodeFlowContext: AuthorizationCodeFlow.Context = TODO("Available from previous steps.")

when (val result = credential.oidcClient.createWebAuthenticationClient().resume(uri, authorizationCodeFlowContext)) {
    is OidcClientResult.Error -> {
        // Show an error to the user. The relevant exception is available in `result.exception`.
    }
    is OidcClientResult.Success -> {
        // The credential instance now has a token! You can use the `Credential` to make calls to OAuth endpoints, or to sign requests!
    }
}
```

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

## Running the sample

The sample is designed to show what is possible when using the SDK.

### Configuring the sample

Update the `okta.properties` file in the root directory of the project with the contents created from the Okta admin dashboard:
```
issuer=https://YOUR_ORG.okta.com/oauth2/default
clientId=test-client-id
signInRedirectUri=com.okta.sample.android:/login
signOutRedirectUri=com.okta.sample.android:/logout
```

Notes:

- issuer - is your authorization server, usually https://your_okta_domain.okta.com/oauth2/default, but custom authorization servers are supported. See https://your_okta_domain.okta.com/admin/oauth2/as for available authorization servers.
- clientId - is your applications client id, created in your Okta admin dashboard
- signInRedirectUri - is used for browser redirect, and should follow the format of reverse domain name notation + /login, ie: com.okta.sample.android:/login
- signOutRedirectUri - is used for browser redirect, and should follow the format of reverse domain name notation + /logout, ie: com.okta.sample.android:/logout

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
