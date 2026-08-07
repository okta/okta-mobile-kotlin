# Changelog

## auth-foundation Unreleased

#### Added
- `OAuth2ClientBuilder` (jvm wrapper) gained `setJson`, `setIdTokenValidator`,
  `setAccessTokenValidator`, `setDeviceSecretValidator`, and `setRateLimitRetryCallback`, restoring
  parity with the commonMain `OAuth2ClientBuilder`/`OAuth2ClientConfiguration`.
- `TokenCredentialManager` (jvm wrapper) gained `setMetadata(TokenMetadata)`,
  `find(Predicate<TokenMetadata>)`, and `findByCredential(Predicate<Credential>)`, restoring parity
  with the commonMain `TokenCredentialManager`.

#### Fixed
- `OAuth2ClientBuilder` (jvm wrapper)'s `clientSecret` was tracked internally as a nullable
  `String?` and only forwarded to the underlying KMP builder when set, diverging from
  `OAuth2ClientConfiguration.clientSecret`'s actual non-null `String` type (default `""`). It's now
  tracked as `String = ""` and always forwarded.

## web-authentication-ui Unreleased

#### Added
- `DefaultWebAuthenticationProvider` now launches via Chrome's Auth Tab (`androidx.browser.auth.AuthTabIntent`)
  when the resolved browser supports it, falling back to Chrome Custom Tabs otherwise. New
  `customizeAuthTabIntent` constructor parameter, symmetric with the existing `customizeTabsIntent`
  (#372).

## auth-foundation 3.0.0 - 2026-08-05

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/auth-foundation@2.0.5...auth-foundation@3.0.0)

Converted from an Android-only library to a Kotlin Multiplatform module (Android + JVM). This is a
major release with breaking changes to the credential-event and cache APIs.

#### Breaking changes
- Module artifact shape changed from a single Android AAR to a KMP artifact (Android + JVM
  variants). Consumers pulling `auth-foundation` directly (not via a flow module) may need to
  re-resolve dependencies.
- `Cache` interface gained a new abstract `clear()` method with no default implementation — any
  custom `Cache` implementation must add it to keep compiling.
- `NoOpCache` changed from public to internal.
- `CredentialCreatedEvent`, `CredentialDeletedEvent`, `CredentialStoredAfterRemovedEvent`,
  `CredentialStoredEvent`, and `DefaultCredentialChangedEvent`: `getCredential(): Credential` removed,
  replaced by `getCredentialIdentifier(): CredentialIdentifier`.
- `NoAccessTokenAvailableEvent.getCredential()` return type changed from `Credential` to
  `CredentialIdentifier`.
- `CredentialStoredEvent.getToken()` return type changed from `Token` to `TokenInfo?` (now nullable).
- `JwtParser.Companion.create()` — the only public factory for `JwtParser` — removed.
- `CoalescingOrchestrator` changed from public to internal.
- `com.okta.authfoundation.api.http.log.AuthFoundationLogger`/`LogLevel` relocated to
  `com.okta.authfoundation.api.log.*`.
- `@InternalAuthFoundationApi`-annotated `AesEncryptionHandler.encryptString` and
  `AndroidKeystoreUtil.getOrCreateAesKey` signatures changed to return `Result` (lower real-world
  impact, but a binary break for anyone using the internal API).
