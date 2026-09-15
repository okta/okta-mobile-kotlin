# Okta Auth Java CLI Sample

A pure Java CLI application demonstrating two Okta authentication approaches:

1. **Direct Authentication** — the `okta-direct-auth` CompletableFuture API (password, OTP, MFA, SSPR)
2. **OAuth2 flows** — all six OAuth2 standard flows via the `oauth2` module's Java-friendly wrappers, including Cross App Access

## Table of Contents

- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
  - [Confidential client authentication (local testing only)](#confidential-client-authentication-local-testing-only)
  - [Cross App Access (local testing only)](#cross-app-access-local-testing-only)
- [Org Setup](#org-setup)
- [Build](#build)
- [Run](#run)
- [CLI Options](#cli-options)
- [Usage Examples](#usage-examples)
  - [Mode selection](#mode-selection)
  - [Direct Authentication — password sign-in](#direct-authentication--password-sign-in)
  - [OAuth2 — Resource Owner Password](#oauth2--resource-owner-password)
  - [OAuth2 — Device Authorization](#oauth2--device-authorization)
  - [OAuth2 — Browser Sign-In](#oauth2--browser-sign-in)
  - [OAuth2 — Cross App Access](#oauth2--cross-app-access)
- [Features](#features)
  - [Direct Authentication](#direct-authentication)
  - [OAuth2 Flows (Java-friendly wrappers)](#oauth2-flows-java-friendly-wrappers)
  - [Shared](#shared)
- [Tests](#tests)
- [Known Limitations](#known-limitations)
  - [Browser Sign-In requires a desktop environment](#browser-sign-in-requires-a-desktop-environment)
  - [Concurrent instances and username persistence](#concurrent-instances-and-username-persistence)

## Prerequisites

- JDK 11 or later
- An Okta org with an OAuth 2.0 OIDC app configured for the flows you want to demo:
  - **Direct Authentication**: enable [Direct Authentication grants](https://developer.okta.com/docs/guides/configure-direct-auth-grants/)
  - **OAuth2 flows**: enable the matching grant types (see [Org Setup](#org-setup) below)

## Configuration

Configuration values are resolved in this order:

1. **CLI arguments** (see [CLI Options](#cli-options))
2. **`local.properties`** in the project root
3. **Interactive prompt** (if neither of the above provides a value)

Example `local.properties` (same single Okta app for both modes):

```properties
issuer=https://your-org.okta.com
clientId=0oa...
authorizationServerId=default
desktopSignInRedirectUri=http://localhost:8080/callback
```

`desktopSignInRedirectUri` defaults to `http://localhost:8080/callback` if not set. It is required for Browser Sign-In and Session Token flows.

### Confidential client authentication (local testing only)

Both the Direct Authentication and Browser Sign-In (OAuth2) builders are public clients by
default — the correct and only recommended setup for a distributed CLI or desktop app. To try
either against a **confidential** client (for example, to exercise PAR with `private_key_jwt` or
`client_secret` authentication, or to run Direct Authentication as a confidential client),
`ClientAuthentication` reads one of the following from `local.properties`
**at runtime** — never bake either of these into `AppConfig` or any other compiled constant:

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
stay public and Direct Authentication/Browser Sign-In behave exactly as before.

The private_key_jwt path registers a `ClientAssertionProvider` rather than a static assertion
string: the SDK invokes it fresh for every client-authenticated request — every token/PAR request
for the OAuth2 builder, and every token/challenge/oob-authenticate/primary-authenticate request
(including each iteration of an OOB poll) for the Direct Authentication builder — so each signed
JWT gets a unique `jti` and an `aud` scoped to the exact endpoint being called — required by
[Okta's client authentication guide](https://developer.okta.com/docs/api/openapi/okta-oauth/guides/client-auth),
which only allows a given `jti` to be used once.

> **SECURITY**: This exists only to make the confidential-client and PAR code paths easy to try
> locally. A client secret or private key must **never** ship inside a mobile app, desktop app, or
> any other binary distributed to end users — anything embedded in a shipped artifact can be
> extracted from it, no matter how it's obfuscated. Confidential-client authentication only makes
> sense for a client that can actually keep a secret, such as a backend service. For a real
> deployment, load the secret from a proper secrets manager or KMS/HSM-backed signer (e.g. AWS
> Secrets Manager, HashiCorp Vault, Google Secret Manager, or your cloud provider's KMS for a
> private_key_jwt signer) — never from a checked-in or checked-out properties file. Keep
> `local.properties` out of version control (it already is, via `.gitignore`) and out of any CI
> build artifact.

### Cross App Access (local testing only)

Cross App Access requires an **administrator-configured trust relationship** between your identity provider org and a separate resource app org — this CLI cannot set that up for you. Configure the trust relationship and the resource app per the
[Cross App Access documentation](https://developer.okta.com/docs/concepts/xaa/) (see also the [requesting app token exchange guide](https://developer.okta.com/docs/guides/xaa-request-token-ex/openidconnect/main/)), then note the resource app's issuer (or authorization server ID) and client ID — scope is entered later, at the CLI prompt, not configured here. Only the **resource app target** needs a confidential-client credential (a client secret or `private_key_jwt`) — this SDK enforces it there. The dedicated IdP app below does not: Okta's own "AI agent" registration explicitly supports a credential-less "Client ID only" requesting app for clients that can't store a secret, so a credential is optional there. **Exception**: if your resource app target is a custom authorization server in the *same* org as the IdP app, the target reuses the IdP's client ID and credential instead of having its own — see [Same-org custom authorization server: extra requirements](#same-org-custom-authorization-server-extra-requirements) below.

#### The requesting app (IdP) identity is separate from the rest of this CLI

Cross App Access's ID-JAG exchange must always be submitted to the org's own authorization server (`/oauth2/v1/token`), never a custom one (`/oauth2/{authorizationServerId}/token`) — Okta's own documentation calls out `/oauth2/default/` by name as unsupported here. This CLI's primary `issuer`/`authorizationServerId`/`clientId` are shared by every other flow (Resource Owner, Browser Sign-In, PAR, etc.) and may legitimately point at a custom authorization server, which would break Cross App Access if it reused that same client. So Cross App Access has its **own, independent requesting-app identity** — its own Okta app registration, its own `OAuth2Client`, and its own dedicated Browser Sign-In — configured through `xaaIdpIssuer`/`xaaIdpClientId` below, always built without an `authorizationServerId`. This also mirrors how a real Cross App Access requesting app works: it's registered as its own "AI agent" app integration in Okta, separate from any other OIDC app.

Register a **second** app integration in Okta for this (an OIDC app with the Authorization Code grant enabled works, since this CLI's IdP flow is just Browser Sign-In against it), and register the *same* loopback redirect URI (`desktopSignInRedirectUri`, e.g. `http://localhost:8080/callback`) as one of its redirect URIs too — the redirect URI is a per-call value, not a per-app-registration one, so the same registered URI can safely serve both app registrations.

`AppConfig` bakes in the non-secret pieces:

```properties
xaaIdpIssuer=<idp_app_issuer>
xaaIdpClientId=<idp_app_client_id>
```

`xaaIdpIssuer` and `xaaIdpClientId` are both required — the Cross App Access menu entry reports what's missing until they, plus the resource app target keys below, are set. A credential is **not** required here: Okta's own "AI agent" registration explicitly supports registering this app as "Client ID only" (a public client with no secret), recommended for clients that can't store one — this CLI included. If you'd rather register it as a confidential client (Okta's "Client secret" or "Public/private key" options), this CLI supports that too — the credential is read **at runtime** by `ClientAuthentication`, the same way `clientSecret`/`clientAssertionPrivateKeyPem` are above — never baked into `AppConfig`:

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
```

The resource app credential is read **at runtime** by `ClientAuthentication`, the same way `clientSecret`/`clientAssertionPrivateKeyPem` are above — never baked into `AppConfig`:

```properties
xaaTargetClientSecret=<resource_app_client_secret>
```

or, for `private_key_jwt` (same key format as [Confidential client authentication](#confidential-client-authentication-local-testing-only) above):

```properties
xaaTargetClientAssertionPrivateKeyPem=-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----\n
```

Also set exactly one of `xaaTargetClientSecret` or `xaaTargetClientAssertionPrivateKeyPem` for the target's credential. All six keys are optional as a group: if none are set, the Cross App Access menu entry reports what's missing instead of failing, and the rest of the CLI is unaffected.

There is no `xaaTargetScopes` config key: the CLI prompts for scope right after subject-kind selection, taking precedence over any scope the SDK's `CrossAppAccessTarget` might otherwise default to. The org authorization server rejects a scope-less exchange, so leaving the prompt blank surfaces as an error through the normal outcome screen rather than being re-prompted. It must also be a **custom, resource-specific scope defined on the target's own authorization server** (e.g. `chat.read`) — standard OIDC scopes like `openid`/`profile`/`email`/`offline_access` are valid for the dedicated sign-in below, but the org authorization server rejects them at this step with `The following scopes are not allowed for this request`. This applies to every case above, not just the same-org one.

##### Same-org custom authorization server: extra requirements

If your resource app target is a custom authorization server **in the same Okta org** as the `xaaIdp*` app above (the `xaaTargetAuthorizationServerId`-alone case), a few things differ from the other two cases:

*   **`xaaTargetClientId` must be the same value as `xaaIdpClientId`**, and the target's credential must be the *same* credential as the IdP's (`xaaIdpClientSecret`/`xaaIdpClientAssertionPrivateKeyPem`), not an independently-registered one. Okta requires the IdP and target to share a single client registration whenever they're in the same org. Using a separate target client/credential here fails with `The 'client_id' in the JWT Bearer Grant must match the 'client_id' used to authenticate the client`.
*   In the Okta Admin Console, this needs a Resource Connection created directly against the **authorization server** (Directory → AI agents → your agent → Resource connections → Add → select the authorization server itself as the resource) — not the more discoverable "Application instance" option, which computes the wrong issuer for audience matching in this case and fails with `Token Exchange requests must include a valid audience of the authorization server`.
*   **This exact configuration — a custom authorization server as an XAA resource in the same org as the requesting app — may not be fully supported by Okta yet.** Verify current support for this specific setup before relying on it, and expect it to need reconfiguration if Okta's supported shape for it changes.

The other two cases above (a different org's default AS, or a custom AS on a different org) don't have any of these caveats — the target genuinely has its own independent client registration and credential there.

Cross App Access has its **own dedicated Browser Sign-In**, separate from every other flow's session — the CLI opens a browser for it the first time you select Cross App Access from the menu. Every subject kind the SDK accepts (identity token, access token, and refresh token) is available to pick from in both modes; which one your resource app's authorization server actually accepts depends on its own configuration.

> **SECURITY**: Exactly like the confidential-client keys above, `xaaTargetClientSecret`/`xaaTargetClientAssertionPrivateKeyPem` and `xaaIdpClientSecret`/`xaaIdpClientAssertionPrivateKeyPem` exist only to make this demonstration easy to try locally, and the same shipped-artifact and secrets-manager guidance applies without exception. Neither credential is ever displayed or logged anywhere in this sample — only its presence (a boolean) is ever read back. This is distinct from this CLI's existing `--format=raw` default, which does print full access/ID tokens to the console as a developer-only convenience — that behavior is unchanged and does not apply to either credential.

## Org Setup

Org setup is identical to the current Direct Auth sample except for the OAuth2 additions:

1. **Register the loopback redirect URI** `http://localhost:8080/callback` (or your configured `desktopSignInRedirectUri`) as a Sign-in redirect URI on the Okta app. For Cross App Access, also register this same URI on the **second, dedicated IdP app** described in [Cross App Access (local testing only)](#cross-app-access-local-testing-only) — it's a different app registration from the primary one.
2. **Enable the required grant types** on the Okta app for the flows you want to demo:

   | Flow | Required grant type |
   |---|---|
   | Resource Owner Password | `password` |
   | Device Authorization | `urn:ietf:params:oauth:grant-type:device_code` |
   | Browser Sign-In | `authorization_code` (PKCE) |
   | Token Exchange | `urn:ietf:params:oauth:grant-type:token-exchange` |
   | Session Token | `authorization_code` (PKCE) + session token support |
   | Cross App Access | requires an administrator-configured trust relationship — see [Cross App Access (local testing only)](#cross-app-access-local-testing-only) |

3. **Enable PAR on a custom authorization server** for Browser Sign-In PAR demos:
   - Use a custom authorization server (for example, `default`) and keep `authorizationServerId=default` in configuration.
   - In Okta Admin, open **Security > API > Authorization Servers > _your server_ > Settings**, then enable PAR.
   - Confirm discovery metadata includes `pushed_authorization_request_endpoint`. If `require_pushed_authorization_requests=true`, Browser Sign-In requires PAR and will fail when PAR cannot be completed.

> **Note**: Tokens are displayed in-memory for demonstration purposes only and are not persisted.

## Build

```bash
./gradlew :okta-direct-auth-java-cli-sample:build
```

## Run

Using the Gradle `run` task:

```bash
./gradlew :okta-direct-auth-java-cli-sample:run
```

With arguments (pre-select mode and format):

```bash
./gradlew :okta-direct-auth-java-cli-sample:run --args="--mode=oauth2 --format=decoded"
./gradlew :okta-direct-auth-java-cli-sample:run --args="--issuer=https://your-org.okta.com --clientId=0oa... --mode=direct"
```

Using the distribution archive:

```bash
./gradlew :okta-direct-auth-java-cli-sample:distZip
cd okta-direct-auth-java-cli-sample/build/distributions
unzip okta-direct-auth-cli.zip
./okta-direct-auth-cli/bin/okta-direct-auth-cli
```

## CLI Options

| Option | Description |
|---|---|
| `--issuer=URL` | Okta issuer URL |
| `--clientId=ID` | OAuth 2.0 client ID |
| `--authorizationServerId=ID` | Authorization server ID (e.g., `default`) |
| `--desktopSignInRedirectUri=URL` | Loopback redirect URI for OAuth2 redirect-based flows (default: `http://localhost:8080/callback`) |
| `--mode=direct\|oauth2` | Pre-select demonstration mode (default: prompt at startup) |
| `--format=raw\|decoded` | Token display format (default: `raw`) |
| `--verbose`, `-v` | Enable debug logging to stderr |
| `--version` | Show version and exit |
| `--help`, `-h` | Show help and exit |

## Usage Examples

### Mode selection

On startup (without `--mode`), the CLI shows a top-level menu:

```
=== Okta Auth CLI ===
[1] Direct Authentication
[2] OAuth2 Flows
[3] Exit
Select option:
```

### Direct Authentication — password sign-in

```
=== Okta Direct Auth CLI ===
[1] Sign In
[2] Forgot Password
[3] Exit
Select option: 1

Enter username (or [0] to go back):
Username [user@example.com]: user@example.com

Select authentication method:
[1] Password
[2] OTP
[3] SMS
[4] Voice
[5] Email
[6] Push (Okta Verify)
[0] Back
Select option: 1

Enter password (or [0] to go back):
Password:

=== Authentication Successful ===
Access Token:
eyJraWQiOiJ...

ID Token:
eyJraWQiOiJ...

Press Enter to sign out...
```

### OAuth2 — Resource Owner Password

```
=== OAuth2 Flows ===
[1] Resource Owner Password
[2] Device Authorization
[3] Browser Sign-In (Auth Code + PKCE)
[4] Token Exchange
[5] Session Token
[0] Back
Select option: 1

Username: user@example.com
Password:

=== Authentication Successful ===
Access Token:
eyJraWQiOiJ...
...
Press Enter to continue...
```

### OAuth2 — Device Authorization

```
Select option: 2

=== Device Authorization ===
Visit: https://your-org.okta.com/activate
Enter code: ABCD-1234
Code expires in 300 seconds. Waiting for approval...

=== Authentication Successful ===
...
```

### OAuth2 — Browser Sign-In

```
Select option: 3

Opening browser for sign-in. Waiting for redirect...
# System browser opens to Okta sign-in page.
# After sign-in, the CLI captures the loopback redirect automatically.
# If PAR is enabled on the custom auth server, Browser Sign-In uses request_uri automatically.

=== Authentication Successful ===
...
```

### OAuth2 — Cross App Access

```
Select option: 6

=== Cross App Access ===
Resource App Target: https://resource-org.okta.com

Cross App Access needs its own signed-in session, separate from the rest
of this CLI — sign in below.
Opening browser for sign-in. Waiting for redirect...
# System browser opens against the dedicated xaaIdp* app; after sign-in, the CLI
# captures the loopback redirect automatically, same as OAuth2 Browser Sign-In.

Select subject: [1] Identity token [2] Access token [3] Refresh token: 1

=== Scope ===
Requested scopes (space-separated): chat.read

[1] One-action exchange
[2] Step-by-step (inspect the ID-JAG, redeem separately)
[0] Back
Select option: 1

=== Resource Access Token ===
This token is for the resource app — not your signed-in app.
Granted Scope: chat.read
Token Type: Bearer
Expires In: 3600s
```

With `--format=decoded`, success output shows parsed JWT claims:

```
=== Authentication Successful ===
Issuer:  https://your-org.okta.com/oauth2/default
Subject: 00u1example
Name:    Test User
Email:   user@example.com

Press Enter to continue...
```

## Features

### Direct Authentication
- **Password authentication** — Sign in with username and password
- **MFA** — OTP, SMS, Voice, Email, and Okta Verify push
- **Device transfer with binding code** — Okta Verify number challenge
- **Self-service password recovery (SSPR)** — Reset password via the MyAccount API

### OAuth2 Flows (Java-friendly wrappers)
- **Resource Owner Password** — `ResourceOwnerFlow`: sign in with username and password
- **Device Authorization** — `DeviceAuthorizationFlow`: device-code flow with polling
- **Browser Sign-In** — `AuthorizationCodeFlow` + `LocalhostBrowserRedirectHandler`: opens the system browser and captures the loopback redirect
- **Token Exchange** — `TokenExchangeFlow`: exchange an existing ID token + device secret
- **Session Token** — `SessionTokenFlow`: exchange a legacy session token
- **Cross App Access** — `CrossAppAccessFlow`: sign in with its own dedicated Browser Sign-In (a requesting-app identity separate from the rest of the CLI) to obtain a scoped access token for a separate resource app; one-action exchange or step-by-step mode with a reusable ID-JAG, subject kind selectable (identity/access/refresh token)

### Shared
- **JWT decoding** — View token claims with `--format=decoded`
- **Username persistence** — Direct Auth mode remembers your last username across sessions

> **Note on raw token output**: With the default `--format=raw`, this CLI prints full access and ID token values to the console for every flow above, including the resource access token obtained via Cross App Access — this is an existing, unchanged developer-only convenience of this sample, not something Cross App Access introduces. The values Cross App Access never prints, in either format, are the IdP app's and the resource app's own client secrets or private keys (`xaaIdpClientSecret`/`xaaIdpClientAssertionPrivateKeyPem` and `xaaTargetClientSecret`/`xaaTargetClientAssertionPrivateKeyPem`) — only whether each is present.

## Tests

```bash
./gradlew :okta-direct-auth-java-cli-sample:test
```

## Known Limitations

### Browser Sign-In requires a desktop environment

The `LocalhostBrowserRedirectHandler` uses `java.awt.Desktop` to open the system browser. It is not supported in headless server environments. If the configured loopback port is already in use, the CLI reports a clear error.

### Concurrent instances and username persistence

The CLI persists the last-used username to `~/.okta-direct-auth-cli/preferences.properties` using `java.util.Properties`. If multiple CLI instances run simultaneously and save a username at the same time, the file uses a last-writer-wins strategy with no locking. This means one instance's write may overwrite another's. In practice this is unlikely to cause issues since the stored value is only a convenience default shown at the username prompt, and the user can always type a different username.
