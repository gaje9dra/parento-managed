# Phase 2.3 — Managed Persistence Integrity & State Repository Contracts

## Repository boundary

Only gaje9dra/parento-managed is changed in this phase. No backend or admin implementation belongs here.

## Architecture

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

Room entities and DAOs remain hidden from UI and ViewModel layers. The repository maps persistence records to domain models and converts storage failures into ManagedError.STORAGE_FAILURE.

## Persistent vs runtime state

Persisted:
- application-scoped installation identity
- identity creation timestamp
- enrollment lifecycle state
- last-known connection state
- local initialization metadata
- state version and synchronization metadata

Runtime-only:
- loading indicators
- temporary error presentation
- active navigation/screen
- transient connection activity

A persisted connection value is a last-known value. It does not prove that a live backend connection exists after restart.

## Lifecycle integrity

The existing EnrollmentState model is preserved:

UNENROLLED
ENROLLING
ENROLLED
CONNECTED
DISCONNECTED
REVOKED
ERROR

Valid local transitions:

UNENROLLED → ENROLLING | ERROR
ENROLLING → UNENROLLED | ENROLLED | ERROR
ENROLLED → CONNECTED | DISCONNECTED | REVOKED | ERROR
CONNECTED → DISCONNECTED | REVOKED | ERROR
DISCONNECTED → CONNECTED | REVOKED | ERROR
REVOKED → terminal
ERROR → UNENROLLED | ENROLLING

The repository rejects invalid transitions with ManagedError.INVALID_STATE. This is only a local integrity contract; it does not perform enrollment or establish connections.

ConnectionState is kept separate:

UNKNOWN → DISCONNECTED | CONNECTING
DISCONNECTED → CONNECTING
CONNECTING → CONNECTED | DISCONNECTED
CONNECTED → DISCONNECTED

A fresh installation therefore cannot become CONNECTED directly through the repository.

## Local identity

The existing application-generated UUID strategy is preserved.

The identity:
- remains stable across normal application restarts
- is application-scoped
- is independent of hardware identifiers
- is generated only when absent
- is testable
- is serialized during initialization to prevent duplicate creation from concurrent repository callers

No IMEI, serial number, MAC address, advertising ID, or equivalent hardware identifier is used.

## Repository contract

LocalStateRepository exposes only domain-oriented operations:
- read
- write
- clear
- observe
- getOrCreateIdentity
- updateEnrollmentState
- updateConnectionState

Room entities, DAOs, SQLite APIs, and Android database implementation details are not exposed.

## Persistence error handling

Malformed persisted enum values and database/storage failures become ManagedError.STORAGE_FAILURE.

Invalid lifecycle transitions become ManagedError.INVALID_STATE.

Raw Room/SQLite exceptions are not exposed to UI.

## Database and migrations

Room remains at schema version 2.

No schema change is required for Phase 2.3 because Phase 2.2 already contains the required singleton state fields.

The existing deterministic MIGRATION_1_2 is preserved. Existing data is retained and safe defaults are used for newly added fields. No destructive migration is enabled.

## Initialization and recovery

Fresh local state is:
- enrollment: UNENROLLED
- connection: UNKNOWN
- identity: absent until generated
- initialized: false

The repository never converts a storage failure into an enrolled or connected state.

A missing row is treated as an empty local state for controlled state updates. Identity initialization creates the singleton state only when necessary.

## Backup and restore

Application backup remains disabled from the existing security baseline.

Persisted Phase 2.3 state contains no authentication/session credentials or enrollment authorization. Restored local data therefore cannot establish backend authentication or remote trust by itself.

Future security-sensitive state must be re-established through its legitimate workflow.

## Testing

Phase 2.3 adds JVM tests for lifecycle transition contracts and isolated Android repository tests for:
- identity stability
- UUID format
- repeated identity initialization
- valid enrollment transitions
- invalid enrollment transitions
- valid connection transitions
- invalid connection transitions
- persistence round trips
- observation
- safe application-level error results

Existing Room migration tests remain in place.

## Performance and concurrency

Room writes remain suspendable and are not performed on the UI thread.

Flow is used for observation instead of polling.

Identity creation is serialized with a coroutine Mutex within the repository instance. No complex locking mechanism is introduced.

Transient UI state is not persisted.

## Deferred functionality

Backend communication, authentication, enrollment/pairing, QR provisioning, Device Owner setup, monitoring, location, camera, microphone, audio, screen capture/sharing, device controls, app blocking, website/DNS/VPN filtering, policies, synchronization, and remote commands remain deferred.
