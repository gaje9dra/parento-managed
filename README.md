# Parento Managed

Parento Managed is the Android application installed on an explicitly enrolled and authorized device in the Parento platform.

## Repository boundary

This repository is only the managed-device Android application:
gaje9dra/parento-managed

The companion repositories are not modified by this phase:
- gaje9dra/parento-admin
- gaje9dra/parento-backend

## Current phase

Phase 1.3 — Configuration, Environment & Operational Foundation

Phase 1.1 established the Android/Gradle foundation. Phase 1.2 added internal architecture and domain contracts. Phase 1.3 adds centralized configuration, environment separation, startup initialization, controlled logging, and an Android security baseline.

No device-management functionality is implemented.

## Configuration

Configuration is supplied through Android Gradle BuildConfig fields and consumed through BuildConfiguration / ManagedApplicationConfig. Application code should use the typed AppConfig boundary instead of scattering build constants through features.

### Environments

| Build type | Environment | Backend URL | Logging | Diagnostics |
|---|---|---|---|---|
| debug | development | https://dev-backend.example.invalid | DEBUG | enabled |
| test | test | https://test-backend.example.invalid | INFO | disabled |
| release | production | https://backend.example.invalid | WARN | disabled |

The .example.invalid endpoints are documentation/test placeholders, not production services. No production credentials are included.

The release configuration requires HTTPS. The final production backend domain must be supplied in deployment configuration when finalized; it is deliberately not invented here.

### Feature flags

A small typed feature-flag foundation exists. Future capabilities are disabled by default:
- enrollment
- realtime communication
- location
- screen sharing
- audio
- application management
- website filtering
- device restrictions

The flags do not implement or activate those features.

## Logging

ManagedLogger remains the application logging boundary. AndroidManagedLogger centrally applies the configured minimum log level and enabled/disabled state.

Do not log authentication or enrollment tokens, passwords, private keys, authorization headers, secrets, unnecessary location information, camera/microphone/screen contents, or other sensitive device information.

Release logging is intentionally restricted to WARN and ERROR-level output.

## Startup

ParentoApplication initializes configuration before the existing UI is created. Startup does not contact the backend and does not initialize future device-control services.

## Security baseline

The manifest currently:
- exposes only the launcher Activity
- sets android:exported=true only where required for the launcher
- disables application backup
- disables global cleartext traffic

No camera, microphone, location, screen-capture, accessibility, VPN, Device Owner, or other sensitive permissions were added.

Future sensitive capabilities must use legitimate Android/Android Enterprise APIs and explicit platform permissions.

## Version information

Current application version remains:
- version name: 0.1.0
- version code: 1

Future release signing should use real deployment-managed signing credentials outside source control. Fake signing credentials are not included.

## Development setup

Open the repository in Android Studio with a compatible JDK/Android SDK and allow Gradle to sync.

Build debug:
./gradlew assembleDebug

Run unit tests:
./gradlew test

Run lint:
./gradlew lint

Build the verification environment variant:
gradle assembleVerification

Build release:
./gradlew assembleRelease

This repository does not currently commit a Gradle wrapper. Use Gradle 8.13 (or configure Android Studio to use the project-compatible Gradle version).

No secrets are required for the current phase.

## Testing

Phase 1.3 adds configuration tests covering:
- valid configuration loading
- development/test/production separation
- invalid backend URL rejection
- production HTTPS enforcement
- production diagnostics restrictions
- default feature-flag state

The tests use the existing JUnit 4 dependency; no second test framework was introduced.

## Limitations

The following remain intentionally unimplemented:
- user/admin authentication
- Google OAuth
- password authentication
- device enrollment or QR pairing
- backend authentication or API calls
- device identity provisioning
- location/GPS collection
- camera or microphone access
- audio capture
- screen capture / MediaProjection / screen sharing
- application blocking or installation restrictions
- website/DNS/VPN/network filtering
- device locking or remote wipe
- Device Owner / Android Enterprise provisioning
- accessibility-based control
- hidden monitoring or covert persistence
- permission/security bypasses

