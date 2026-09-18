# AGENTS.md — okta-mobile-kotlin

Kotlin Multiplatform SDK for Okta authentication (Android, JVM). Code guidelines for anyone (human or AI) writing or reviewing code in this repository.

## Architecture & Design Principles

- **Immutability**: any class property that is public or reachable through a public method must be immutable.
- **Model separation**: keep three distinct kinds of models — a serializable *data model* for persistent storage, a non-serializable *domain model* for business logic, and a serializable *network model* used only for (de)serializing requests/responses.
- **Singletons are discouraged**, except for narrow cases such as a shared database instance.
- **Builder pattern** for any class with more than 5 configurable parameters (e.g. `OAuth2ClientBuilder`, `DirectAuthenticationFlowBuilder`).
- **Functional programming** patterns are preferred where they keep code clear; use imperative style when it is more concise.
- **`lateinit`.** is discouraged, except for cases such as tests.
- **Single source of truth**: state must have exactly one authoritative owner.
- **Depend on abstractions, not concretions**: even without a DI framework, wire dependencies via constructor parameters and code against interfaces rather than reaching for concrete singletons inside business logic.

### Module Dependency Hierarchy

```
auth-foundation           (KMP: Android+JVM) — root dependency, no internal SDK deps
├── oauth2                (KMP: Android+JVM) — AuthorizationCodeFlow, PKCE, token exchange
├── web-authentication-ui (Android-only) — browser redirect UI; depends on oauth2
├── legacy-token-migration (Android-only) — migrate from legacy Okta SDKs
├── okta-idx-kotlin       (Android-only) — IDX interaction code flow
│   └── native-authentication (Android-only)
├── okta-direct-auth      (KMP: Android+JVM) — direct auth flow
│   └── okta-direct-auth-shared (KMP: Android+JVM, Compose Multiplatform UI)
│       — also depends on oauth2 (commonMain) and web-authentication-ui (androidMain)
└── bom                   — Maven BOM (java-platform); constrains auth-foundation,
                             web-authentication-ui, oauth2, legacy-token-migration,
                             okta-idx-kotlin, okta-direct-auth (not native-authentication
                             or okta-direct-auth-shared)
```

Note: `okta-direct-auth` has no `js()`/`wasmJs()` target — despite the module name/README implying JS+WASM support, it is currently Android+JVM only.

Sample apps: `app`, `okta-direct-auth-android-app`, `okta-direct-auth-desktop-app`, `session-token-sample`, `legacy-token-migration-sample`, `dynamic-app`

### KMP Source Sets

Multiplatform modules (`auth-foundation`, `okta-direct-auth`, `okta-direct-auth-shared`) use:
`commonMain` / `androidMain` / `jvmMain` with corresponding test sets (`commonTest`, `androidHostTest`, `androidDeviceTest`, `jvmTest`).

Android-only libraries use standard `src/main/` and `src/test/` layout.

### JVM wrapper parity with Kotlin

`jvm` sub-packages provide Java-idiomatic builders/flows (method-chaining setters, `AuthFoundationResult`/`DirectAuthResult` instead of `kotlin.Result`, no suspend fun in the public surface) that delegate to a KMP commonMain Kotlin API — e.g. auth-foundation's `OAuth2ClientBuilder`/`TokenCredentialManager`, oauth2's flow wrappers (`AuthorizationCodeFlow`, `DeviceAuthorizationFlow`, etc.), okta-direct-auth's `DirectAuthenticationFlowBuilder`.

- These jvm wrappers MUST stay at full parity with their commonMain counterpart: every public property, setter, and method on the commonMain API needs a corresponding Java-friendly equivalent on the jvm wrapper, unless there's a documented reason (e.g. a suspend-only API with no Java-callable equivalent, or an intentionally Java-convenience-only method).
- When editing a commonMain builder or flow (adding/renaming/removing a public property, setter, or method), update its jvm wrapper in the same change — don't defer it. Check `git log --oneline -- <commonMain file>` vs the jvm file's history if unsure whether drift already exists.
- Found via real drift in `OAuth2ClientBuilder`, `TokenCredentialManager`, and `DirectAuthenticationFlowBuilder`.
- Keep the public surface narrow: prefer `internal`/`private` over `public` unless a member is genuinely meant for external callers. Every public member on a commonMain API is a mandatory parity obligation on its jvm wrapper, so a smaller public surface means a smaller, cheaper-to-maintain wrapper.

### Key Patterns

