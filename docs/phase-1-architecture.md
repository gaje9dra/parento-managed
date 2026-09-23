# Parento Managed — Phase 1 Architecture

## Scope

This repository contains only the managed-device Android application. Phase 1 establishes a secure application shell and architecture boundaries without implementing device-management capabilities.

## Application lifecycle

Android Application -> ParentoApplication -> ManagedApplicationConfig -> MainActivity -> ManagedStatusScreen -> ManagedStatusViewModel -> ManagedUiState

`ParentoApplication` initializes typed configuration during application startup. `MainActivity` owns Activity/UI lifecycle concerns. The ViewModel owns UI state and has no Activity, View, or Context reference.

`ManagedStatusViewModel` uses `SavedStateHandle` so its current Phase 1 UI state can survive Activity recreation and process recreation. No persistent background service is started.

## Configuration

`AppEnvironment`, `AppConfig`, `BuildConfiguration`, and `ManagedApplicationConfig` form the configuration boundary.

Current build environments: development/debug, test, and production/release.

The backend URL is centralized and validated as an origin. Production requires HTTPS. No real production domain or credentials are committed.

Future feature flags are present only as disabled configuration boundaries: enrollment, realtime communication, location, screen sharing, audio, application management, website filtering, and device restrictions.

## UI

The current UI uses Android Views with the repository's existing Material Components dependency. Compose was not introduced because it was not previously part of the project architecture.

The shell exposes four states: `Loading`, `Unenrolled`, `Content`, and `Error`.

The default state is `Unenrolled`; the app never claims that a device is enrolled or connected without an actual management implementation.

The shell uses normal Android layout behavior, scalable text sizing, accessible loading semantics, and a minimum 48dp action target.

## Navigation

`RootDestination` and `RootNavigator` define the Phase 1 navigation boundary.

Current destinations: `DEVICE_STATUS`, `ENROLLMENT_PLACEHOLDER`, and `ERROR`.

These destinations do not implement enrollment or device-management functionality. There is no fake backend navigation and no unnecessary back-stack implementation.

## ViewModel and state management

`ManagedStatusViewModel` provides explicit state-transition methods and persists the state representation through `SavedStateHandle`.

State restoration is defensive: invalid or incomplete saved state falls back to `Unenrolled`.

## Logging

`ManagedLogger` and `AndroidManagedLogger` are the application logging boundary.

Current startup logging contains only a static initialization message. The logger applies environment-specific minimum levels and can be disabled. Release configuration uses WARN as the minimum level.

Credentials, tokens, authorization headers, pairing/enrollment secrets, private keys, and future sensitive device data must not be logged.

## Security boundaries

Implemented Phase 1 security baseline:
- only the launcher Activity is exported
- no sensitive Android permissions are declared
- global cleartext traffic is disabled
- application backup is disabled
- production configuration requires HTTPS
- backend URL rejects embedded credentials and unexpected path/query/fragment data
- production diagnostics are disabled
- feature flags default to disabled
- no authentication or enrollment secrets exist in source
- no device-management or surveillance APIs are initialized

Future sensitive functionality must use legitimate Android/Android Enterprise APIs and required platform authorization.

## Testing

JUnit 4 is the existing JVM test framework.

Tests cover configuration validity and invalid configuration, environment separation, HTTPS enforcement, backend URL credential/path rejection, feature-flag defaults, release/debug diagnostic boundaries, ViewModel state transitions, ViewModel state restoration, navigation destination stability, navigator transitions, and domain architecture contracts.

Tests do not use production credentials, production endpoints, backend services, or device-specific state.

## Implemented

- Android/Gradle foundation
- domain architecture contracts
- centralized configuration
- environment separation
- controlled logging
- security baseline
- lifecycle-safe UI shell
- deterministic navigation boundary
- explicit UI state model
- SavedStateHandle restoration
- JVM tests
- CI verification for tests, lint, debug build, and release build

## Planned

Future phases may define authenticated backend contracts, enrollment, device identity, policy synchronization, and authorized management operations.

## Deferred

- authentication
- enrollment or QR pairing
- backend API integration
- WebSockets or FCM
- location
- camera
- microphone/audio capture
- screen capture/sharing
- application blocking
- Play Store restrictions
- website/DNS/VPN filtering
- Device Owner or Android Enterprise provisioning
- device locking
- remote wipe
- policy enforcement
- covert monitoring
- surveillance
- permission or security bypasses

## Cross-repository relationship

Parento Managed -> Parento Backend <- Parento Admin

The managed app will eventually authenticate/enroll and communicate with the backend. The Admin app will consume backend-mediated administrator contracts. Those protocols are not implemented in Phase 1.5 and this repository does not modify either companion repository.

## Phase 2.1 — Local Persistence

Phase 2.1 adds Room as the managed app's local relational persistence technology.

Persistence flow:

UI → ViewModel → Domain / Use Case → Repository → Local Data Source → Room Database

The UI does not access Room directly, and the database has no UI dependencies.

### Database

ParentoDatabase contains only the minimal baseline entity LocalApplicationStateEntity. It stores stateVersion, lastSynchronizationTimestamp, and initialized. No enrollment, authentication, device-control, sensor, policy, command, location, or audit entities were created.

LocalDatabaseProvider initializes the database once using applicationContext and exposes isolated test setup/cleanup hooks.

### Repository

LocalStateRepository abstracts reading, writing, clearing, and observing local state. RoomLocalStateRepository maps Room entities to domain data and converts storage failures into the existing ManagedError.STORAGE_FAILURE result instead of exposing raw database exceptions to UI.

DAO operations are suspend functions or Flow. No arbitrary thread pool or main-thread database operation is used by production code.

### Schema and migration boundary

Room database versioning is enabled at version 1 with schema export configured under schemas/. No fake future migration was added. Future schema changes must increment the Room version and provide an explicit deterministic migration. Destructive migration is not enabled.

### Security

Phase 2.1 stores no credentials or secrets. Storage contents are not logged. Android backup remains disabled and no sensitive Android permissions were added. Future sensitive state must use appropriate Android secure-storage/cryptographic facilities in its relevant phase; no custom cryptographic protocol is introduced.

### Testing

JVM tests cover the local-state model and storage error contract. Android instrumentation tests use an in-memory Room database and cover initialization, write/read, update, clear, reactive observation, and safe result typing.

The in-memory test database is isolated from the application's persistent database.

### Backend boundary

Phase 2.1 does not implement REST, authentication, enrollment API calls, WebSockets, FCM, backend synchronization, or device commands. Local persistence remains independent of future backend synchronization.