- `ClaimsProvider.audience` return type changed from `String?` to `List<String>?`, since per
  RFC 7519 §4.1.3 / OIDC Core 1.0 §3.1.3.7 the `aud` claim may be either a single string or a JSON
  array of strings (#414).
- `Credential.scope()` return type changed from `String` to `List<String>`; the redundant
  `Credential.scopes(): List<String>` accessor was removed (merged into `scope()`).
- `OAuth2ClientConfiguration.defaultScope` return type changed from `String` to `List<String>`.

#### Added
- KMP `OAuth2Client`, `OAuth2ClientBuilder`, `OAuth2ClientConfiguration`, `OAuth2EndpointOverrides`
  (#379, #380, #383, #398).
- Cross-platform credential management: `TokenCredentialManager`, KMP `Credential`,
  `CredentialIdentifier`, `TokenData`/`TokenInfo`, `TokenMetadata` (#381).
- Room-based KMP persistent storage with pluggable encryption (`RoomTokenStorage`,
  `TokenEncryptionHandler`) (#382).
- Cross-platform User-Agent header and ID token validation (#384).
- KMP crypto abstraction, `PkceGenerator`, URL utilities, `BrowserRedirectHandler` (#387).
- HTTP-date parsing in `parseRetryAfterHeader` per RFC 7231 §7.1.3 (#388).
- EC key JWT validation, `refreshToken` extra params, custom endpoint overrides via
  `OAuth2EndpointOverrides` (#398).
- Biometric authentication support for the KMP credential path (#405).
- Typed rate-limit retry configuration (`RateLimitRetryConfig`, `MaxRetries`, `MinDelaySeconds`,
  `rateLimitRetryCallback` on the builder), replacing the mutable `EventCoordinator`-based retry
  config on the KMP path (#407, #408).
- New event categories: `CredentialEvent`/`TokenEvent` marker interfaces, plus new
  `TokenRefreshedEvent`, `TokenRevokedEvent`, `RateLimitException` (#407).
- ABI validation (KGP native for the jvm target, `android-bcv-bridge` for the android target)
  rolled out (#411).
- Deprecated the Android-only APIs (`OidcConfiguration`, `OAuth2Client.default`, Android
  `Credential`, `com.okta.oauth2.*`) in favor of the KMP equivalents, with migration guidance in
  `auth-foundation/README.md`.
- ID token validation (Android and KMP `DefaultIdTokenValidator`) now accepts the `aud` claim as
  either a single string or a JSON array, checking that the client ID is contained in the audience
  list rather than requiring an exact string match (#414).

#### Fixed
- NPE when `getCertificate()` returns null in `TokenEncryptionHandler` (#402).
- Uncaught `ProviderException` in `AndroidKeystoreUtil.getOrCreateAesKey` (#403).
- `CoalescingOrchestrator` reimplemented with `Mutex` instead of `synchronized`, removing a
  thread-blocking lock inside suspend functions (#400).

## oauth2 3.0.0 - 2026-08-05

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/e70ffa5c...oauth2@3.0.0)

#### Breaking changes
- Removed the `String`-scope overloads of `start(...)` on `AuthorizationCodeFlow`,
  `DeviceAuthorizationFlow`, `ResourceOwnerFlow`, `SessionTokenFlow`, and `TokenExchangeFlow` —
  scopes must now be passed as `List<String>`.
- Reordered the `scope`/extra-params parameters in `AuthorizationCodeFlow.start` and
  `SessionTokenFlow.start` (scope now precedes the extra-params map), affecting positional
  (non-named-argument) call sites.
- All six KMP flow interfaces (`AuthorizationCodeFlow`, `DeviceAuthorizationFlow`,
  `RedirectEndSessionFlow`, `ResourceOwnerFlow`, `SessionTokenFlow`, `TokenExchangeFlow`) gained a
  new abstract `getClient(): OAuth2Client` accessor — any custom implementation of these interfaces
  must add it to keep compiling.

#### Added
- Entire `com.okta.oauth2.kmp` package: KMP `ResourceOwnerFlow`, `DeviceAuthorizationFlow`,
  `TokenExchangeFlow`, `AuthorizationCodeFlow`, `SessionTokenFlow`, `RedirectEndSessionFlow`
  (#389–#394).
- Java-compatible `CompletableFuture` wrappers for all six flows under `com.okta.oauth2.kmp.jvm`
  (#395, #404).
- `LocalhostBrowserRedirectHandler` and other KMP crypto/URL utilities for JVM browser redirects
  (#387).
- Android compat extensions bridging the new KMP flows to existing Android call sites (#395).
- Scopes are requested as `List<String>` on `AuthorizationCodeFlow`, `DeviceAuthorizationFlow`,
  `ResourceOwnerFlow`, `SessionTokenFlow`, and `TokenExchangeFlow` (`RedirectEndSessionFlow` has no
  scope parameter).
- Public `getClient(): OAuth2Client` accessor on all six KMP flow interfaces.
- ABI validation rolled out for both the jvm and android targets (#411).
- Deprecated the Android-only flow classes (`com.okta.oauth2.*`) in favor of `com.okta.oauth2.kmp.*`,
  with migration guidance in `oauth2/README.md`.

#### Changed
- Module converted from an Android-only build to Kotlin Multiplatform (Android + JVM) (#386).

## web-authentication-ui 3.0.0 - 2026-08-05

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/e70ffa5c...web-authentication-ui@3.0.0)

#### Breaking changes
- `WebAuthentication.authorizationCodeFlow` and `.redirectEndSessionFlow` changed from public `var`
  properties to internal, removing their public getters/setters (#406).
- The `login(..., scope: List<String>, ...)` overload added in #406 had its `scope`/extra-params
  parameters reordered (scope now precedes the extra-params map) — source- and binary-incompatible
  for existing callers of that overload.

#### Added
- New `WebAuthentication(OAuth2Client, WebAuthenticationProvider)` constructor accepting the KMP
  `OAuth2Client`, added alongside the existing constructors (#406).
- New `login(..., scope: List<String>, ...)` overload alongside the existing `String` overload
  (#406).
- `DefaultWebAuthenticationProvider` (+ companion), `WebAuthentication.FlowAlreadyInProgressException`,
  `events.UIEvent` marker interface (#406, #407).
- ABI validation rolled out (#411).

#### Fixed
- Step-up redirect race condition in `DefaultRedirectCoordinator` (#401).

#### Changed
- Internally now uses the KMP `AuthorizationCodeFlow`; deprecated the Android-only
  `OidcConfiguration`-based path (#406).
- `ForegroundActivityEvent`/`CustomizeBrowserEvent`/`CustomizeCustomTabsEvent` reparented under a
  new `UIEvent` marker interface — still `Event` subtypes, non-breaking (#407).

## [2.0.3] - 2025-02-18
- Make SDK defaults configurable by third party SDKs [#323](https://github.com/okta/okta-mobile-kotlin/pull/323)
- Update dependencies

## [2.0.2] - 2024-09-17

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/2.0.1...2.0.2)

### Fixed
- Fix AEADBadTagException issues caused by corrupt encrypted files [#313](https://github.com/okta/okta-mobile-kotlin/pull/313)
- Fix default token migration from 1.x to 2.x [#314](https://github.com/okta/okta-mobile-kotlin/pull/314)
- Allow using accessToken if idToken is missing [#315](https://github.com/okta/okta-mobile-kotlin/pull/315)

## [2.0.1] - 2024-06-12

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/2.0.0...2.0.1)

This version exposes ApplicationContextHolder for use by [okta-idx-android](https://github.com/okta/okta-idx-android)

## [2.0.0] - 2024-06-03

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.2.1...2.0.0)

This is a major version release with a number of breaking API changes and new features. Please check README.md changes under the above Commits link.

### Migration
- See [Migrating from okta-mobile-kotlin 1.x to 2.x](https://github.com/okta/okta-mobile-kotlin?tab=readme-ov-file#migrating-from-okta-mobile-kotlin-1x-to-2x) for a full description of how to migrate.

### Major changes
- The SDK now includes first class support for Biometric encryption. See [Biometric Credentials](https://github.com/okta/okta-mobile-kotlin?tab=readme-ov-file#biometric-credentials)
- TokenStorage interface is redefined and reimplemented. If using a custom TokenStorage, please migrate it using [Token Migration guide](https://github.com/okta/okta-mobile-kotlin?tab=readme-ov-file#token-migration)
- OAuth APIs are instantiated differently from before. Users no longer need to manage references to OidcClient for instantiating OAuth flows.
- Internally, EncryptedSharedPreferences have been removed from the SDK, and replaced with Room DB. Encryption is done using AndroidKeyStore primitives, and SQLCipher. Migration to the new storage is handled automatically for most cases.

### Minor changes
- Jetpack startup has been removed from the SDK. This should resolve any startup initializer issues.
- DT cookie has been removed from this SDK. That will be moved to [okta-idx-android](https://github.com/okta/okta-idx-android) instead.
- EventCoordinator events now subclass Event class. This should make it easier to find Events.

## [1.2.1] - 2024-01-31

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.2.0...1.2.1)

### Added
- Added state value customization to AuthorizationCodeFlow.start [#278](https://github.com/okta/okta-mobile-kotlin/pull/278)

### Fixed
- DeviceTokenProvider initialization issues have been mostly fixed. A possible crash can still be encountered in case of corrupt key in keystore [#278](https://github.com/okta/okta-mobile-kotlin/pull/278)

## [1.2.0] - 2023-11-06

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.1.5...1.2.0)

### Added
- Updated libraries across several commits: [#269](https://github.com/okta/okta-mobile-kotlin/pull/269) [#264](https://github.com/okta/okta-mobile-kotlin/pull/264)
- Add optional debounce functionality to browser redirect cancellation: [#263](https://github.com/okta/okta-mobile-kotlin/pull/263)

### Fixed
- Reorder okhttp interceptors to prioritize user-defined interceptors [#265](https://github.com/okta/okta-mobile-kotlin/pull/265)

## [1.1.5] - 2023-08-04

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.1.4...1.1.5)

### Fixed
- Fix DT (device token) cookie formatting to fix "remember device" functionality in downstream SDKs. [#260](https://github.com/okta/okta-mobile-kotlin/pull/260)

## [1.1.4] - 2023-08-03

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.1.3...1.1.4)

### Added
- CredentialBootstrap.reset() is now publicly visible for easier testing. [#258](https://github.com/okta/okta-mobile-kotlin/pull/258)

### Fixed
- Fix issues with activity lifecycle destroying browser login state. [#258](https://github.com/okta/okta-mobile-kotlin/pull/258)
- Handle possible concurrent access to SharedTokenStorage. [#256](https://github.com/okta/okta-mobile-kotlin/pull/256)

## [1.1.3] - 2023-04-12

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.1.2...1.1.3)

### Added
- Added DT (device token) cookie to okHttpClient for supporting "remember device" functionality in downstream SDKs. [#240](https://github.com/okta/okta-mobile-kotlin/pull/240)

## [1.1.2] - 2023-02-13

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.1.1...1.1.2)

### Fixed
- Fix a race condition caused by activity lifecycle when multiple login/logout are called too quickly. [#238](https://github.com/okta/okta-mobile-kotlin/pull/238)

## [1.1.1] - 2022-10-17

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.1.0...1.1.1)

### Fixed
- Fix a potential race when writing exceptionPairs. [#222](https://github.com/okta/okta-mobile-kotlin/pull/222)

## [1.1.0] - 2022-09-13

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/1.0.0...1.1.0)

### Added
- Add revokeAllTokens to Credential. [#201](https://github.com/okta/okta-mobile-kotlin/pull/201)
- Add support for biometric backed storage. [#207](https://github.com/okta/okta-mobile-kotlin/pull/207)
- Add Credential.tokenStateFlow. [#211](https://github.com/okta/okta-mobile-kotlin/pull/211)
- Introduce CredentialStoredEvent. [#212](https://github.com/okta/okta-mobile-kotlin/pull/212)
- Add getOrThrow method to OidcClientResult. [#213](https://github.com/okta/okta-mobile-kotlin/pull/213)
- Expose an errorIdentifier for IdTokenValidator. [#214](https://github.com/okta/okta-mobile-kotlin/pull/214)
- Add rate limit handling for network requests. [#215](https://github.com/okta/okta-mobile-kotlin/pull/215)

## [1.0.0] - 2022-07-11

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/0.6.0-BETA...1.0.0)

### Added
- Added support for amr and acr claims [#175](https://github.com/okta/okta-mobile-kotlin/pull/175)
- Support for more OpenID Providers
- Support for Device Authorization Grant slow_down [#186](https://github.com/okta/okta-mobile-kotlin/pull/186)
- Added `errorId` to `AuthorizationCodeFlow.ResumeException` [#184](https://github.com/okta/okta-mobile-kotlin/pull/184)

### Changed
- Updated IdTokenValidator to include an object for validation parameters [#181](https://github.com/okta/okta-mobile-kotlin/pull/181)

## [0.6.0-BETA] - 2022-06-03

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/0.5.0-BETA...0.6.0-BETA)

### Added
- `SessionTokenFlow` which aids migration from legacy Authn APIs.
- Cache .well-known/openid-configuration results.

### Changed
- Made most of `OidcConfiguration` internal, use `AuthFoundationDefaults` for customization.
- Add extra parameters to the `DeviceAuthorizationFlow`.
- Remove the default on `Credential.revoke`.
- Expose `JwtParser.parse` instead of `OidcClient.parseJwt`.

### Fixed
- Listen for configuration changes in `ForegroundActivity`.
- Fix missing slash in SDK version.

## [0.5.0-BETA] - 2022-05-10

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/0.4.0-BETA...0.5.0-BETA)

### Changed
- `OidcClient.refresh` no longer accepts scopes, as they are not used.
- Changed the way id token validation customization happens.
- Made scope a string, rather than a set.
- Renamed metadata to tags.

### Fixed
- Fixed issues with non Chrome browsers.
- Eagerly error when launching a web based flow when an Activity is backgrounded.
- Properly support backgrounded internal Activities during web authentication.

## [0.4.0-BETA] - 2022-04-25

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/0.3.0-BETA...0.4.0-BETA)

### Added
- Legacy token migration - migrate tokens from okta-oidc-android, see [migrate.md](migrate.md).
- Consumer proguard rules, allowing R8 including with full mode.
- Attempt to fix storage/crypto errors automatically.
- Emit an event when credentials are deleted.
- Added BOM to project.
- Added `CredentialBootstrap.oidcClient` to preserve ease of use.

### Changed
- `Credential.oidcClient` is now an implementation detail, and not publicly accessible.
- Minting tokens no longer automatically stores tokens, it's now an explicit action.
- Renamed `CredentialBootstrap.credential` to `CredentialBootstrap.defaultCredential`.

### Fixed
- Fixed an issue where the chrome custom tab would linger after authentication.

## [0.3.0-BETA] - 2022-04-14

[Commits](https://github.com/okta/okta-mobile-kotlin/compare/0.2.0-BETA...0.3.0-BETA)

### Added
- Added CredentialBootstrap for handling common `Credential` use cases.
- Added a tag to OkHttp requests with the associated `Credential`.

### Changed
- Simplified WebAuthenticationClient to return a Token in a single API call.

### Fixed
- Fixed an issue where a valid issuer might fail validation.
- Numerous bug fixes and improvements.

## [0.2.0-BETA] - 2022-03-25
### Added
- Initial release!
