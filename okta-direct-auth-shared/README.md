# Okta Direct Authentication App

This sample application demonstrates how to use the Okta Direct Authentication SDK to build a custom authentication experience.

## Table of Contents

- [Features](#features)
  - [Direct Authentication](#direct-authentication)
  - [OAuth2 Flows](#oauth2-flows)
  - [Cross App Access](#cross-app-access)
- [Setup](#setup)
  - [Okta Configuration](#okta-configuration)
    - [1. Enable Authenticators](#1-enable-authenticators)
    - [2. Create an App Integration](#2-create-an-app-integration)
    - [3. Configure the Authorization Server Policy](#3-configure-the-authorization-server-policy)
    - [4. Configure the App Sign-on Policy](#4-configure-the-app-sign-on-policy)
    - [5. Enroll a Test User](#5-enroll-a-test-user)
  - [Local Configuration](#local-configuration)
  - [Confidential client authentication (local testing only)](#confidential-client-authentication-local-testing-only)
  - [Cross App Access (local testing only)](#cross-app-access-local-testing-only)
  - [Self-Service Password Recovery (SSPR)](#self-service-password-recovery-sspr)
- [Build and Run](#build-and-run)
  - [Android](#android)
  - [Desktop](#desktop)
  - [Using the App](#using-the-app)
  - [OAuth2 Flow Notes](#oauth2-flow-notes)

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

### Cross App Access
Demonstrates the SDK's Cross App Access support: signing in with a dedicated requesting-app identity to obtain a scoped access token for a separate resource app, in a separate security domain, without a second consent prompt or a static API key. Requires an administrator-configured trust relationship between the two orgs — see [Cross App Access (local testing only)](#cross-app-access-local-testing-only).

*   **One-action exchange** — obtains and redeems an ID-JAG (the short-lived assertion the identity provider issues for this purpose) in a single call.
*   **Step-by-step mode** — obtains the ID-JAG first so you can inspect it, then redeems it separately; the same ID-JAG can be redeemed again for a fresh resource access token without another round trip to the identity provider, as long as it hasn't expired.
*   Either mode lets you choose which part of the signed-in session to use as the subject: the ID token, the access token, or the refresh token — whichever the resource app's authorization server accepts.

## Setup

To build and run this application, you first need to configure your Okta organization and application, then create a local properties file to store the configuration values.

### Okta Configuration

Follow these steps in your Okta Admin Console to configure your application for Direct Authentication.

#### 1. Enable Authenticators
Ensure the authenticators you want to use (e.g., Okta Verify, Google Authenticator, SMS, Email) are enabled in your Okta organization.

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

#### 3a. Enable PAR (for Browser Sign-In demo)
Use a custom authorization server (typically `default`) and enable PAR in the server settings:

*   In **Security > API > Authorization Servers**, open your custom authorization server.
*   In **Settings**, enable Pushed Authorization Requests (PAR).
*   Keep `authorizationServerId=<your_authorization_server_id>` in `local.properties` (for example, `default`).
*   PAR behavior for Browser Sign-In:
    *   If the server advertises PAR and it succeeds, the sample uses `request_uri`.
    *   If PAR is optional and unavailable/fails, it falls back to the classic browser authorize URL.
    *   If metadata requires PAR, Browser Sign-In fails when PAR cannot be completed.

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

    To also try Cross App Access, add a resource app target. See [Cross App Access (local testing only)](#cross-app-access-local-testing-only) for the full set of `xaaTarget*` keys and the trust-relationship prerequisite. These keys are entirely optional — omitting all of them leaves the rest of the sample unaffected and only hides the Cross App Access menu entry behind a "not configured" message.

### Confidential client authentication (local testing only)

Browser Sign-In and Direct Authentication both build public clients by default — the correct and
only recommended setup for a distributed Android or desktop app. To try either against a
**confidential** client (for example, to exercise PAR with `private_key_jwt` or `client_secret`
authentication, or to run Direct Authentication as a confidential client), add one of the
following to `local.properties`:

```properties
clientSecret=your-client-secret
```

or, for `private_key_jwt` (generate a PKCS#8 key with
`openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt`,
then paste it on one line with newlines escaped as `\n`):

```properties
clientAssertionPrivateKeyPem=-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----\n
```

If both are set, the private_key_jwt assertion takes precedence. If neither is set, both clients
stay public and Browser Sign-In/Direct Authentication behave exactly as before. See the two
`configureClientAuthentication` overloads — one for `OAuth2ClientBuilder`, one for
`DirectAuthenticationFlowBuilder` — in `src/commonMain/.../platform/PlatformClientAuthentication.kt`
and its two platform actuals for how each platform applies this — the two platforms get there very
differently:

*   **Desktop** (`src/jvmMain`) reads `local.properties` directly **at runtime**, deliberately not
    via the `AppConfig` pattern used for the rest of this sample's config, so the secret is never
    baked into a build artifact.
*   **Android** (`src/androidMain`) has no access to the developer machine's `local.properties` at
    runtime, so it reads the same two values from `AppConfig` instead — baked into the APK at
    build time by the `generateAppConfig` Gradle task, the same way `local.properties`'s
    non-secret values (issuer, client ID, etc.) already are.

On both platforms, the private_key_jwt path registers a `ClientAssertionProvider` rather than a
static assertion string: the SDK invokes it fresh for every client-authenticated request — every
token/PAR request for `OAuth2ClientBuilder`, and every token/challenge/oob-authenticate/
primary-authenticate request (including each iteration of an OOB poll) for
`DirectAuthenticationFlowBuilder` — so each signed JWT gets a unique `jti` and an `aud` scoped to
the exact endpoint being called — required by
[Okta's client authentication guide](https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth),
which only allows a given `jti` to be used once.

> **SECURITY**: This exists only to make the confidential-client and PAR code paths easy to try
> locally, on either platform. A client secret or private key must **never** ship inside a mobile
> app, desktop app, or any other binary distributed to end users — anything embedded in a shipped
> artifact can be extracted from it, no matter how it's obfuscated or which of the two mechanisms
> above put it there. Confidential-client authentication only makes sense for a client that can
> actually keep a secret, such as a backend service. For a real deployment, load the secret from a
> proper secrets manager or KMS/HSM-backed signer (e.g. AWS Secrets Manager, HashiCorp Vault,
> Google Secret Manager, or your cloud provider's KMS for a private_key_jwt signer) — never from a
> checked-in or checked-out properties file. Keep `local.properties` out of version control (it
> already is, via `.gitignore`) and out of any CI build artifact.
>
> **For an enterprise-managed mobile deployment** (not a public app-store app): an MDM's managed
> app configuration (Android Enterprise managed configurations, or an iOS/iPadOS managed app
> configuration) can push the secret to the device at runtime instead of baking it into the
> APK/IPA, and lets it be rotated or revoked centrally without shipping a new build. This only
> raises the bar, though — it doesn't remove the exposure the way a server-side secret does. The
> secret still ends up in the app's sandbox on an end-user (if corporate-owned) device and can
> still be extracted by an attacker who compromises that device.

### Cross App Access (local testing only)

Cross App Access requires an **administrator-configured trust relationship** between your identity provider org and a separate resource app org — this is not something either sample app can set up for you. In your Okta Admin Console (or the resource org's), configure the trust relationship and the resource app per the
[Cross App Access documentation](https://developer.okta.com/docs/concepts/xaa/) (see also the [requesting app token exchange guide](https://developer.okta.com/docs/guides/xaa-request-token-ex/openidconnect/main/)), then note the resource app's issuer (or authorization server ID) and client ID — scope is entered later, in the app itself, not configured here. Only the **resource app target** needs a confidential-client credential (a client secret or `private_key_jwt`) — this SDK enforces it there. The dedicated IdP app below does not: Okta's own "AI agent" registration explicitly supports a credential-less "Client ID only" requesting app for clients that can't store a secret, so a credential is optional there. **Exception**: if your resource app target is a custom authorization server in the *same* org as the IdP app, the target reuses the IdP's client ID and credential instead of having its own — see [Same-org custom authorization server: extra requirements](#same-org-custom-authorization-server-extra-requirements) below.

#### The requesting app (IdP) identity is separate from the rest of this sample

Cross App Access's ID-JAG exchange must always be submitted to the org's own authorization server (`/oauth2/v1/token`), never a custom one (`/oauth2/{authorizationServerId}/token`) — Okta's own documentation calls out `/oauth2/default/` by name as unsupported here. This sample's primary `issuer`/`authorizationServerId`/`clientId` are shared by every other flow (Resource Owner, Browser Sign-In, PAR, etc.) and may legitimately point at a custom authorization server, which would break Cross App Access if it reused that same client. So Cross App Access has its **own, independent requesting-app identity** — its own Okta app registration, its own `OAuth2Client`, and its own dedicated Browser Sign-In — configured through `xaaIdpIssuer`/`xaaIdpClientId` below, always built without an `authorizationServerId`. This also mirrors how a real Cross App Access requesting app works: it's registered as its own "AI agent" app integration in Okta, separate from any other OIDC app.

Register a **second** app integration in Okta for this (an OIDC app with the Authorization Code grant enabled works, since this sample's IdP flow is just Browser Sign-In against it), and register the *same* `signInRedirectUri`/`desktopSignInRedirectUri` value from [OAuth2 Flows](#oauth2-flows) above as one of its redirect URIs too — the redirect URI is a per-call value, not a per-app-variant one, so the same registered URI can safely serve both app registrations.

Add the following to `local.properties`:

```properties
xaaIdpIssuer=<idp_app_issuer>
xaaIdpClientId=<idp_app_client_id>
```

`xaaIdpIssuer` and `xaaIdpClientId` are both required — the Cross App Access menu entry shows a "not configured" message until they, plus the resource app target keys below, are set. A credential is **not** required here: Okta's own "AI agent" registration explicitly supports registering this app as "Client ID only" (a public client with no secret), recommended for clients that can't store one — this sample included. If you'd rather register it as a confidential client (Okta's "Client secret" or "Public/private key" options), this sample supports that too — set one of:

```properties
xaaIdpClientSecret=<idp_app_client_secret>
```

or, for `private_key_jwt`:

```properties
xaaIdpClientAssertionPrivateKeyPem=-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----\n
```

Set at most one of the two — whichever is present is applied to the IdP client; leaving both unset builds it as a public client.

#### The resource app target

`xaaTargetIssuer` and `xaaTargetAuthorizationServerId` name the resource app's authorization server, similarly to `issuer`/`authorizationServerId` above — but they aren't the same pair applied to a different org, because they cover three distinct cases, not one:

*   `xaaTargetIssuer` alone — the resource app is a **different Okta org's default authorization server**. Must be a bare origin (scheme + host, no path), e.g. `https://resource-org.okta.com`. A path here is silently discarded (the SDK derives the effective issuer from scheme/host/port only) — see the next case for a custom server on that org.
*   `xaaTargetAuthorizationServerId` alone — a **custom authorization server in your own org**, i.e. the same host as your primary `issuer` above. There's no separate origin to set here because it reuses your primary client's host — this is exactly `forAuthorizationServerId`'s behavior, unrelated to `xaaTargetIssuer`. **This case has extra requirements the other two don't — see [Same-org custom authorization server: extra requirements](#same-org-custom-authorization-server-extra-requirements) below before using it.**
*   Both set — a **custom authorization server on the different org** named by `xaaTargetIssuer`. The two combine exactly like the primary `issuer`+`authorizationServerId` do: `xaaTargetAuthorizationServerId` is appended as a path onto `xaaTargetIssuer`'s origin.

Add the following to `local.properties`, leaving out whichever of the two you don't need:

```properties
xaaTargetIssuer=<resource_app_issuer_or_leave_blank>
xaaTargetAuthorizationServerId=<resource_app_authorization_server_id_or_leave_blank>
xaaTargetClientId=<resource_app_client_id>
xaaTargetResource=<resource_indicator_or_leave_blank>
xaaTargetClientSecret=<resource_app_client_secret>
```

or, for `private_key_jwt` (same key format as [Confidential client authentication](#confidential-client-authentication-local-testing-only) above):

```properties
xaaTargetClientAssertionPrivateKeyPem=-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----\n
```

Also set exactly one of `xaaTargetClientSecret` or `xaaTargetClientAssertionPrivateKeyPem` for the target's credential. All six keys are optional as a group: if none are set, the Cross App Access menu entry shows a "not configured" message instead of failing, and the rest of the sample is unaffected.

There is no `xaaTargetScopes` config key: scope is entered in the app itself, in the Requested Scopes field on the Cross App Access screen — taking precedence over any scope the SDK's `CrossAppAccessTarget` might otherwise default to. The org authorization server rejects a scope-less exchange, so this field must be non-blank before the exchange/step-by-step buttons become enabled. It must also be a **custom, resource-specific scope defined on the target's own authorization server** (e.g. `chat.read`) — standard OIDC scopes like `openid`/`profile`/`email`/`offline_access` are valid for the dedicated sign-in below, but the org authorization server rejects them at this step with `The following scopes are not allowed for this request`. This applies to every case above, not just the same-org one.

##### Same-org custom authorization server: extra requirements

If your resource app target is a custom authorization server **in the same Okta org** as the `xaaIdp*` app above (the `xaaTargetAuthorizationServerId`-alone case), a few things differ from the other two cases:

*   **`xaaTargetClientId` must be the same value as `xaaIdpClientId`**, and the target's credential must be the *same* credential as the IdP's (`xaaIdpClientSecret`/`xaaIdpClientAssertionPrivateKeyPem`), not an independently-registered one. Okta requires the IdP and target to share a single client registration whenever they're in the same org. Using a separate target client/credential here fails with `The 'client_id' in the JWT Bearer Grant must match the 'client_id' used to authenticate the client`.
*   In the Okta Admin Console, this needs a Resource Connection created directly against the **authorization server** (Directory → AI agents → your agent → Resource connections → Add → select the authorization server itself as the resource) — not the more discoverable "Application instance" option, which computes the wrong issuer for audience matching in this case and fails with `Token Exchange requests must include a valid audience of the authorization server`.
*   **This exact configuration — a custom authorization server as an XAA resource in the same org as the requesting app — may not be fully supported by Okta yet.** Verify current support for this specific setup before relying on it, and expect it to need reconfiguration if Okta's supported shape for it changes.

The other two cases above (a different org's default AS, or a custom AS on a different org) don't have any of these caveats — the target genuinely has its own independent client registration and credential there.

Cross App Access has its **own dedicated Browser Sign-In**, separate from every other flow's session — tap **"Sign in for Cross App Access"** the first time you open the Cross App Access screen. Every subject kind the SDK accepts (identity token, access token, and refresh token) is available to pick from in both modes; which one your resource app's authorization server actually accepts depends on its own configuration.

> **SECURITY**: Exactly like the confidential-client keys above, `xaaTargetClientSecret`/`xaaTargetClientAssertionPrivateKeyPem` and `xaaIdpClientSecret`/`xaaIdpClientAssertionPrivateKeyPem` exist only to make this demonstration easy to try locally. Neither credential is ever displayed or logged anywhere in this sample — only its presence (a boolean) is ever read back. The same shipped-artifact and secrets-manager guidance from [Confidential client authentication](#confidential-client-authentication-local-testing-only) applies here without exception.

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
*   **Cross App Access** — Sign in with its own dedicated Browser Sign-In, then use that session to obtain a scoped access token for a separate resource app. Choose one-action exchange or step-by-step mode, and which part of the session to use as the subject.

### OAuth2 Flow Notes

*   **Browser Sign In** requires `signInRedirectUri` to be configured in `local.properties` and registered in your Okta app's redirect URIs.
*   **Browser Sign In + PAR** requires using a custom authorization server (`authorizationServerId` set, such as `default`) with PAR enabled in Okta.
*   **Session Token Flow** requires `signInRedirectUri` for the server-side redirect. To obtain a session token, use the [Okta Authentication API](https://developer.okta.com/docs/reference/api/authn/) (e.g., via `curl` or another tool).
*   **Token Exchange** requires tokens from a prior authentication that included the `device_sso` scope to obtain a device secret.
*   **Device Authorization** requires the `urn:ietf:params:oauth:grant-type:device_code` grant type enabled on your authorization server.
*   **Cross App Access** requires both the `xaaIdp*` and `xaaTarget*` keys from [Cross App Access (local testing only)](#cross-app-access-local-testing-only), the `xaaIdp*` app's redirect URI registration, and an administrator-configured trust relationship — without all of these, the menu entry shows a "not configured" message rather than starting an exchange. Like the rest of this sample, some screens log full token values to the console as a developer-only convenience; the confidential-client credentials are the only values never displayed or logged.
