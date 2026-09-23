# Parento Managed

Parento Managed is the Android application installed on an explicitly enrolled and authorized device in the Parento platform.

## Repository boundary

This repository is only the managed-device Android application:
gaje9dra/parento-managed

The companion repositories are not modified by this phase:
- gaje9dra/parento-admin
- gaje9dra/parento-backend

## Current phase

Phase 2.5 — Local Persistence Security, Testing & Phase 2 Completion

Phase 2.3 strengthens the Phase 2.1/2.2 Room persistence layer with explicit local lifecycle transitions, repository/domain separation, deterministic identity initialization, safe error translation, and isolated integrity tests.

No backend communication, authentication, enrollment workflow, remote commands, monitoring, screen sharing, camera, microphone, audio, app blocking, website blocking, or policy enforcement is implemented.

## Persistence architecture

The persistence boundary is:

Room
  ↓
DAO
  ↓
Repository
  ↓
Domain
  ↓
ViewModel
  ↓
UI

Room entities are never exposed to the UI. Repository methods return domain models or OperationResult.

## Persistent vs runtime state

Persisted state includes:
- application-scoped installation identity
- identity creation timestamp
- enrollment lifecycle state
- initialization and synchronization metadata

Runtime-only state includes:
- current connection state
- loading indicators
- temporary error presentation
- navigation state
- transient connection activity

A persisted connection state is only the last known state. It does not prove that a live backend connection exists after application restart.

## Local lifecycle state

The existing enrollment lifecycle model remains:

- UNENROLLED
- ENROLLING
- ENROLLED
- CONNECTED
- DISCONNECTED
- REVOKED
- ERROR

The repository validates transitions so a fresh UNENROLLED state cannot jump directly to CONNECTED.

The separate connection-state model remains:
- UNKNOWN
- DISCONNECTED
- CONNECTING
- CONNECTED

No actual connection mechanism is implemented in this phase.

## Local identity

The managed application uses an application-generated UUID as its stable local installation identifier.

It:
- survives normal application restarts
- is independent of transient UI state
- does not use IMEI, serial number, MAC address, advertising ID, or other hardware identifiers
- is generated only when no identity exists

Identity initialization is serialized within the repository to prevent duplicate creation from concurrent callers.

## Database

Room database: parento-managed.db

Current schema version: 3

Entity:
- LocalApplicationStateEntity

DAO:
- LocalApplicationStateDao

The singleton primary key keeps local application state to one record.

The explicit MIGRATION_1_2 and MIGRATION_2_3 paths are preserved. MIGRATION_2_3 removes the previously persisted runtime connection column without losing restart-safe state. No destructive migration is enabled.

## Error handling

Storage failures map to ManagedError.STORAGE_FAILURE.

Invalid local state transitions map to ManagedError.INVALID_STATE.

Malformed persisted enum values are treated as storage failures.

Raw Room/SQLite exceptions are not exposed to UI.

## Backup and security

Application backup remains disabled.

The persisted state contains no passwords, authentication tokens, refresh tokens, backend secrets, enrollment secrets, or private keys.

No sensitive Android permissions were added for Phase 2.3.

## Testing

Phase 2.5 adds:
- lifecycle transition contract tests
- invalid-transition repository tests
- repeated identity initialization tests
- UUID-format verification
- persistence/observation tests

Existing isolated Room migration tests remain in place.

## Build and verification

The repository does not contain a Gradle wrapper. Use the repository-compatible Gradle/Android Studio environment.

Expected commands:

    ./gradlew test
    ./gradlew lint
    ./gradlew connectedDebugAndroidTest
    ./gradlew assembleDebug
    ./gradlew assembleRelease

Do not treat these commands as completed unless they have actually been executed.

## Deferred functionality

The following remain intentionally unimplemented:
- backend REST communication
- Retrofit
- WebSockets
- FCM
- authentication
- tokens/passwords/OAuth/sessions
- QR enrollment/pairing
- provisioning/Device Owner setup
- live monitoring/telemetry
- location
- camera/microphone/audio
- screen capture/sharing
- device locking/reboot/wipe/kiosk
- app blocking
- website/DNS/VPN filtering
- policy synchronization/enforcement
- remote command execution
- covert monitoring or security bypasses

Future sensitive capabilities must use legitimate Android/Android Enterprise APIs and explicit authorization.


## Phase 2.5 completion audit

The Phase 2.5 pass reviewed Room initialization, entity/DAO boundaries, repository serialization, local identity stability, persistent/runtime state separation, migration safety, corruption handling, concurrency/idempotency, backup behavior, permissions, manifest security, logging, dependency configuration, and test isolation.

Runtime connection state is deliberately not persisted. After process recreation it starts at UNKNOWN; persisted enrollment and identity state remain available for deterministic startup reconstruction.

The repository does not implement authentication, backend communication, enrollment, realtime communication, FCM, remote commands, monitoring, location, camera, microphone/audio, screen capture, application/website blocking, device restrictions, or remote policies.

Android Room guidance recommends explicit migration paths when preserving existing on-device data and warns that destructive migration can permanently delete data when used as a fallback. This project therefore keeps explicit migrations and does not enable destructive migration. urlAndroid Room migration guidancehttps://developer.android.com/training/data-storage/room/migrating-db-versions