- **OAuth2Client** (`auth-foundation`) — low-level Okta authorization server client; uses `CoalescingOrchestrator` for request deduplication
- **Credential / CredentialDataSource** — token management with `RoomTokenStorage` (SQLCipher-encrypted Room DB) and biometric support
- **Flow classes** — each auth method is a flow: `AuthorizationCodeFlow`, `ResourceOwnerFlow`, `DeviceAuthorizationFlow`, `SessionTokenFlow`, `TokenExchangeFlow`, `RedirectEndSessionFlow` (oauth2),
  `InteractionCodeFlow` (idx), `DirectAuthenticationFlow` (direct-auth). Platform-specific flow implementations must honor the same contract — no platform-only exceptions, ordering, or side
  effects that a common-code caller wouldn't expect.
- **EventCoordinator** — pub/sub for credential lifecycle events (`CredentialStoredEvent`, `TokenCreatedEvent`, `BiometricKeyInvalidatedEvent`, etc.)

## Kotlin Concurrency & Coroutines

- **No thread-blocking locks in `suspend` functions**: never use `synchronized`, `synchronized()` blocks, or `ReentrantLock` inside suspend code — they block the underlying thread and risk deadlocking the whole dispatcher pool. Use `kotlinx.coroutines.sync.Mutex` with `withLock { }` instead.
- **Atomic primitives / concurrent collections** (`AtomicInteger`, `AtomicBoolean`, `AtomicReference`, `ConcurrentHashMap`) for simple shared mutable state.
- **No `GlobalScope`**: every coroutine must be tied to a structured `CoroutineScope` to avoid leaks and orphaned work.
- **`coroutineScope { }`** when a child failure should cancel siblings; **`supervisorScope { }`** when child failures should be isolated.
- **Never block `Main` or `Default`**: wrap blocking I/O or CPU-heavy work in `withContext(Dispatchers.IO)` / `withContext(Dispatchers.Default)`.
- **State flows**: mutate `MutableStateFlow` via `.update { it.copy(...) }`, never a direct `.value =` assignment, to keep read-modify-write atomic.
- **One-time events** (navigation, snackbars): use `Channel(Channel.BUFFERED)`, not a conflated flow.
- **File I/O**: always on `Dispatchers.IO`; write to a `.tmp` file and `Files.move(..., ATOMIC_MOVE)` rather than writing the target file in place.
- **Rapid repeated calls** (e.g. token refresh triggered by multiple callers): prefer `collectLatest`/`flatMapLatest` or an explicit `Job?` cancel-and-replace over manual cancellation tracking; debounce high-frequency triggers.

## Testing Constraints

