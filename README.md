# Parento Managed

Parento Managed is the Android application installed on an explicitly enrolled and authorized device in the Parento platform.

## Repository boundary

This repository is only the managed-device Android application:
gaje9dra/parento-managed

The companion repositories are not modified by this phase:
- gaje9dra/parento-admin
- gaje9dra/parento-backend

## Current phase

Phase 5.2 — Managed Android Secure Enrollment & Pairing Client Foundation

Phase 2.5 strengthens the Phase 2.1/2.2 Room persistence layer with explicit local lifecycle transitions, repository/domain separation, deterministic identity initialization, safe error translation, and isolated integrity tests.

The Managed app now has a dedicated enrollment client boundary for the Phase 5.1 backend consume contract. Remote commands, monitoring, screen sharing, camera, microphone, audio, app blocking, website blocking, and policy enforcement remain unimplemented.

## Identity and state architecture

The Managed application keeps these concepts separate:

- local installation identity (`installationId`)
- backend-assigned Managed device identity (`managedDeviceId`, nullable)
- Android management identity (`NOT_MANAGED`, `PROFILE_OWNER`, `DEVICE_OWNER`, `UNKNOWN`)
- enrollment state
- connection state
- UI state

Android management remains platform-authoritative. Enrollment and connection state cannot overwrite Device Owner/Profile Owner detection.

The installation ID is a locally generated UUID persisted through the Room repository. It is not authorization proof and is not derived from hardware identifiers. Identity initialization is serialized so concurrent callers receive the same identity.

A nullable `managedDeviceId` is reserved for a future backend-assigned identity. Phase 4.4 never invents one.

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
- nullable backend-assigned managed-device identity
- enrollment lifecycle state
- independent connection-state snapshot
- Android management diagnostic metadata
- initialization and synchronization metadata

Connection state remains independent from enrollment. Startup reconstructs the live connection state as UNKNOWN so stale CONNECTED data cannot be treated as proof of a live backend connection.

## Local lifecycle state

The enrollment lifecycle model is:

- UNENROLLED
- ENROLLING
- ENROLLED
- REVOKED
- ERROR

Enrollment and connection are deliberately separate. The connection-state model remains:
- UNKNOWN
- DISCONNECTED
- CONNECTING
- CONNECTED

## Managed Device Architecture

Android management state is authoritative from `DevicePolicyManager`. The managed app does not infer Device Owner or Profile Owner status from installation, backend enrollment, local flags, or cached state.

The platform boundary is:

UI
  ↓
ViewModel
  ↓
Managed-device state
  ↓
DeviceManagementManager / ManagementModeDetector
  ↓
AndroidDeviceManagementPlatform
  ↓
DevicePolicyManager

`Device Owner`, `Profile Owner`, `NOT_MANAGED`, and `UNKNOWN` are separate management modes. An `UNKNOWN` result carries a structured detection error and fails closed.

The `DeviceAdminReceiver` is a minimal Android platform foundation only. It is protected by `BIND_DEVICE_ADMIN`, uses the required device-admin metadata, and does not execute remote commands or sensitive operations.

Capability discovery distinguishes platform authorization from features that are merely planned. Phase 4.2 reports future control/monitoring capabilities as unavailable or not supported rather than claiming them from code paths alone.

## Security

Parento uses legitimate Android Enterprise / Device Owner and Profile Owner mechanisms. No root, hidden APIs, Accessibility abuse, covert sensor activation, covert capture, anti-uninstall bypass, fake Device Owner state, or unauthorized settings manipulation is implemented.

Persisted management metadata is diagnostic/UI state only. Every application initialization refreshes management status from Android so a reboot or reinstall cannot turn cached data into proof of Device Owner status.

The Device Admin receiver requires the system-only `android.permission.BIND_DEVICE_ADMIN` permission. No privileged or system-only application permissions were added.

## Testing

Unit coverage includes:
- Device Owner detection
- Profile Owner detection
- unmanaged state
- unsupported platform/API state
- security exceptions
- unexpected platform failures
- conflicting Device Owner/Profile Owner signals
- capability fail-closed behavior
- lifecycle-safe management initialization

