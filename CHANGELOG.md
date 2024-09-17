# Changelog

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
