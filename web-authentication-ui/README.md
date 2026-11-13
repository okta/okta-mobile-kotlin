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
- [Resuming login after process death](#resuming-login-after-process-death)
- [Troubleshooting](#troubleshooting)

## Installation

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:3.2.0"))
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
previous user's browser session), set `ephemeralBrowsingEnabled` on `DefaultWebAuthenticationProvider`:

```kotlin
val webAuthentication = WebAuthentication(
    client,
    DefaultWebAuthenticationProvider(ephemeralBrowsingEnabled = true)
)
```

Use this instead of calling `setEphemeralBrowsingEnabled` directly from `customizeTabsIntent` or
`customizeAuthTabIntent`: `WebAuthentication` picks between Chrome Custom Tabs and Auth Tab per-launch
depending on what the resolved browser supports, and only one of those two hooks runs for a given
launch — setting the flag on just one builder means it's silently dropped whenever the other path is
taken. `ephemeralBrowsingEnabled` applies to whichever path actually launches.

Browsers that don't support ephemeral browsing silently ignore the flag rather than failing, so it's
always safe to set.

## Resuming login after process death

If Android kills your app while the user is in the browser (low memory, a phone call, a password
manager), the redirect arrives at a fresh process and the original `login()` call no longer exists.
To handle this, `login()` persists the in-progress flow state to app-private DataStore for 10
minutes, and `resumeLogin()` finishes the token exchange once the app restarts:

```kotlin
// For example in your launching Activity's onCreate, using a lifecycle-aware scope.
lifecycleScope.launch {
    // Returns null when there is no interrupted login to resume.
    val result = webAuthentication.resumeLogin(applicationContext) ?: return@launch
    result.onSuccess { tokenInfo -> /* signed in */ }
        .onFailure { /* FlowCancelledException, or the token exchange error */ }
}
```

`resumeLogin()` suspends until the browser redirect arrives, or returns `null` once the 10-minute window
passes without one (for example the user closed the browser without finishing). Cancelling it leaves the
pending login in place so it can be resumed again. It also returns `null` right away if a `login()`,
`logoutOfBrowser()` or another `resumeLogin()` is already running in this process, since that call owns
the redirect. Starting a new `login()` discards any pending login.

### Opting in to persistence

Persistence is **off by default**, because the persisted state includes the PKCE `code_verifier`. Without it,
a login interrupted by process death can't be resumed and `resumeLogin()` returns `null`. To turn it on:

```kotlin
val webAuthentication = WebAuthentication(
    client,
    DefaultWebAuthenticationProvider(),
    PendingLoginPersistence.Enabled()
)
```

The state is stored in the `com.okta.webauthenticationui.pending_login` DataStore. Exclude that file from
Android backup and device transfer in your `backup_rules.xml` / `data_extraction_rules.xml`, and prefer an
encryptor (below). A pending login is tied to the client ID that started it; `resumeLogin()` on a
`WebAuthentication` for a different client ignores it. If persistence is later disabled, the next
`resumeLogin()` removes any leftover entry.

### Encrypting the persisted state

The persisted state includes the PKCE `code_verifier`. The SDK stores it **unencrypted** unless you supply an encryptor,
in app-private storage. Whether to encrypt it, and how, is up to your app. To encrypt it, implement
`PendingLoginEncryptor` and pass it through `PendingLoginPersistence.Enabled`. For example, using a
key from the Android Keystore:

```kotlin
class KeystorePendingLoginEncryptor(private val key: SecretKey) : PendingLoginEncryptor {
    override suspend fun encrypt(plaintext: String): Result<String> = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.iv + cipher.doFinal(plaintext.toByteArray())
        Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    override suspend fun decrypt(ciphertext: String): Result<String> = runCatching {
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12)) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }
}

val webAuthentication = WebAuthentication(
    client,
    DefaultWebAuthenticationProvider(),
    PendingLoginPersistence.Enabled(KeystorePendingLoginEncryptor(myKeystoreKey))
)
```

If `encrypt` fails, the login still works but cannot be resumed. If `decrypt` fails (for example the
key was lost), `resumeLogin()` treats it as nothing to resume.

Android Auto Backup is on by default and backs up your app's `files/` directory, so unless you
exclude it the persisted state (including the unencrypted `code_verifier`) can end up in cloud backups
and device-to-device transfers. Exclude the DataStore file, which lives at
`files/datastore/com.okta.webauthenticationui.pending_login.preferences_pb`, in both backup configs:

```xml
<!-- res/xml/backup_rules.xml, referenced by android:fullBackupContent (API 30 and lower) -->
<full-backup-content>
    <exclude domain="file" path="datastore/com.okta.webauthenticationui.pending_login.preferences_pb" />
</full-backup-content>

<!-- res/xml/data_extraction_rules.xml, referenced by android:dataExtractionRules (API 31+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="datastore/com.okta.webauthenticationui.pending_login.preferences_pb" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="datastore/com.okta.webauthenticationui.pending_login.preferences_pb" />
    </device-transfer>
</data-extraction-rules>
```

## Troubleshooting

### PAR with custom authorization servers

`WebAuthentication` uses `AuthorizationCodeFlow` under the hood. PAR is opt-in: set
`enablePushedAuthorizationRequests = true` on `OAuth2ClientBuilder` along with a custom
authorization server (`authorizationServerId` set) that advertises `pushed_authorization_request_endpoint`.

- Optional PAR: falls back to the classic browser authorization URL when PAR is unavailable.
- Required PAR (`require_pushed_authorization_requests=true`): browser sign-in fails if PAR cannot complete.

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