Instrumentation coverage retains the Room migration tests. A specially provisioned Device Owner test device is not required for normal unit tests.

For legitimate manual Device Owner verification, provision the application using a supported Android Enterprise provisioning flow or an appropriate development/test mechanism. Do not grant Device Owner status from inside the app or bypass Android provisioning.

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

Current schema version: 5

Entity:
- LocalApplicationStateEntity

DAO:
- LocalApplicationStateDao

The singleton primary key keeps local application state to one record.

The explicit MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, and MIGRATION_4_5 paths are preserved. MIGRATION_4_5 adds independent connection-state and nullable managed-device identity columns without destructive migration.

## Error handling

Storage failures map to ManagedError.STORAGE_FAILURE.

Invalid local state transitions map to ManagedError.INVALID_STATE.

Malformed persisted enum values are treated as storage failures.

Raw Room/SQLite exceptions are not exposed to UI.

## Backup and security

Application backup remains disabled.

Room contains no passwords, authentication tokens, refresh tokens, or private keys. Temporary Phase 5.2 enrollment authorization material is stored separately using Android encrypted preferences backed by a MasterKey and is cleared after successful completion.

No sensitive Android permissions were added for Phase 5.2.

## Testing

Phase 2.5 adds:
- lifecycle transition contract tests
- invalid-transition repository tests
- repeated identity initialization tests
- UUID-format verification
- persistence/observation tests
- malformed persisted identity recovery tests

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

## Phase 5.2 status

The Managed application now has a user-visible enrollment foundation. Enrollment ID, one-time authorization secret, expiry, and required device name are validated before submission. The enrollment repository serializes operations, persists temporary authorization securely, submits only the Phase 5.1 documented consume fields, persists the backend ManagedDevice ID only after successful completion, and keeps enrollment state separate from Android management state.

## Deferred functionality

The following remain intentionally unimplemented:
- general backend REST communication beyond the dedicated Phase 5.1 enrollment consume boundary
- Retrofit migration
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


## Phase 4.4 completion audit

The Phase 2.5 pass reviewed Room initialization, entity/DAO boundaries, repository serialization, local identity stability, persistent/runtime state separation, migration safety, corruption handling, concurrency/idempotency, backup behavior, permissions, manifest security, logging, dependency configuration, and test isolation.

Runtime connection state is deliberately not persisted. After process recreation it starts at UNKNOWN; persisted enrollment and identity state remain available for deterministic startup reconstruction.

The repository does not implement authentication, backend communication, enrollment, realtime communication, FCM, remote commands, monitoring, location, camera, microphone/audio, screen capture, application/website blocking, device restrictions, or remote policies.

Android Room guidance recommends explicit migration paths when preserving existing on-device data and warns that destructive migration can permanently delete data when used as a fallback. This project therefore keeps explicit migrations and does not enable destructive migration. urlAndroid Room migration guidancehttps://developer.android.com/training/data-storage/room/migrating-db-versions


## Phase 4.4 — Lifecycle & Reliability

The Managed app now has centralized startup state, lifecycle-aware management refresh, event-driven connectivity observation, and a WorkManager boundary for explicitly scheduled future jobs. Initialization and state recovery remain Android-supported and fail-closed. See `docs/phase-4.4-lifecycle-reliability.md`.


## Phase 5.2 enrollment documentation

See `docs/phase-5.2-enrollment-client.md`.

The Phase 5.1 backend contract is intentionally consumed read-only from this repository. The Managed app does not use administrator authentication. Backend create/list/status/cancel endpoints remain Admin-only.

The Managed app's local Cancel action clears temporary local authorization and returns local enrollment state to UNENROLLED; it does not claim that the administrator-side backend session was cancelled.

A backend recovery/status operation for a Managed installation is not part of Phase 5.1. If a process dies after backend consumption commits but before the client persists the response, the client fails closed rather than guessing the ManagedDevice ID. Automatic retry is therefore intentionally disabled.
