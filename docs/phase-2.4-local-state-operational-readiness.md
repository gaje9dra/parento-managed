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

Persisted connection state is last-known state only. Runtime connection state begins at `UNKNOWN` after process recreation, so stale persisted `CONNECTED` never becomes proof of a live connection.

## Persistence lifecycle

The application owns one Room database instance for the process lifetime. Repository read-modify-write operations are serialized with a coroutine `Mutex`. DAO operations remain suspend/Flow based and are not accessed by the UI layer.

## Migration

Room remains at schema version 2 with the explicit v1 → v2 migration. No destructive migration is enabled. Existing migration tests cover preservation and safe defaults.

## Backup and restore

`android:allowBackup="false"` remains enabled in the manifest. Local state is not a supported backup/restore channel. Future authentication, enrollment, trust, and remote relationships must be re-established through legitimate workflows.

## Logging

Startup logs only generic initialization success/failure categories. Installation IDs, credentials, tokens, and future monitoring data are not logged.

## Testing

Phase 2.4 adds service tests for first/repeated initialization, missing identity recovery, storage failure propagation, and stale persisted connection recovery. Existing repository/instrumentation tests continue to cover identity stability, persistence, transitions, Flow observation, migration, and isolated Room databases.

## Deferred

Backend communication, authentication, enrollment/pairing, provisioning, realtime communication, monitoring/telemetry, location, camera/microphone/audio, screen capture/sharing, device controls, app blocking, website/DNS/VPN filtering, policy synchronization/enforcement, and remote commands remain deferred.
