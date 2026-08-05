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
- [Troubleshooting](#troubleshooting)

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

The KMP client performs all HTTP work through an `ApiExecutor`. The default is `KtorHttpExecutor`,
which wraps a Ktor `HttpClient` (OkHttp engine on Android/JVM, with timeouts, cookies, and caching
pre-configured). To customize networking, build your own Ktor `HttpClient` and pass it to
`KtorHttpExecutor`, then set it as the builder's `apiExecutor`:

```kotlin
import com.okta.authfoundation.api.http.KtorHttpExecutor
import com.okta.authfoundation.client.OAuth2ClientBuilder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.Logging

val httpClient = HttpClient {
    install(Logging)
    // Add your own plugins, interceptors, or engine configuration here.
}

val client =
    OAuth2ClientBuilder.create(
        issuerUrl = "https://your-org.okta.com",
        clientId = "your-client-id",
        scope = listOf("openid", "profile", "email", "offline_access")
    ) {
        apiExecutor = KtorHttpExecutor(httpClient = httpClient)
    }.getOrThrow()
```

For full control you can implement the `ApiExecutor` interface yourself (a single
`suspend fun execute(request: ApiRequest): Result<ApiResponse>`) and swap out the HTTP engine
entirely — an advanced use case that most integrations won't need.

> Note: The deprecated Android-only APIs (`OidcConfiguration` / `OAuth2Client.default`) customize
> networking differently, through an OkHttp `Call.Factory` supplied to
> `AuthFoundationDefaults.okHttpClientFactory`. That surface applies only to the legacy Android path
> and has no effect on the KMP client above.

## Rate limiting

Okta returns HTTP 429 (Too Many Requests) responses when a request exceeds the endpoint's rate
limit (see [Rate Limiting at Okta][rate-limiting]).

**The KMP client does not retry 429 responses by default** — it emits a `RateLimitExceededEvent` and
surfaces the failure to the caller as an `OAuth2ClientResult.Error.RateLimitException`. This differs
from the deprecated Android-only path (see note below). To opt in to automatic retries, provide a
`rateLimitRetryCallback` on the builder. Return a `RateLimitRetryConfig` to retry, or `null` to give
up and surface the failure:

```kotlin
import com.okta.authfoundation.client.OAuth2ClientBuilder
import com.okta.authfoundation.client.kmp.MaxRetries
import com.okta.authfoundation.client.kmp.MinDelaySeconds
import com.okta.authfoundation.client.kmp.RateLimitRetryConfig

val client =
    OAuth2ClientBuilder.create(
        issuerUrl = "https://your-org.okta.com",
        clientId = "your-client-id",
        scope = listOf("openid", "profile", "email", "offline_access")
    ) {
        // retryCount is 0-based (0 on the first retry opportunity).
        rateLimitRetryCallback = { retryCount ->
            if (retryCount < 3) {
                RateLimitRetryConfig(MaxRetries(3), MinDelaySeconds(1L))
            } else {
                null // stop retrying
            }
        }
    }.getOrThrow()
```

The actual wait before each retry is `max(Retry-After header, minDelaySeconds)`. You can observe
rate-limit activity regardless of retry configuration by collecting the client's event flow:

```kotlin
import com.okta.authfoundation.client.kmp.events.RateLimitExceededEvent

scope.launch {
    client.events.collect { event ->
        if (event is RateLimitExceededEvent) {
            // event.requestUrl, event.responseHeaders, event.retryAfterSeconds
        }
    }
}
```

> Note: The deprecated Android-only path behaves differently — it retries 429s automatically
> (defaulting to `maxRetries = 3`, `minDelaySeconds = 1`) and lets you adjust those values by
> mutating the `RateLimitExceededEvent` delivered through `AuthFoundationDefaults.eventCoordinator`.
> That automatic-retry behavior and the mutable `maxRetries` / `minDelaySeconds` fields exist only on
> the legacy Android event; the KMP `RateLimitExceededEvent` shown above is read-only and does not
> retry on its own.

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

## Troubleshooting

- `java.lang.NoClassDefFoundError: Failed resolution of: Ljava/time/Instant;` — the SDK uses
  `java.time` APIs. On Android, enable
  [Core Library Desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring)
  to make them available on all supported API levels.

[rate-limiting]: https://developer.okta.com/docs/api/getting_started/rate-limits
