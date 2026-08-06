# Okta Auth Java CLI Sample

A pure Java CLI application demonstrating two Okta authentication approaches:

1. **Direct Authentication** — the `okta-direct-auth` CompletableFuture API (password, OTP, MFA, SSPR)
2. **OAuth2 flows** — all five OAuth2 standard flows via the `oauth2` module's Java-friendly wrappers

## Table of Contents

- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
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

## Org Setup

Org setup is identical to the current Direct Auth sample except for the OAuth2 additions:

1. **Register the loopback redirect URI** `http://localhost:8080/callback` (or your configured `desktopSignInRedirectUri`) as a Sign-in redirect URI on the Okta app.
2. **Enable the required grant types** on the Okta app for the flows you want to demo:

   | Flow | Required grant type |
   |---|---|
   | Resource Owner Password | `password` |
   | Device Authorization | `urn:ietf:params:oauth:grant-type:device_code` |
   | Browser Sign-In | `authorization_code` (PKCE) |
   | Token Exchange | `urn:ietf:params:oauth:grant-type:token-exchange` |
   | Session Token | `authorization_code` (PKCE) + session token support |

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
[5] Push (Okta Verify)
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
- **MFA** — OTP, SMS, Voice, and Okta Verify push
- **Device transfer with binding code** — Okta Verify number challenge
- **Self-service password recovery (SSPR)** — Reset password via the MyAccount API

### OAuth2 Flows (Java-friendly wrappers)
- **Resource Owner Password** — `ResourceOwnerFlow`: sign in with username and password
- **Device Authorization** — `DeviceAuthorizationFlow`: device-code flow with polling
- **Browser Sign-In** — `AuthorizationCodeFlow` + `LocalhostBrowserRedirectHandler`: opens the system browser and captures the loopback redirect
- **Token Exchange** — `TokenExchangeFlow`: exchange an existing ID token + device secret
- **Session Token** — `SessionTokenFlow`: exchange a legacy session token

### Shared
- **JWT decoding** — View token claims with `--format=decoded`
- **Username persistence** — Direct Auth mode remembers your last username across sessions

## Tests

```bash
./gradlew :okta-direct-auth-java-cli-sample:test
```

## Known Limitations

### Browser Sign-In requires a desktop environment

The `LocalhostBrowserRedirectHandler` uses `java.awt.Desktop` to open the system browser. It is not supported in headless server environments. If the configured loopback port is already in use, the CLI reports a clear error.

### Concurrent instances and username persistence

The CLI persists the last-used username to `~/.okta-direct-auth-cli/preferences.properties` using `java.util.Properties`. If multiple CLI instances run simultaneously and save a username at the same time, the file uses a last-writer-wins strategy with no locking. This means one instance's write may overwrite another's. In practice this is unlikely to cause issues since the stored value is only a convenience default shown at the username prompt, and the user can always type a different username.
