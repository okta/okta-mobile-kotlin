# AuthFoundation

AuthFoundation is the core Okta Mobile Kotlin module for Kotlin Multiplatform authentication and token management. It provides the shared `OAuth2Client`, credential storage, token validation, rate-limit handling, and the lower-level APIs used by the other modules.

## Table of Contents

- [Installation](#installation)
- [Creating an OAuth2Client](#creating-an-oauth2client)
- [Credential management](#credential-management)
- [Auto backup rules](#auto-backup-rules)
- [Biometric credentials](#biometric-credentials)
- [Networking customization](#networking-customization)
- [Rate limiting](#rate-limiting)
- [Customization](#customization)
- [Migrating from Android-only APIs to KMP APIs](#migrating-from-android-only-apis-to-kmp-apis)
- [Android-only legacy APIs](#android-only-legacy-apis)
- [Notes](#notes)

## Installation

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:2.0.5"))
    implementation("com.okta.kotlin:auth-foundation")
}
```

## Creating an OAuth2Client

Use the KMP builder to create a client:

```kotlin
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.kmp.OAuth2Client

val client: OAuth2Client =
    OAuth2ClientBuilder.create(
        issuerUrl = "https://your-org.okta.com",
        clientId = "your-client-id",
        scope = listOf("openid", "profile", "email", "offline_access")
    ) {
        authorizationServerId = "default"
    }.getOrThrow()
```

## Credential management

The KMP `Credential` is an immutable snapshot. When token state changes, the APIs return a new `Credential` instead of mutating in place.

```kotlin
import com.okta.authfoundation.credential.kmp.TokenCredentialManager

val manager = TokenCredentialManager(
    client = client,
    storage = storage, // a `TokenStorage`, e.g. `RoomTokenStorage`
    defaultIdStore = defaultIdStore // a `DefaultCredentialIdStore`, e.g. `RoomDefaultCredentialIdStore`
)
```

Key points:

- `refreshToken()` returns a new snapshot; the original snapshot is stale after a successful refresh.
- `refreshIfExpired()` only refreshes when the current access token has expired, returning a new snapshot.
- `accessTokenIfNotExpired()` is a pure read (no network call) that returns the access token string, or `null` if expired.
- Android can use `RoomTokenStorage` with `AndroidTokenEncryptionHandler`.
- JVM can use the unencrypted KMP storage helpers.

Ways to check whether a user is authenticated, via `TokenCredentialManager`:

- `manager.getDefault().getOrNull() != null`
- `manager.allIds().getOrDefault(emptyList()).isNotEmpty()`
- `manager.getDefault().getOrNull()?.accessTokenIfNotExpired() != null`
- Custom checks with `Credential.token`, `Credential.refreshToken()`, and `Credential.accessTokenIfNotExpired()`

## Auto backup rules

If your app overrides Android backup settings, include the SDK backup rules from:

- `auth-foundation/src/main/res/xml/data_extraction_rules.xml`
- `auth-foundation/src/main/res/xml/full_backup_content.xml`

## Biometric credentials

AuthFoundation supports biometric-backed credential storage on Android. Use the biometric storage helpers when you need device-bound token protection, and fetch those credentials with async APIs.

## Networking customization

The SDK uses OkHttp by default. You can provide a custom call factory or interceptor when you need to tune request behavior.

## Rate limiting

The client exposes rate-limit retry configuration so you can control how 429 responses are retried.

## Customization

AuthFoundation also owns the shared client configuration features:

- `OAuth2EndpointOverrides` for custom discovery endpoints
- rate-limit retry configuration
- token validators for ID, access, and device-secret claims

## Migrating from Android-only APIs to KMP APIs

The older Android-only APIs still exist for compatibility, but new code should prefer the KMP packages in `com.okta.authfoundation.client.kmp` and `com.okta.authfoundation.credential.kmp`.

## Android-only legacy APIs

Legacy Android-specific APIs remain functional for existing integrations, but they are no longer the preferred path.

## Notes

- On Android, the SDK uses coroutines heavily and performs network and crypto work off the main thread.
- If you provide custom backup rules, include the SDK backup rules from `src/main/res/xml`.
