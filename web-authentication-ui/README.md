# WebAuthenticationUI

Browser-based OIDC sign-in and sign-out for Android. `WebAuthentication` launches a
[Chrome Custom Tab](https://developer.chrome.com/docs/android/custom-tabs/) to display the Okta
login form and, once the user completes it, captures the redirect back into your app. Internally it
wraps the `oauth2` module's Authorization Code (with PKCE) and Redirect End Session flows and uses
the KMP `OAuth2Client` from `auth-foundation`.

This module is **Android only**.

## Table of Contents

- [Installation](#installation)
- [Redirect scheme configuration](#redirect-scheme-configuration)
- [Troubleshooting](#troubleshooting)

## Installation

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:2.0.5"))
    implementation("com.okta.kotlin:auth-foundation")
    implementation("com.okta.kotlin:oauth2")
    implementation("com.okta.kotlin:web-authentication-ui")
}
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
