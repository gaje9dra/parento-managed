# Phase 2.4 — Local State Services & Persistence Operational Readiness

## Repository boundary

Only `gaje9dra/parento-managed` is modified. Backend and admin repositories are dependencies for later phases only.

## Local state architecture

The existing Android Views UI architecture is preserved. The local persistence boundary is:

```text
UI / ViewModel
      ↓
Local State Service
      ↓
LocalStateRepository
      ↓
LocalApplicationStateDao
      ↓
Room
```

UI and ViewModels do not construct Room databases or access DAOs directly.

## Initialization

Application startup initializes configuration and the application-scoped Room database, then starts local-state initialization on the application IO scope. Initialization creates a UUID identity when missing, marks local state initialized, preserves the default enrollment state `UNENROLLED`, and resets runtime connection state to `UNKNOWN`.

Initialization is idempotent and serialized. Repeated initialization preserves the existing installation UUID and creation timestamp.

## Identity recovery

If a valid installation identity is absent, the local service recreates a UUID and persists it. Recovery does not claim enrollment, create backend registration, create authentication credentials, or establish a remote connection. Storage failures remain application-level `STORAGE_FAILURE` results.

## State lifecycle

Enrollment state keeps the existing validated lifecycle:

```text
UNENROLLED → ENROLLING → ENROLLED
                         ↓
                 CONNECTED / DISCONNECTED
```

`REVOKED` remains terminal and `ERROR` retains its existing recovery transitions.

Connection state is runtime-only. It begins at `UNKNOWN` after process recreation and is never restored from Room, so stale connection information cannot be mistaken for a live connection.

## Persistence lifecycle

The application owns one Room database instance for the process lifetime. Repository read-modify-write operations are serialized with a coroutine `Mutex`. DAO operations remain suspend/Flow based and are not accessed by the UI layer.

## Migration

Room is now at schema version 3. The explicit v1 → v2 migration adds identity/enrollment fields, and v2 → v3 removes the runtime-only connection column while preserving restart-safe state. No destructive migration is enabled. Instrumented migration tests cover clean upgrade paths and preservation of persistent state.

## Backup and restore

`android:allowBackup="false"` remains enabled in the manifest. Local state is not a supported backup/restore channel. Future authentication, enrollment, trust, and remote relationships must be re-established through legitimate workflows.

## Logging

Startup logs only generic initialization success/failure categories. Installation IDs, credentials, tokens, and future monitoring data are not logged.

## Testing

Phase 2.4 adds service tests for first/repeated initialization, missing identity recovery, storage failure propagation, and stale persisted connection recovery. Existing repository/instrumentation tests continue to cover identity stability, persistence, transitions, Flow observation, migration, and isolated Room databases.

## Deferred

Backend communication, authentication, enrollment/pairing, provisioning, realtime communication, monitoring/telemetry, location, camera/microphone/audio, screen capture/sharing, device controls, app blocking, website/DNS/VPN filtering, policy synchronization/enforcement, and remote commands remain deferred.


## Phase 2.5 hardening note

Phase 2.5 treats the Room database as the source of truth only for restart-safe local state. Current connection status is held in the service runtime state and is validated through the existing connection transition model. Persistence writes cannot accidentally turn a transient CONNECTED state into durable state.
