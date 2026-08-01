## [1.0.0] - Unreleased

Graduating from beta (0.0.1) to the first stable release.

### Breaking changes

- `DirectAuthenticationState.Authenticated.token` type changed from
  `com.okta.authfoundation.credential.Token` to `com.okta.authfoundation.client.TokenInfo`.
- `DirectAuthContinuation.WebAuthn.challengeData` changed from a `String` property to a
  `challengeData(): Result<String>` function; `proceed(authenticationResponseJson: String)`
  (previously an unimplemented stub) removed, replaced by `proceed(WebAuthnCeremonyHandler)` and
  `proceed(WebAuthnAssertionResponse)`.
- `com.okta.directauth.http.KtorHttpExecutor` removed — relocated to
  `com.okta.authfoundation.api.http.KtorHttpExecutor`.
- `com.okta.directauth.log.AuthFoundationLoggerImpl` removed from the public API — relocated into
  `auth-foundation`.
- `UNKNOWN_ERROR` constant removed from `InternalErrorCodeKt`.
- The builder's logger accessor type changed to the relocated
  `com.okta.authfoundation.api.log.AuthFoundationLogger`.

### Added

- WebAuthn/passkey support: `WebAuthnCeremonyHandler`, `WebAuthnAssertionResponse`,
  `AuthenticatorEnrollment`, `AndroidWebAuthnCeremonyHandler`, `PrimaryFactor.WebAuthn`,
  `DirectAuthTokenRequest.WebAuthn`/`WebAuthnMfa`.
- Full Java-compatible `CompletableFuture` API under `com.okta.directauth.jvm`: `DirectAuthResult`,
  `DirectAuthenticationFlow`, `DirectAuthenticationFlowBuilder`, and MFA/OOB/Prompt/Transfer/WebAuthn
  continuation wrappers.
- Java CLI sample app demonstrating Direct Authentication end-to-end.
- Cross-platform (KMP) credential management integration.
- New 3-arg builder `create(issuerUrl, clientId, scope)` overload; new 2-arg MFA `challenge`/
  `resume` overloads, added alongside the existing ones.
- ABI validation rolled out.
- `com.okta.directauth.jvm` MFA/OOB/Prompt/Transfer/WebAuthn continuation wrappers now implement
  `Closeable`, so callers can cancel an in-flight `*Async` call (e.g. an `OobPendingContinuation`/
  `TransferContinuation` poll loop, which can otherwise run for the challenge's full expiration
  window) without discarding the returned `CompletableFuture`.

### Changed

- Module converted from an Android-only build to Kotlin Multiplatform (Android + JVM).
- Internal `ApiResponseExt` extension functions refactored into `StepHandlers` — internal-only, no
  public API impact.

## [0.0.1] - 2026-02-02

### Added

- Initial alpha release of okta-direct-auth with the following features:
    * Password authentication
    * One-Time Passcode (OTP)
    * Out-of-Band authentication (Push, SMS, Voice)
    * Multi-Factor Authentication (MFA)
    * Self-Service Password Recovery (SSPR)
