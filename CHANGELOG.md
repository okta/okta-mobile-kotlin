# Changelog

## [2.4.0] - 2023-08-04
- Update AuthFoundation to 1.1.5. This fixes device token for "Keep me signed in" functionality.

## [2.3.0] - 2023-05-24

## Changed
- Treat responses with http code >500 as errors. [#186]
- Change module name from idx-kotlin to okta-idx-kotlin to align with the docs. [#187]

## [2.2.0] - 2023-04-13

## Changed
- Updated to latest auth-foundation (1.1.3). This adds support for DT (device token) cookie. DT cookie is used for "remember device" functionality.
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
- Due to the integration with AuthFoundation, many breaking changes were made, result and token classes were removed, and consolidated with AuthFoundation.

### Changed
- `IdxClient` -> `IdxFlow` which aligns with naming from other authentication flows.

## [1.0.0] - 2022-02-03
### Added
- Initial release!
