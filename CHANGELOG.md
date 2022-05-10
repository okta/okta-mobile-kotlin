# Changelog

## [0.5.0-BETA] - 2022-05-10
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