## Cross-repository requirements

No companion repository was modified in Phase 1.3.

### gaje9dra/parento-backend

Later backend phases must expose the concrete authenticated managed-device API/realtime contracts that correspond to the Managed app communication boundary and finalized backend base URL.

### gaje9dra/parento-admin

Later Admin phases must consume backend-mediated contracts for administrator-facing operations. The Admin app must not connect directly to the Managed app.

## Phase status

Phase 1.3 establishes configuration, environment separation, controlled logging, startup initialization, and a basic Android security baseline without implementing future device-management functionality.

## Existing architecture boundaries

Phase 1.2 established these boundaries and they remain intact:

- Presentation / UI
- Device Management
- Policy Engine
- Communication
- Security
- Local Data
- Android Platform Integration
- Background work
- Logging

The domain model remains Android-independent and contains ManagedDevice, DeviceStatus, EnrollmentState, PolicyState, ConnectionState, OperationResult, and ManagedError. Phase 1.3 does not implement enrollment, backend communication, policy enforcement, or device-control behavior.

## Established Android versions and dependencies

Phase 1.3 preserves the existing versions rather than upgrading unrelated tooling:

- Android Gradle Plugin: 8.13.0
- Kotlin: 2.2.20
- compileSdk: 36
- targetSdk: 36
- minSdk: 26
- Java/Kotlin JVM target: 17
- AndroidX Core KTX: 1.17.0
- AndroidX AppCompat: 1.7.1
- Material Components: 1.13.0
- JUnit: 4.13.2

No new Gradle dependency was introduced in Phase 1.3.


## Phase 1.4 — Application Lifecycle, Navigation & UI Shell

Phase 1.4 establishes the managed application's UI/application-shell foundation while preserving the Phase 1.1–1.3 architecture.

### UI architecture

The application flow is:

    ParentoApplication
        ↓
    ManagedApplicationConfig
        ↓
    MainActivity
        ↓
    ManagedStatusScreen
        ↓
    ManagedUiState / ManagedStatusViewModel
        ↓
    Existing domain state contracts

The root navigation boundary is represented by RootDestination and RootNavigator. Current destinations are DEVICE_STATUS, ENROLLMENT_PLACEHOLDER, and ERROR. The enrollment destination is a future navigation capability only; enrollment itself is not implemented.

The UI uses Android Views and existing Material Components because the repository did not previously establish a Compose dependency. No Compose stack was introduced solely for this phase.

### UI states

ManagedUiState is a sealed state model containing Loading, Unenrolled, Content, and Error. ManagedStatusViewModel owns screen state rather than the Activity or individual views. State transitions are directly unit-testable.

The initial state is intentionally Unenrolled; the application does not claim that the device is enrolled or connected.

### Application lifecycle

MainActivity obtains the ViewModel through the Android lifecycle and re-renders from the ViewModel state during onCreate and onStart. No Activity, View, or Context reference is stored by the ViewModel.

No background device-management work is started.

### Screens

The current UI shell provides one user-facing status screen with representations for loading, unenrolled, content/managed status, and safe error states. Future enrollment and error destinations exist only as navigation boundaries.

The status shell displays the application name, version, management state, connection state, and a neutral statement that device-management features are not active in this phase.

### Accessibility and responsive behavior

The shell uses normal Android layouts rather than absolute positioning, provides a meaningful loading content description, uses a minimum 48dp action target, and relies on Android text rendering so user font scaling remains supported.

No information is communicated through color alone. System-bar dimensions are not hard-coded.

### Testing

Phase 1.4 adds unit coverage for root destination stability, default unenrolled UI state, loading/content/error ViewModel transitions, and typed device and connection state presentation.

No fake backend or device-management behavior is used for tests.

### Permissions and future functionality

