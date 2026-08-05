[![Support](https://img.shields.io/badge/support-Developer%20Forum-blue.svg)][devforum]
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta Mobile Kotlin

Okta Mobile Kotlin is a Kotlin Multiplatform SDK for Okta authentication. The repository centers on KMP libraries for Android and JVM, with a few Android-only support modules and shared sample apps.

## Table of Contents

- [Documentation](#documentation)
- [Repository layout](#repository-layout)
- [Installation](#installation)
- [KMP migration](#kmp-migration)
- [Building locally](#building-locally)
- [Need help?](#need-help)
- [Contributing](#contributing)

## Documentation

New to the SDK? Start with `auth-foundation` (every library builds on it), then pick the flow
module that matches how you want users to sign in.

**Libraries**

- **[`auth-foundation/README.md`](auth-foundation/README.md)** — Start here. The core module every other library depends on: how to build an `OAuth2Client`, store and refresh credentials (immutable KMP snapshots via `TokenCredentialManager`, SQLCipher-encrypted Room storage on Android), use biometric-backed storage, and customize networking and rate-limit retries. Also holds the guide for migrating from the deprecated Android-only APIs to the KMP `*.kmp.*` packages.
- **[`oauth2/README.md`](oauth2/README.md)** — Reach for this when you drive a standard OAuth2 grant yourself: Resource Owner Password, Device Authorization, Authorization Code + PKCE, Token Exchange (Native SSO), Session Token, and Redirect End Session. Per-flow Kotlin `Result` examples plus Java `CompletableFuture` wrappers, and the Android-only → KMP migration guide.
- **[`web-authentication-ui/README.md`](web-authentication-ui/README.md)** — Use when you want browser-based sign-in/sign-out handled for you: launches a Chrome Custom Tab and wraps `oauth2`'s Authorization Code and Redirect End Session flows. Android-only.
- **[`okta-direct-auth/README.md`](okta-direct-auth/README.md)** — Choose this to build a fully native (no browser) sign-in UI on Okta's Direct Authentication API: password, OTP, out-of-band push/SMS/voice, WebAuthn/passkeys, MFA, and self-service password recovery. Covers the coroutine `StateFlow` API and the Java `CompletableFuture` API.
- **[`okta-idx-kotlin/README.md`](okta-idx-kotlin/README.md)** — Use when you need Okta Identity Engine's dynamic, policy-driven sign-in via the interaction code flow — the SDK walks you through server-defined remediations step by step. Documents `InteractionCodeFlow` (`start`/`resume`/`proceed`/`exchangeInteractionCodeForTokens`). Android-only.

**Sample apps**

- **[`app/README.md`](app/README.md)** — Android sample wiring `oauth2` + `web-authentication-ui` + `auth-foundation` together: browser sign-in (Authorization Code via Chrome Custom Tabs), Resource Owner Password, Device Authorization, and Token Exchange, with a post-login dashboard. Copy from here for a typical Android OAuth2 integration.
- **[`dynamic-app/README.md`](dynamic-app/README.md)** — Android sample for the `okta-idx-kotlin` interaction code flow: builds its sign-in UI dynamically from server remediations. Look here (rather than `app`) when integrating Identity Engine, and for the Cucumber/e2e test setup.
- **[`okta-direct-auth-shared/README.md`](okta-direct-auth-shared/README.md)** — The setup reference for the Compose Multiplatform direct-auth sample (Android + desktop runners): full Okta org configuration and `local.properties` for both the Direct Auth and OAuth2 flows the sample demonstrates.
- **[`okta-direct-auth-java-cli-sample/README.md`](okta-direct-auth-java-cli-sample/README.md)** — Pure-Java (no Kotlin) CLI exercising the `CompletableFuture` wrappers for both `okta-direct-auth` and all five `oauth2` flows. The reference to follow if you integrate from Java.

## Repository layout

### Publishable libraries

| Module | Target | Purpose |
| --- | --- | --- |
| `auth-foundation` | KMP (Android + JVM) | Core SDK — `OAuth2Client`, credential/token storage (encrypted Room on Android), and shared config every other module depends on |
| `oauth2` | KMP (Android + JVM) | Standard OAuth2 grant flows (Auth Code + PKCE, Device, Resource Owner, Token Exchange, Session Token, End Session) with Kotlin `Result` + Java `CompletableFuture` wrappers |
| [`web-authentication-ui`](web-authentication-ui/README.md) | Android | Browser-based OIDC sign-in/sign-out via Chrome Custom Tabs; wraps oauth2's Authorization Code and Redirect End Session flows |
| `legacy-token-migration` | Android | One-time migration of tokens from the legacy Okta OIDC Android SDK's `SessionClient` into a `Credential` |
| `okta-idx-kotlin` | Android | Okta Identity Engine interaction code flow — policy-driven, server-remediation sign-in |
| `okta-direct-auth` | KMP (Android + JVM) | Native (browser-less) Direct Authentication — password, OTP, OOB, WebAuthn, MFA, SSPR |
| `bom` | Java platform | Bill of materials aligning `auth-foundation`, `oauth2`, `web-authentication-ui`, `legacy-token-migration`, `okta-idx-kotlin`, and `okta-direct-auth` versions |

### Shared, sample, and internal modules

| Module | Purpose |
| --- | --- |
| `okta-direct-auth-shared` | Shared Compose Multiplatform UI + flow logic for the direct-auth sample; consumed by the Android and desktop runners. Its README holds the sample's Okta setup instructions |
| `app` | Android sample: browser sign-in plus Resource Owner, Device Authorization, and Token Exchange using `oauth2` / `web-authentication-ui` / `auth-foundation` |
| `okta-direct-auth-android-app` | Android (Compose) runner that hosts the `okta-direct-auth-shared` sample |
| `okta-direct-auth-desktop-app` | Desktop/JVM (Compose) runner that hosts the `okta-direct-auth-shared` sample |
| `okta-direct-auth-java-cli-sample` | Pure-Java CLI exercising the `CompletableFuture` wrappers for `okta-direct-auth` and `oauth2` |
| `session-token-sample` | Android sample exchanging an Okta Authn-API session token for tokens via `SessionTokenFlow` |
| `legacy-token-migration-sample` | Android sample demonstrating `legacy-token-migration` from the legacy Okta OIDC Android SDK |
| `dynamic-app` | Android sample for the `okta-idx-kotlin` interaction code flow (dynamic, remediation-driven UI) |
| `docs`, `test-helpers`, `test-utils`, `suppress-internal-dokka-plugin` | Internal only — Dokka API-docs aggregation (`docs`), MockWebServer/coroutine test fixtures (`test-helpers`, `test-utils`), and a Dokka plugin that hides `@InternalApi` from published docs |

## Installation

Use the BOM to keep versions aligned:

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:3.0.0"))
    implementation("com.okta.kotlin:auth-foundation")
    implementation("com.okta.kotlin:oauth2")
    implementation("com.okta.kotlin:web-authentication-ui")
    implementation("com.okta.kotlin:okta-direct-auth")
}
```

### Maven / plain Java projects

The BOM is a Gradle `java-platform`, which publishes as a standard Maven BOM — import it the same way:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.okta.kotlin</groupId>
            <artifactId>bom</artifactId>
            <version>3.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`auth-foundation`, `oauth2`, and `okta-direct-auth` are Kotlin Multiplatform (Android + JVM) artifacts. Their plain `<artifactId>` (e.g. `auth-foundation`) resolves to a Gradle-Module-Metadata-only umbrella with no compiled classes — Gradle uses it to auto-select a target, but Maven doesn't understand Gradle Module Metadata's variant resolution, so a Maven build needs to depend on the JVM-target artifact directly, with a `-jvm` suffix:

```xml
<dependencies>
    <dependency>
        <groupId>com.okta.kotlin</groupId>
        <artifactId>auth-foundation-jvm</artifactId>
    </dependency>
    <dependency>
        <groupId>com.okta.kotlin</groupId>
        <artifactId>oauth2-jvm</artifactId>
    </dependency>
    <dependency>
        <groupId>com.okta.kotlin</groupId>
        <artifactId>okta-direct-auth-jvm</artifactId>
    </dependency>
</dependencies>
```

See [`okta-direct-auth-java-cli-sample`](okta-direct-auth-java-cli-sample/README.md) for a working pure-Java example. `web-authentication-ui`, `legacy-token-migration`, and `okta-idx-kotlin` are Android-only (single-variant AARs) — use their plain `<artifactId>` as-is, but note they require an Android runtime/toolchain regardless of build tool.

## KMP migration

New code should use the KMP APIs in `com.okta.authfoundation.client.kmp.*`, `com.okta.authfoundation.credential.kmp.*`, and `com.okta.oauth2.kmp.*`. See [`auth-foundation/README.md`](auth-foundation/README.md) and [`oauth2/README.md`](oauth2/README.md) for the module-specific migration guides and examples.

## Building locally

```bash
./gradlew build
./gradlew spotlessCheck
./gradlew :auth-foundation:testAndroidHostTest :auth-foundation:jvmTest :oauth2:testAndroidHostTest :oauth2:jvmTest :okta-direct-auth:testAndroidHostTest :okta-direct-auth:jvmTest
```

## Need help?

This library uses semantic versioning and follows Okta's [Library Version Policy][okta-library-versioning].

- See the [CHANGELOG](CHANGELOG.md) for the most recent changes, and the [releases page][github-releases] for published versions.
- Ask questions on the [Okta Developer Forums][devforum].
- Report bugs or request features by opening an [issue][github-issues].

If you're migrating from the legacy [okta-oidc-android](https://github.com/okta/okta-oidc-android) SDK, see [migrate.md](migrate.md). (For moving from the deprecated Android-only APIs in this repo to the KMP APIs, see [KMP migration](#kmp-migration) above instead.)

## Contributing

We are happy to accept contributions and PRs! Please see the [contribution guide](CONTRIBUTING.md) to understand how to structure a contribution.

[devforum]: https://devforum.okta.com/
[github-issues]: https://github.com/okta/okta-mobile-kotlin/issues
[github-releases]: https://github.com/okta/okta-mobile-kotlin/releases
[okta-library-versioning]: https://developer.okta.com/code/library-versions
