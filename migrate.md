# Migrating from okta-oidc-android
There are many reasons to upgrade from [okta-oidc-android](https://github.com/okta/okta-oidc-android)!

- [Unlocking new use cases](README.md#unlocking-use-cases)
- Kotlin Support
- Coroutines based APIs
- Multiple signed in users via [CredentialDataSource](auth-foundation/src/main/java/com/okta/authfoundation/credential/CredentialDataSource.kt)
- Store associated data with tokens via `Credential.tags`

## Installation
Add the Legacy Token Migration dependency to your `build.gradle` file:

```gradle
implementation('com.okta.kotlin:legacy-token-migration')
```

## Configuration
Applications migrating tokens will require code to configure both the legacy `SessionClient` as well as the new `Credential`.
See [README.md](README.md) for configuring Auth Foundation.

## Token Migration
Once your application is configured, use the `LegacyTokenMigration.migrate` method to migrate your tokens.

```kotlin
import android.content.Context
import com.okta.authfoundation.credential.Credential
import com.okta.authfoundationbootstrap.CredentialBootstrap
import com.okta.legacytokenmigration.LegacyTokenMigration
import com.okta.oidc.clients.sessions.SessionClient

val context: Context = TODO("Supplied by the developer.")
val sessionClient: SessionClient = TODO("Supplied by the developer.")
val credential: Credential = CredentialBootstrap.defaultCredential()

when (val result = LegacyTokenMigration.migrate(context, sessionClient, credential)) {
    is LegacyTokenMigration.Result.Error -> TODO("An error occurred: ${result.exception}")
    LegacyTokenMigration.Result.MissingLegacyToken -> TODO()
    LegacyTokenMigration.Result.PreviouslyMigrated -> TODO()
    LegacyTokenMigration.Result.SuccessfullyMigrated -> TODO()
}
```

Session client is often accessed via `webAuthClient.sessionClient`.

## Update legacy configuration

In order for the user to get automatically redirected to the correct activity without a prompt, update the legacy configuration to use a legacy `appAuthRedirectScheme`.

```gradle
android {
  defaultConfig {
    manifestPlaceholders = [
      "webAuthenticationRedirectScheme": "<your_redirect_scheme>",
      "appAuthRedirectScheme": "<your_redirect_scheme>.legacy",
    ]
  }
}
```

## Use Credential

`Credential` replaces `SessionClient`, remove usages of `SessionClient` in your application.
See all available methods here: [Credential](auth-foundation/src/main/java/com/okta/authfoundation/credential/Credential.kt).

## Use WebAuthenticationClient

`WebAuthenticationClient` replaces `WebAuthClient`, remove usages of `WebAuthClient` in your application.
See all available methods here: [WebAuthenticationClient](web-authentication-ui/src/main/java/com/okta/webauthenticationui/WebAuthenticationClient.kt).

## Sample
There's a sample demonstrating migration in the `legacy-token-migration-sample` [directory](legacy-token-migration-sample).
