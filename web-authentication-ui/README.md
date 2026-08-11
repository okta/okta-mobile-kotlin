# WebAuthenticationUI

Browser-based OIDC sign-in and sign-out for Android. `WebAuthentication` launches a
[Chrome Custom Tab](https://developer.chrome.com/docs/android/custom-tabs/) to display the Okta
login form and, once the user completes it, captures the redirect back into your app. Internally it
wraps the `oauth2` module's Authorization Code (with PKCE) and Redirect End Session flows and uses
the KMP `OAuth2Client` from `auth-foundation`.

This module is **Android only**.

## Table of Contents

- [Installation](#installation)
- [Usage](#usage)
- [Redirect scheme configuration](#redirect-scheme-configuration)
- [Ephemeral browsing](#ephemeral-browsing)
- [Troubleshooting](#troubleshooting)

## Installation

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:3.0.0"))
    implementation("com.okta.kotlin:auth-foundation")
    implementation("com.okta.kotlin:oauth2")
    implementation("com.okta.kotlin:web-authentication-ui")
}
```

## Usage

Construct `WebAuthentication` with a KMP `OAuth2Client` from `auth-foundation`, then call `login`
from an `Activity` context to sign in, and `logoutOfBrowser` to sign out:

```kotlin
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.webauthenticationui.WebAuthentication

val client = OAuth2ClientBuilder.create(
    issuerUrl = "https://your-org.okta.com",
    clientId = "your-client-id",
    scope = listOf("openid", "profile", "email", "offline_access")
).getOrThrow()

val webAuthentication = WebAuthentication(client)

// Sign in (call from an Activity):
val tokenInfo = webAuthentication.login(
    context = activity,
    redirectUrl = "com.okta.sample.android:/login",
    scope = listOf("openid", "profile", "email", "offline_access")
).getOrElse { error ->
    // Handle error, e.g. WebAuthentication.FlowCancelledException if the user dismissed the browser
    return
}
val accessToken = tokenInfo.accessToken

// Sign out, using the idToken obtained from login above:
val idToken = tokenInfo.idToken ?: return
webAuthentication.logoutOfBrowser(
    context = activity,
    redirectUrl = "com.okta.sample.android:/logout",
    idToken = idToken
)
```

## Redirect scheme configuration

Your app must claim the redirect scheme so the browser can hand the callback back to it. Set the
`webAuthenticationRedirectScheme` manifest placeholder in your `build.gradle` to the scheme of your
`signInRedirectUri`. For example, a `signInRedirectUri` of `com.okta.sample.android:/login` uses the
scheme `com.okta.sample.android`:

```groovy
android {
    defaultConfig {
        manifestPlaceholders = [
            "webAuthenticationRedirectScheme": "com.okta.sample.android"
        ]
    }
}
```

## Ephemeral browsing

To request an **ephemeral browsing session** (no cookies or session data persisted from or to the
browser, useful when a device is shared between accounts and you want to avoid silently reusing a
previous user's browser session), set `customizeTabsIntent` on `DefaultWebAuthenticationProvider`:

```kotlin
val webAuthentication = WebAuthentication(
    client,
    DefaultWebAuthenticationProvider(
        customizeTabsIntent = { _, builder -> builder.setEphemeralBrowsingEnabled(true) }
    )
)
```

Browsers that don't support ephemeral browsing silently ignore the flag rather than failing, so it's
always safe to set.

## Troubleshooting

### `FlowCancelledException`

`WebAuthentication.FlowCancelledException` is meant to be thrown when the user cancels the login
flow — usually by dismissing the browser login window. It can sometimes be thrown incorrectly in
these cases:

- **Logging out through the Android system WebView.** The WebView doesn't persist the session after
  a successful login, so logout never receives a redirect and the user is forced to cancel the
  logout.
- **Clearing the browser cache after logging in, then logging out.** As above, the browser must
  retain the login state to complete the logout redirect; without it, the browser cannot provide the
  logout redirect.
- **A browser returning an empty redirect result followed by a well-defined one.** Observed on some
  older devices and browsers. Work around this by increasing
  `AuthFoundationDefaults.loginCancellationDebounceTime`, which controls how long the SDK waits for a
  well-defined redirect before treating an empty result as a cancellation.
