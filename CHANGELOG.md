# Changelog

## [3.1.0] 2025-07-18

[Commits](https://github.com/okta/okta-idx-android/compare/3.0.3...3.1.0)

### Added

- Added support for passkey (WebAuthn) enrollment and authentication.
- Added `webauthnAutofillUIChallenge` as a remediation capability to improve the passkey user experience.
- Added an example of passkey registration and authentication to the sample app.
- Added the `Origin` header to all satisfy a backend requirement for android webauthn origin.

### Changed

- Updated the sample app to use `local.properties` instead of `okta.properties` for configuration.
- Updated the `woodstox`, `dokka`, and `jackson-fasterxml` libraries to their latest versions.

## [3.0.3] 2025-04-10

[Commits](https://github.com/okta/okta-idx-android/compare/3.0.2...3.0.3)

### Changed

- InteractionCodeFlow.resume() now returns an error when the flowContext is not initialized.
- InteractionCodeFlow.exchangeInteractionCodeForTokens() now returns an error when the flowContext is not initialized.
- InteractionCodeFlow.evaluateRedirectUri() now returns an error when the flowContext is not initialized.

## [3.0.2] 2025-03-10

[Commits](https://github.com/okta/okta-idx-android/compare/3.0.1...3.0.2)

### Fixed

- Fixed a crash when using this SDK in Flutter

## [3.0.1] 2024-09-17

[Commits](https://github.com/okta/okta-idx-android/compare/3.0.0...3.0.1)

### Fixed

- Fixed occasional encrypted storage corruption.

## [3.0.0] 2024-06-14

[Commits](https://github.com/okta/okta-idx-android/compare/2.4.1...3.0.0)

### Changed

- Update AuthFoundation to 2.0. By updating to AuthFoundation 2.0, initializing `InteractionCodeFlow` has changed from
  `oidcClient.createInteractionCodeFlow` to `InteractionCodeFlow`. Users must perform an additional
  `InteractionCodeFlow.start` call before calling `InteractionCodeFlow.resume`.
- AuthFoundation 2.0 is a major version release with breaking API changes. As a result, okta-idx-android is also a major
  version release. See
  okta-mobile-kotlin [migration guide from 1.x to 2.x](https://github.com/okta/okta-mobile-kotlin?tab=readme-ov-file#migrating-from-okta-mobile-kotlin-1x-to-2x).
- Root level org auth URLs are now supported with this release.

## [2.4.1] 2024-03-20

### Changed

- Update AuthFoundation to 1.2.1

### Fixed

- Make PasswordSettings.Age field nullable. This should fix crashes when parsing IDX enrollment responses in
  passwordless settings.

## [2.4.0] - 2023-08-04

- Update AuthFoundation to 1.1.5. This fixes device token for "Keep me signed in" functionality.

## [2.3.0] - 2023-05-24

## Changed

- Treat responses with http code >500 as errors. [#186]
- Change module name from idx-kotlin to okta-idx-kotlin to align with the docs. [#187]

## [2.2.0] - 2023-04-13

## Changed

- Updated to latest auth-foundation (1.1.3). This adds support for DT (device token) cookie. DT cookie is used for "
  remember device" functionality.
- Add user profile information to IdxResponse [#181](https://github.com/okta/okta-idx-android/pull/181)
- Add nonce and max_age support [#172](https://github.com/okta/okta-idx-android/pull/172)
- Add form validation support [#171](https://github.com/okta/okta-idx-android/pull/171)
- Add polling support [#169](https://github.com/okta/okta-idx-android/pull/169)

## [2.1.0] - 2022-09-21

### Changed

- Updated to the latest auth-foundation (1.1.0)

## [2.0.0] - 2022-07-11

### Changed

- Updated to the latest auth-foundation (1.0.0)
- Rename IdxFlow to InteractionCodeFlow [#152](https://github.com/okta/okta-idx-android/pull/152)

## [2.0.0-BETA6] - 2022-06-03

### Changed

- Updated to the latest auth-foundation (0.6.0-BETA)

## [2.0.0-BETA5] - 2022-04-25

### Changed

- Updated to the latest auth-foundation (0.5.0-BETA)

## [2.0.0-BETA4] - 2022-04-25

### Changed

- Updated to the latest auth-foundation (0.4.0-BETA)

## [2.0.0-BETA3] - 2022-04-14

### Changed

- Updated to the latest auth-foundation (0.3.0-BETA)

## [2.0.0-BETA2] - 2022-03-25

### Added

- Integrated with AuthFoundation, this included the ability to security store and access credentials.

### Removed

- Due to the integration with AuthFoundation, many breaking changes were made, result and token classes were removed,
  and consolidated with AuthFoundation.

### Changed

- `IdxClient` -> `IdxFlow` which aligns with naming from other authentication flows.

## [1.0.0] - 2022-02-03

### Added

- Initial release!
