# Okta Direct Auth Java CLI Sample

A pure Java CLI application demonstrating the `okta-direct-auth` CompletableFuture API for direct authentication against an Okta org.

## Prerequisites

- JDK 11 or later
- An Okta org configured for [Direct Authentication](https://developer.okta.com/docs/guides/configure-direct-auth-grants/) with an OAuth 2.0 client

## Configuration

Configuration values are resolved in this order:

1. **CLI arguments** (`--issuer=`, `--clientId=`, `--authorizationServerId=`)
2. **`local.properties`** in the project root (keys: `issuer`, `clientId`, `authorizationServerId`)
3. **Interactive prompt** (if neither of the above provides a value)

Example `local.properties`:

```properties
issuer=https://your-org.okta.com
clientId=0oa...
authorizationServerId=default
```

## Build

```bash
./gradlew :okta-direct-auth-java-cli-sample:build
```

## Run

Using the Gradle `run` task:

```bash
./gradlew :okta-direct-auth-java-cli-sample:run
```

With arguments:

```bash
./gradlew :okta-direct-auth-java-cli-sample:run --args="--issuer=https://your-org.okta.com --clientId=0oa... --format=decoded --verbose"
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
| `--format=raw\|decoded` | Token display format (default: `raw`) |
| `--verbose` | Enable debug logging to stderr |
| `--version`, `-v` | Show version and exit |
| `--help`, `-h` | Show help and exit |

## Usage Example

### Password sign-in

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

With `--format=decoded`, the success output shows parsed JWT claims instead of raw tokens:

```
=== Authentication Successful ===
Issuer:  https://your-org.okta.com/oauth2/default
Subject: 00u1example
Name:    Test User
Email:   user@example.com

Press Enter to sign out...
```

### MFA sign-in

When the Okta org requires a second factor, the CLI prompts for an MFA method after the primary factor succeeds:

```
MFA Required. Select method:
[1] OTP
[2] SMS
[3] Voice
[4] Push (Okta Verify)
[0] Back to menu
Select option: 1

Enter verification code (or [0] to go back):
Code: 123456

=== Authentication Successful ===
...
```

## Features

- **Password authentication** — Sign in with username and password
- **MFA** — OTP, SMS, Voice, and Okta Verify push
- **Device transfer with binding code** — Okta Verify number challenge
- **Self-service password recovery (SSPR)** — Reset password via the MyAccount API
- **JWT decoding** — View token claims with `--format=decoded`
- **Username persistence** — Remembers your last username across sessions

## Tests

```bash
./gradlew :okta-direct-auth-java-cli-sample:test
```

## Known Limitations

### Concurrent instances and username persistence

The CLI persists the last-used username to `~/.okta-direct-auth-cli/preferences.properties` using `java.util.Properties`. If multiple CLI instances run simultaneously and save a username at the same time, the file uses a last-writer-wins strategy with no locking. This means one instance's write may overwrite another's. In practice this is unlikely to cause issues since the stored value is only a convenience default shown at the username prompt, and the user can always type a different username.