- Do not write tests that assert what the compiler, type system, or language runtime already guarantees (e.g. asserting a typed variable's type). If a test failure would mean the compiler itself is broken, don't write it — only test custom business logic, edge cases, and state changes.
- New functionality must ship with tests in the same commit/PR — don't defer test coverage to a follow-up.
- Name test functions `xxx_Xxx` (no backticks).

## Code Style

- **Spotless** with ktlint + Compose rules (`io.nlopez.compose.rules:ktlint`); convention plugin and pinned versions at `buildSrc/src/main/kotlin/spotless.gradle.kts`.
- **License headers** required on all `.kt`, `.java`, `.xml` — templates in `config/license` and `config/license.xml`.
- **EditorConfig**: 4-space indent, max line 200 chars, LF line endings, trailing commas allowed. Java files use Google Java Format (default Google style, not AOSP).
- Public APIs are documented with KDoc (Kotlin) or Javadoc (Java).
- Methods with return types are wrapped in `Result` unless the function is pure or cannot throw. Prefer `runCatching` over try/catch where it fits.
- Use type-safe builders/DSLs for configuration.
- Never suppress a compiler warning without a documented reason.

## Security

- Never commit API keys or secrets. Use environment variables for sensitive configuration; keep test credentials local only.
- Any change to token storage, serialization, or encryption (SQLCipher-backed `RoomTokenStorage`) is security-sensitive — review carefully for leakage via logs or exceptions.
- PKCE/crypto/JWT/biometric handling (`code_verifier`, `state`, `nonce` generation, key invalidation) must not be weakened without an explicit, reviewed reason.
- Review dependency changes for known vulnerabilities; keep specific version pins in `gradle/libs.versions.toml` rather than open-ended ranges.

## API Compatibility

Public API surfaces are tracked with two mechanisms, split by target:

- **JVM targets** (`auth-foundation`, `oauth2`) — Kotlin Gradle Plugin's built-in ABI validation (`kotlin { abiValidation { } }`, `@OptIn(ExperimentalAbiValidation::class)`). Dump lives at
  `<module>/api/jvm/<module>.api`. Update with `./gradlew :auth-foundation:updateKotlinAbi`.
- **Everything Android** — `android-bcv-bridge`, a small convention plugin vendored in `buildSrc` (`buildSrc/src/main/kotlin/AndroidBcvBridgePlugin.kt`, applied as `id("android-bcv-bridge")`,
  configured via an `androidBcvBridge { }` block). It registers the standalone `binary-compatibility-validator`'s own internal task types (`KotlinApiBuildTask`/`KotlinApiCompareTask`) by hand,
  fed directly by a named compile task's output classes, because neither ABI tool sees these targets on their own:
  - `web-authentication-ui`, `okta-idx-kotlin`, `native-authentication`, `legacy-token-migration` — plain Android-only modules on AGP 9's built-in Kotlin (no `kotlin("android")` plugin applied), which
    neither the standalone BCV plugin nor KGP's native validator can detect (both gate on a Kotlin/Android KGP extension existing). Registered as `releaseApiCheck`/`releaseApiDump`.
  - `auth-foundation`, `oauth2`'s `android` target — a KMP target from `com.android.kotlin.multiplatform.library` (`KotlinMultiplatformAndroidLibraryTargetImpl`), which KGP's native `abiValidation`
    silently skips since it only recognizes `KotlinAndroidTarget`. Registered as `androidApiCheck`/`androidApiDump`, alongside `checkKotlinAbi`/`updateKotlinAbi` for their `jvm` target.
  Dump lives at `<module>/api/<module>.api` in all cases. `KotlinApiBuildTask`/`KotlinApiCompareTask` are BCV-internal, not public API, so the plugin is pinned to an exact BCV/ASM/kotlin-metadata-jvm
  version set — bumping requires re-verifying it still works.

Each module's config ignores its generated `BuildConfig`/`BuildInfo` class. The root `checkLegacyAbi` task aggregates `checkKotlinAbi`, `androidApiCheck`, and `releaseApiCheck` across all subprojects.
A removed or narrowed public API signature is a breaking change and requires a major version bump.

## Versions & Configuration

| What                    | Where                                                                                  |
|-------------------------|----------------------------------------------------------------------------------------|
| Per-module SDK versions | `buildSrc/src/main/java/Configuration.kt` (`BOM_VERSION`, `AUTH_FOUNDATION_VERSION`, etc.) |
| Android SDK levels      | `Configuration.kt` — `MIN_SDK`, `COMPILE_SDK`, `TARGET_SDK`                            |
| Dependency versions     | `gradle/libs.versions.toml` (Kotlin, Ktor, coroutines, Room, etc.)                     |
| Maven group             | root `build.gradle.kts` — `group = "com.okta.kotlin"` (per-module version comes from `Configuration.kt`, not a static `VERSION_NAME`) |
| Java target             | Java 11 (source + target compatibility)                                                |

## Publishing

- **Plugin**: vanniktech gradle-maven-publish-plugin — `publishToMavenCentral(automaticRelease)`
- **Coordinates**: `com.okta.kotlin:<module>:<version>`
- **Signing**: GPG via `-PsignAllPublications -PsignWithGpgCommand`
- **Snapshot**: pass `-Psnapshot` to append `-SNAPSHOT` suffix

## CI (CircleCI)

All jobs require `reversing-labs` (security scan) to pass first, then run in parallel:

| Job                      | What it validates                                                            |
|--------------------------|------------------------------------------------------------------------------|
| `unit-test`              | `testDebugUnitTest`                                                          |
| `unit-test-android-host` | `:okta-direct-auth:testAndroidHostTest :auth-foundation:testAndroidHostTest` |
| `build`                  | `assembleDebug` (generates debug keystore)                                   |
| `spotless-check`         | `spotlessCheck`                                                              |
| `api-check`              | `checkLegacyAbi`                                                             |
| `snyk-scan`              | Dependency vulnerability scan (master only)                                  |

Build image, JDK version, and Gradle daemon/worker/heap settings are defined in `.circleci/config.yml`.

## Build & Test Commands

```bash
./gradlew build                        # Full build + tests
./gradlew testDebugUnitTest            # Android unit tests (CI default)
./gradlew :okta-direct-auth:testAndroidHostTest :auth-foundation:testAndroidHostTest  # KMP host tests (Robolectric)
./gradlew spotlessCheck                # Verify formatting (ktlint + license headers)
./gradlew spotlessApply                # Auto-fix formatting
./gradlew :checkLegacyAbi              # Binary API compatibility check across all modules
./gradlew :auth-foundation:checkKotlinAbi  # Per-module jvm-target API check (KMP modules: auth-foundation, oauth2)
./gradlew :auth-foundation:androidApiCheck # Per-module android-target API check (KMP modules, via android-bcv-bridge)
./gradlew :okta-idx-kotlin:releaseApiCheck # Per-module API check (Android-only modules, via android-bcv-bridge)
./gradlew koverHtmlReport              # Code coverage report
```

Run a single module's tests: `./gradlew :oauth2:testDebugUnitTest`

Always run a build and the linter (`spotlessCheck`) before committing, and ensure all tests pass.