Phase 1.4 adds no sensitive Android permissions and does not implement enrollment, backend communication, realtime transport, location, camera, microphone, screen capture, application blocking, website filtering, device locking, remote wipe, Device Owner provisioning, covert monitoring, or security bypasses.

## Phase 1.5 — Security Baseline, Testing Infrastructure & Phase 1 Completion

Phase 1.5 hardens the existing managed-app foundation without implementing future device-management functionality.

### Security review

- The manifest exposes only the required launcher Activity.
- No camera, microphone, location, accessibility, VPN, notification-listener, Device Owner, storage, or other future sensitive permissions were added.
- Global cleartext traffic remains disabled.
- Application backup remains disabled.
- No production credentials or management secrets are committed.
- Backend configuration is centralized and production requires HTTPS.
- Backend URLs reject embedded credentials and unexpected path/query/fragment data.
- Production debug diagnostics remain disabled.
- Future management feature flags remain disabled.

### Lifecycle

`ManagedStatusViewModel` owns UI state and has no Activity, View, or Context reference.

`SavedStateHandle` persists the current Phase 1 UI state representation so Activity recreation and process recreation can restore a valid state. Invalid saved state safely falls back to the neutral `Unenrolled` state.

No persistent background service or uncontrolled background operation was introduced.

### Navigation and UI

The existing `RootDestination` / `RootNavigator` boundary remains intact.

The existing state model remains:
- Loading
- Unenrolled
- Content
- Error

The UI continues to use Android Views and Material Components. No Compose migration was introduced.

Accessibility and responsive behavior remain intentionally lightweight: scalable Android text sizing, loading semantics, standard layouts, and a minimum 48dp action target.

### Testing

Phase 1.5 strengthens JVM tests for:
- valid/invalid configuration
- HTTPS enforcement
- backend URL credential/path/query/fragment rejection
- environment separation
- release/debug diagnostic boundaries
- disabled future feature flags
- ViewModel state transitions
- SavedStateHandle state restoration
- navigation destination stability
- navigator transitions
- existing architecture/domain contracts

Tests do not require real backend services, credentials, or device-specific state.

### CI

A minimal GitHub Actions verification workflow is provided at `.github/workflows/verify.yml`.

It runs with JDK 17 and Gradle 8.13 and performs:
1. unit tests
2. lint
3. debug build
4. release build

It uploads debug and release APK artifacts for verification only. It does not publish or deploy an APK.

The repository does not currently contain a Gradle wrapper, so CI provisions Gradle 8.13 explicitly.

### Documentation

Added:
- `docs/phase-1-architecture.md`
- `docs/cross-repository-contracts.md`

These documents distinguish implemented Phase 1 architecture from planned and deferred functionality.

### Cross-repository boundary

Only `gaje9dra/parento-managed` is modified in Phase 1.5.

Future requirements for `parento-backend` and `parento-admin` are documented only. Neither repository is modified.

### Security and management boundary

This application is intended for authorized device management. Future sensitive capabilities must use legitimate Android/Android Enterprise APIs and required platform authorization.

The following remain deferred: authentication, enrollment, QR pairing, backend API integration, WebSockets, FCM, location, camera, microphone/audio capture, screen capture/sharing, application blocking, website/DNS/VPN filtering, Device Owner/Android Enterprise provisioning, device locking, remote wipe, policy enforcement, covert monitoring, surveillance, and security bypasses.


## Phase 2.1 — Local Persistence

Phase 2.1 establishes the managed application's local persistence foundation only.

### Persistence technology

The project uses Android Room with SQLite as its local relational persistence technology. No second database framework was introduced.

### Local data architecture

UI → ViewModel → Domain / Use Case → Repository → Local Data Source → Room Database

The UI does not access Room directly. Room has no UI dependencies.

### Database location and initialization

The database is stored in the application's private Room/SQLite storage as `parento-managed.db`. `LocalDatabaseProvider` initializes it once using the application context.

The initial schema contains only `LocalApplicationStateEntity`, representing minimal application/device-management metadata:

- local state version
- last synchronization timestamp
- local initialization state

No enrollment, authentication, password, JWT, location, camera, microphone, audio, screen, application-blocking, website-blocking, remote-command, policy, or audit entities were created.

### Repository

`LocalStateRepository` abstracts reading, writing, clearing, and observing persisted application state. `RoomLocalStateRepository` maps Room entities to domain data and converts storage failures into `ManagedError.STORAGE_FAILURE` rather than exposing raw database exceptions to UI.

Production database operations use suspend DAO methods or Flow. No production main-thread database access is introduced.

### Migrations

Room schema versioning is enabled at version 1. Schema export is configured under `schemas/`. No fake future migrations are included. Future schema changes must increment the database version and provide an explicit deterministic migration. Destructive migration is not silently enabled.

### Testing

JVM tests cover the local state model and storage error contract.

Android instrumentation tests use an isolated in-memory Room database and cover:

- initialization/read
- write/read round trip
- update
- clear
- reactive observation
- safe storage result handling

The tests never depend on the developer's real persistent application database.

### Security

Phase 2.1 does not create or store credentials, tokens, passwords, or other secrets. Storage contents are not logged. Android backup remains disabled. No sensitive Android permissions were added.

Future sensitive local data must use appropriate Android secure-storage/cryptographic facilities in the relevant phase. No custom cryptographic protocol is introduced here.

### Development/test reset

Tests use an in-memory database. There is no production behavior that automatically deletes management state, no anti-uninstall behavior, and no covert persistence mechanism.

### Backend boundary

This phase does not implement REST clients, authentication requests, enrollment API calls, WebSockets, FCM, backend synchronization, or device commands. Backend requirements are documented only and belong to later phases/repositories.


## Phase 2.2 — Managed Local Domain Persistence

Phase 2.2 extends the Phase 2.1 Room foundation into a minimal lifecycle-state persistence layer.

### Architecture

UI → ViewModel → Domain → Repository → Local Data Source → Room

The current application UI remains Android Views/Material because the repository did not establish Compose. The persistence layer remains UI-independent.

### Persisted state

The single local state record now contains:

- stable application-scoped installation identifier
- identity creation timestamp
- local state version
- last synchronization timestamp
- local initialization state
- enrollment lifecycle state
- connection lifecycle state

A newly installed or previously empty state starts as:

- enrollment: `UNENROLLED`
- connection: `UNKNOWN`

The installation identifier is generated with a UUID and stored locally. Android hardware identifiers such as IMEI, serial number, and MAC address are not collected.

### Room schema

Room database version is now **2**.

Migration:

`MIGRATION_1_2`

preserves the Phase 2.1 state row and adds the Phase 2.2 identity and lifecycle columns with safe defaults.

Production destructive migration is not enabled.

### Repository

`LocalStateRepository` provides:

- read/observe local state
- write/clear local state
- get-or-create stable local identity
- update enrollment state
- update connection state

Room exceptions are converted to `ManagedError.STORAGE_FAILURE`.

No backend communication, enrollment workflow, credentials, or device-control functionality is introduced.

### Testing

Phase 2.2 adds isolated Android tests for:

- stable identity creation
- identity retrieval
- enrollment state persistence
- connection state persistence
- reactive Flow observation
- repository round trips
- Room 1 → 2 migration
- safe initial state

The migration test starts from a Phase 2.1 schema and verifies existing persisted values survive.

### Backup and security

Application backup remains disabled. The Phase 2.2 state contains no passwords, tokens, refresh tokens, backend secrets, enrollment secrets, or private keys.

The installation ID is application-scoped and non-hardware-derived.

No sensitive Android permissions were added.

### Deferred

Backend communication, authentication, enrollment/pairing, Device Owner provisioning, monitoring, location, camera, microphone/audio, screen capture/sharing, device controls, application/website blocking, filtering, policy synchronization, remote commands, and sensitive management capabilities remain deferred.
