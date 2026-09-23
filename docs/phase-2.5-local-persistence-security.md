# Phase 2.5 — Local Persistence Security, Testing & Phase 2 Completion

## Repository

gaje9dra/parento-managed

## Scope

This phase completes the Managed Android App portion of Phase 2. It hardens local persistence, state integrity, migration safety, identity stability, concurrency, logging, backup boundaries, permissions, and verification without implementing Phase 3 functionality.

## Audited

- Room database initialization and single-database boundary
- DAO/repository/domain separation
- entity nullability, defaults, and singleton identity
- local installation identity creation and recovery
- persistent versus runtime state separation
- lifecycle transition validation
- idempotent initialization and repository serialization
- Room migration preservation and upgrade paths
- database failure handling
- Android backup configuration
- manifest and sensitive-permission boundary
- production/debug configuration separation
- logging and diagnostic boundaries
- dependency configuration
- unit and instrumented test isolation

## Key hardening decision

Connection state is runtime state, not durable state. The Room entity therefore stores installation identity, identity creation time, enrollment lifecycle, initialization metadata, and synchronization metadata, but not current connection status. Schema version 3 removes the obsolete connection column through an explicit v2 → v3 migration.

After process recreation the runtime connection state begins at UNKNOWN. A persisted enrollment state or identity never implies a live backend connection.

## Migration safety

The migration chain is:

1. v1 → v2: add installation identity, identity creation timestamp, enrollment state, and the legacy connection column.
2. v2 → v3: preserve all restart-safe fields and remove the runtime-only connection column.

No destructive migration fallback is configured. Existing data is preserved through explicit migration code and instrumented tests.

## Identity safety

The application-generated UUID remains stable across ordinary restarts. Identity creation is serialized and idempotent. The identity is a local installation identifier, not an authentication credential. No custom cryptography or hardware-derived identifiers are introduced.

## State integrity

Enrollment transitions continue to be validated at the persistence boundary. Runtime connection transitions are validated independently and are not written to Room. Invalid transitions return the existing INVALID_STATE result rather than silently changing state.

## Backup and permissions

Application backup remains disabled. Phase 2 does not add camera, microphone, location, media, screen-capture, accessibility, notification-access, VPN, device-administration, or other future-sensitive permissions.

## Phase boundary

Not implemented in Phase 2.5:

- authentication
- passwords, JWTs, sessions, or tokens
- backend communication
- enrollment or pairing
- device registration/provisioning
- FCM or WebSockets
- remote commands
- monitoring/telemetry
- location
- camera/microphone/audio
- screen sharing/capture
- application or website blocking
- VPN filtering
- device restrictions
- remote policies
- future command audit/event logging
- admin dashboard functionality

## Verification

The repository workflow is configured to run unit tests, lint, instrumented persistence tests on an Android emulator, debug build, and release build. Results are reported only after the corresponding CI run actually completes.

## Cross-repository dependencies

None discovered for this phase. The work is confined to gaje9dra/parento-managed.
