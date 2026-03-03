# Sample App

This is a sample Android application demonstrating how to use the [OktaOAuth2](../oauth2) module along with [WebAuthenticationUI](../web-authentication-ui) and [AuthFoundation](../auth-foundation). It
showcases several OAuth 2.0 flows including:

- **Browser-based login** (Authorization Code Flow via Chrome Custom Tab)
- **Resource Owner Password** flow
- **Device Authorization** flow
- **Token Exchange** flow

## Prerequisites

- An [Okta developer account](https://developer.okta.com/signup) (free)
- Android Studio
- An Okta Application configured as a **Native App**

## Okta Server Setup

### 1. Create an Okta Application

1. Sign in to your [Okta Admin Console](https://your_okta_domain.okta.com/admin).
2. Navigate to **Applications → Applications** and click **Create App Integration**.
3. Select **OIDC - OpenID Connect** as the sign-in method and **Native Application** as the application type, then click **Next**.
4. Configure the application:
    - **App integration name**: Choose a name (e.g., `Okta Mobile Kotlin Sample`)
    - **Grant types**: Ensure **Authorization Code** and **Refresh Token** are checked. Enable **Device Authorization** if you want to test the Device Authorization flow.
    - **Sign-in redirect URIs**: Add `com.okta.sample.android:/login`
    - **Sign-out redirect URIs**: Add `com.okta.sample.android:/logout`
5. Under **Assignments**, assign the app to the appropriate users or groups, then click **Save**.
6. Note the **Client ID** from the application's **General** tab.

### 2. Locate Your Authorization Server

The default authorization server issuer URL follows this pattern:

```
https://<your_okta_domain>.okta.com/oauth2/default
```

You can view all available authorization servers at:
`https://<your_okta_domain>.okta.com/admin/oauth2/as`

## Configuring `okta.properties`

Update the `okta.properties` file in the **root directory** of the project with values from your Okta application:

```properties
issuer=https://<your_okta_domain>.okta.com/oauth2/default
clientId=<your_client_id>
signInRedirectUri=com.okta.sample.android:/login
signOutRedirectUri=com.okta.sample.android:/logout
legacySignInRedirectUri=com.okta.sample.android.legacy:/login
legacySignOutRedirectUri=com.okta.sample.android.legacy:/logout
```

| Property                   | Description                                                                                                                                        |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `issuer`                   | Your authorization server URL. Usually `https://<domain>.okta.com/oauth2/default`. Custom authorization servers are also supported.                |
| `clientId`                 | The Client ID of your Native App from the Okta Admin Console.                                                                                      |
| `signInRedirectUri`        | The redirect URI after sign-in. Must match what is registered in the Okta app. Use reverse domain notation, e.g. `com.okta.sample.android:/login`. |
| `signOutRedirectUri`       | The redirect URI after sign-out. Must match what is registered in the Okta app.                                                                    |
| `legacySignInRedirectUri`  | Redirect URI used by the legacy token migration sample.                                                                                            |
| `legacySignOutRedirectUri` | Redirect URI used by the legacy token migration sample.                                                                                            |

> **Note:** `okta.properties` is read at build time by `app/build.gradle.kts` and injected as `BuildConfig` fields. Never commit real credentials to source control — the file is listed in `.gitignore`
> for this reason.

## Running the Sample

Open the project in Android Studio and run the **app** configuration, or build via Gradle:

```bash
./gradlew :app:assembleDebug
```

## Project Structure

| Package               | Description                                                |
|-----------------------|------------------------------------------------------------|
| `browser`             | Browser-based login using the Authorization Code flow      |
| `deviceauthorization` | Device Authorization Grant flow                            |
| `resourceowner`       | Resource Owner Password flow                               |
| `tokenexchange`       | Token Exchange (Native SSO) flow                           |
| `dashboard`           | Post-login screen showing token info and available actions |
