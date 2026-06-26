# Okta Direct Authentication App

This application demonstrates how to use the Okta Direct Authentication SDK for Android to build a custom authentication experience.

## Features

### Direct Authentication
*   Sign in with a username and password.
*   WebAuthn/Passkey authentication (primary and MFA). (Requires server support)
*   Multi-factor authentication (MFA) with Okta Verify, One-Time Passwords (OTP), and more.
*   Self-service password recovery (SSPR).

### OAuth2 Flows
*   **Resource Owner Flow** — Username + password via OAuth2 Resource Owner Password grant.
*   **Device Authorization Flow** — Device code + verification URL with polling for approval.
*   **Browser Sign-In** — Authorization Code + PKCE via system browser (Chrome Custom Tabs on Android, localhost redirect on Desktop).
*   **Token Exchange Flow** — Native SSO token exchange using an existing ID token and device secret.
*   **Session Token Flow** — Exchange a pre-obtained session token for OAuth2 tokens (server-side redirect, no browser).

## Setup

To build and run this application, you first need to configure your Okta organization and application, then create a local properties file to store the configuration values.

### Okta Configuration

Follow these steps in your Okta Admin Console to configure your application for Direct Authentication.

#### 1. Enable Authenticators
Ensure the authenticators you want to use (e.g., Okta Verify, Google Authenticator, SMS) are enabled in your Okta organization.

*   In the Admin Console, go to **Security > Authenticators**.
*   On the **Setup** tab, add or verify that your desired authenticators are present.
*   On the **Enrollment** tab, find your policy (e.g., Default Policy) and ensure the authenticator's status is set to **Optional** or **Required** so users can enroll in them.

#### 2. Create an App Integration
Register your client application in Okta to get a Client ID.

*   In the Admin Console, go to **Applications > Applications**.
*   Click **Create App Integration**.
*   Select **OIDC - OpenID Connect** as the sign-in method and **Native Application** as the application type, then click **Next**.
*   Provide an **App integration name**.
*   In the **Grant type** section, click **Advanced** and select the direct auth grant types you need (e.g., **Password**, **OTP**, **OOB**, **MFA OOB**).
*   For OAuth2 flows, also enable: **Authorization Code** (with PKCE), **Resource Owner Password**, **Device Authorization**, and **Token Exchange**.
*   Configure **Sign-in redirect URIs** (you can use the default for this sample app) and **Controlled access** as needed, then click **Save**.
*   From the **General** tab of your new app integration, copy the **Client ID**.

#### 3. Configure the Authorization Server Policy
Modify your authorization server's access policy to permit the direct authentication grant types.

*   In the Admin Console, go to **Security > API**.
*   From the **Authorization Servers** tab, select your `default` server.
*   Go to the **Access Policies** tab and edit the relevant policy rule (e.g., `Default Policy Rule`).
*   In the **"IF Grant type is"** section, click **Advanced**.
*   Select the same grant types you enabled in Step 2 (including both direct auth and OAuth2 grant types), then click **Update Rule**.

#### 4. Configure the App Sign-on Policy
Set up a policy to define your application's authentication requirements.

*   Navigate back to your application (**Applications > Applications**).
*   Go to the **Sign On** tab and find the **User authentication** section.
*   Edit or clone a policy to define the required authentication factors (e.g., "Password + Another factor" or "Any 1 factor type").

#### 5. Enroll a Test User
Ensure your test user is enrolled in the authenticators you intend to use.

*   In the Admin Console, go to **Directory > People** and select your test user.
*   Go to the **Profile** tab and check the **More** dropdown to reset their password or enroll them in authenticators.
*   For MFA, ensure the user has enrolled in at least one of the authenticators you enabled in Step 1 (e.g., Okta Verify, a phone number for SMS, etc.).

### Local Configuration

1.  Create or edit a `local.properties` file in the root of the `okta-mobile-kotlin` project.
2.  Add the following properties to the file, using the values from your Okta configuration:

    ```properties
    issuer=<your_okta_domain>
    clientId=<your_application_client_id>
    authorizationServerId=<your_authorization_server_id>
    signInRedirectUri=<android_custom_scheme_uri>
    desktopSignInRedirectUri=http://localhost:8080/callback
    ```

    Replace the following values:
    *   `<your_okta_domain>`: Your Okta organization's domain (e.g., `https://dev-12345.okta.com`).
    *   `<your_application_client_id>`: The Client ID you copied in Step 2.
    *   `<your_authorization_server_id>`: The ID of your authorization server (usually `default`).
    *   `signInRedirectUri`: **Android only.** Custom scheme redirect URI for Browser Sign-In and Session Token flows (e.g., `com.example.app:/callback`). Register this in your Okta app's **Sign-in redirect URIs**.
    *   `desktopSignInRedirectUri`: **Desktop only.** Localhost redirect URI for Browser Sign-In and Session Token flows (e.g., `http://localhost:8080/callback`). Register this in your Okta app's **Sign-in redirect URIs**.

    > **Note**: `signInRedirectUri` and `desktopSignInRedirectUri` are only required for the Browser Sign-In and Session Token flows. If you only plan to use Direct Authentication, Resource Owner, Device Authorization, or Token Exchange flows, you can omit both.

### Self-Service Password Recovery (SSPR)

To enable self-service password recovery, you must grant the `okta.myAccount.password.manage` scope to your application.

1.  In your Okta Admin Console, go to **Applications > Applications** and select your application.
2.  Go to the **Okta API Scopes** tab.
3.  Find `okta.myAccount.password.manage` and click **Grant**.

This scope allows the application to use the MyAccount Password API to change a user's password.

## Build and Run

Once you have configured Okta and your `local.properties` file, you can build and run the application.

### Android

1.  Open the `okta-mobile-kotlin` project in Android Studio.
2.  Select the `okta-direct-auth-android-app` run configuration.
3.  Click the "Run" button.

### Desktop

```bash
./gradlew :okta-direct-auth-desktop-app:run
```

### Using the App

When the app launches, you'll see a **Home Menu** with the following options:

*   **Direct Authentication** — Enters the existing Direct Auth flow (username, password, MFA, passkeys, etc.).
*   **Resource Owner Flow** — Enter a username and password to get OAuth2 tokens directly.
*   **Device Authorization Flow** — Starts a device code flow. Visit the displayed URL in a browser and enter the code to approve.
*   **Browser Sign In** — Opens a browser for Authorization Code + PKCE authentication. On Android, uses Chrome Custom Tabs. On Desktop, opens the system browser and captures the redirect on localhost.
*   **Token Exchange Flow** — Paste an existing ID token and device secret to exchange for new tokens (Native SSO).
*   **Session Token Flow** — Paste a session token (obtained from the Okta Authn API) to exchange for OAuth2 tokens.

### OAuth2 Flow Notes

*   **Browser Sign In** requires `signInRedirectUri` to be configured in `local.properties` and registered in your Okta app's redirect URIs.
*   **Session Token Flow** requires `signInRedirectUri` for the server-side redirect. To obtain a session token, use the [Okta Authentication API](https://developer.okta.com/docs/reference/api/authn/) (e.g., via `curl` or another tool).
*   **Token Exchange** requires tokens from a prior authentication that included the `device_sso` scope to obtain a device secret.
*   **Device Authorization** requires the `urn:ietf:params:oauth:grant-type:device_code` grant type enabled on your authorization server.
