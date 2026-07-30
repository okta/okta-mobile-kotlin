[![Support](https://img.shields.io/badge/support-Developer%20Forum-blue.svg)][devforum]
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Okta Mobile Kotlin

Okta Mobile Kotlin is a Kotlin Multiplatform SDK for Okta authentication. The repository centers on KMP libraries for Android and JVM, with a few Android-only support modules and shared sample apps.

## Table of Contents

- [Repository layout](#repository-layout)
- [Installation](#installation)
- [Documentation](#documentation)
- [KMP migration](#kmp-migration)
- [Building locally](#building-locally)

## Repository layout

### Publishable libraries

| Module | Target | Purpose |
| --- | --- | --- |
| `auth-foundation` | KMP (Android + JVM) | Core credential, token, and OAuth2 client APIs |
| `oauth2` | KMP (Android + JVM) | OAuth2 flow implementations and Java wrappers |
| `web-authentication-ui` | Android | Browser-based OIDC redirect UI |
| `legacy-token-migration` | Android | Migration helpers from legacy Okta SDKs |
| `okta-idx-kotlin` | Android | IDX interaction code flow |
| `okta-direct-auth` | KMP (Android + JVM) | Direct Authentication SDK |
| `bom` | Java platform | Bill of materials for version alignment |

### Shared, sample, and internal modules

| Module | Purpose |
| --- | --- |
| `okta-direct-auth-shared` | Shared Compose UI and flow logic for the direct-auth sample |
| `app` | Legacy Android sample app |
| `okta-direct-auth-android-app` | Android runner for the shared direct-auth sample |
| `okta-direct-auth-desktop-app` | Desktop runner for the shared direct-auth sample |
| `okta-direct-auth-java-cli-sample` | Java CLI sample for direct auth |
| `session-token-sample` | Sample app for session-token flows |
| `legacy-token-migration-sample` | Sample app for legacy token migration |
| `dynamic-app` | Dynamic sample app |
| `docs`, `test-helpers`, `test-utils`, `suppress-internal-dokka-plugin` | Internal tooling and support modules |

## Installation

Use the BOM to keep versions aligned:

```kotlin
dependencies {
    implementation(platform("com.okta.kotlin:bom:2.0.5"))
    implementation("com.okta.kotlin:auth-foundation")
    implementation("com.okta.kotlin:oauth2")
    implementation("com.okta.kotlin:web-authentication-ui")
}
```

## Documentation

- AuthFoundation core APIs, credential storage, and migration guidance: [`auth-foundation/README.md`](auth-foundation/README.md)
- OAuth2 flow examples, Java usage, and sample app notes: [`oauth2/README.md`](oauth2/README.md)
- Direct Authentication sample setup: [`okta-direct-auth-shared/README.md`](okta-direct-auth-shared/README.md)
- Android sample app: [`app/README.md`](app/README.md)
- Java CLI sample: [`okta-direct-auth-java-cli-sample/README.md`](okta-direct-auth-java-cli-sample/README.md)

## KMP migration

New code should use the KMP APIs in `com.okta.authfoundation.client.kmp.*`, `com.okta.authfoundation.credential.kmp.*`, and `com.okta.oauth2.kmp.*`. See [`auth-foundation/README.md`](auth-foundation/README.md) and [`oauth2/README.md`](oauth2/README.md) for the module-specific migration guides and examples.

## Building locally

```bash
./gradlew build
./gradlew spotlessCheck
./gradlew :auth-foundation:testAndroidHostTest :auth-foundation:jvmTest :oauth2:testAndroidHostTest :oauth2:jvmTest :okta-direct-auth:testAndroidHostTest :okta-direct-auth:jvmTest
```

[devforum]: https://devforum.okta.com/
