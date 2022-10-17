# Changelog

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
