# Phase 4.1 — Managed Device Foundation & Device-Management Architecture

## Scope

This phase modifies only `gaje9dra/parento-managed`. The backend and Admin repositories are unchanged.

## Architecture

The Managed application now has explicit boundaries:

```text
UI
 ↓
ViewModel
 ↓
Domain / Use Cases
 ↓
Device Management Layer
 ↓
Policy Engine
 ↓
Command Execution Boundary
 ↓
Android Platform APIs
```

Supporting boundaries:

```text
Communication Layer → Backend REST / future realtime / push
Local Data Layer   → Room / secure local state
Permission Layer   → Android runtime permission state
```

The implementation keeps the existing Phase 1–2 Room and UI architecture rather than replacing it.

## Android Enterprise / Device Owner boundary

`AndroidDeviceManagementPlatform` is the only implementation that directly queries `DevicePolicyManager` in Phase 4.1.

The application can determine:

- `NOT_MANAGED`
- `PROFILE_OWNER`
- `DEVICE_OWNER`
- `UNKNOWN`

The application does not provision itself, set itself as Device Owner, invoke hidden APIs, use root, or bypass Android provisioning.

A `DeviceAdminReceiver` and device-admin metadata are declared as a platform readiness boundary only. No policy is enabled and no remote control operation is implemented.

Android's documented DPC model uses Device Owner/Profile Owner roles and managed provisioning; those roles are established by Android Enterprise provisioning rather than by application code. urlAndroid DevicePolicyManager documentationhttps://developer.android.com/reference/android/app/admin/DevicePolicyManager

## Management state

Enrollment and Android management mode remain separate concepts.

Enrollment remains:

- `UNENROLLED`
- `ENROLLING`
- `ENROLLED`
- `CONNECTED`
- `DISCONNECTED`
- `REVOKED`
- `ERROR`

Android management mode is represented separately.

This permits an enrolled device to be temporarily or permanently unmanaged by Android Enterprise.

## Capability model

The foundation models:

- Device Owner
- Profile Owner
- Managed Configuration
- Policy Support
- Lock Capability
- App Management Capability
- Network Restriction Capability
- Screen Capture Capability
- Camera Capability
- Microphone Capability
- Location Capability

Each capability can be:

- `AVAILABLE`
- `UNAVAILABLE`
- `REQUIRES_AUTHORIZATION`
- `REQUIRES_PERMISSION`
- `NOT_SUPPORTED`

Sensitive capabilities are not activated in Phase 4.1.

## Persistence

The existing Room state now also persists:

- last detected management mode
- last evaluated capability snapshot
- management-state evaluation timestamp

The persisted installation identity remains independent from authentication identity and is not treated as proof of authorization.

Room schema version is now 4 with an explicit 3 → 4 migration.

Transient connection state remains runtime-only.

## Permission boundary

`PermissionManager` centralizes future runtime-permission state.

It models:

- granted
- denied
- not requested
- requires user action
- not applicable

No camera, microphone, location, or media-projection permission is requested in this phase, and no new sensitive manifest permissions were added.

Android recommends requesting runtime permissions in the context of the feature that needs them and handling denial without assuming the permission will be granted. urlAndroid runtime permission guidancehttps://developer.android.com/training/permissions/requesting

## Policy engine

The policy engine is an architectural boundary only.

A policy definition currently contains:

- policy ID
- policy type
- enabled state
- configuration
- version
- updated timestamp
- source

Only structural validation exists. No final parental policy schema or enforcement is implemented.

## Command boundary

Future commands are represented separately from execution:

```text
Command
 ↓
Validator
 ↓
Authorization
 ↓
Executor
 ↓
Device Management Platform
```

Commands have identity, type, version, expiration, and execution-state concepts.

Phase 4.1 authorization fails closed because no remote authorization source exists yet. No remote command is delivered or executed.

## Communication boundary

The Managed application exposes separate conceptual boundaries for:

- REST API
- realtime communication
- push delivery
- command delivery
- status reporting

No complete backend communication, direct Admin ↔ Managed communication, or realtime command delivery was implemented.

The intended topology remains:

```text
Admin App
   ↓
Backend
   ↓
Managed App
```

## Background execution

The existing background scheduler remains a placeholder. No permanent service or covert persistence mechanism was introduced.

Future synchronization, policy updates, status reporting, connectivity recovery, and command processing should use Android-supported lifecycle/background mechanisms appropriate to the operation.

## Lifecycle

Managed-device initialization is deterministic and serialized:

```text
Application startup
 ↓
Local state initialization
 ↓
Android platform integration
 ↓
Management-mode detection
 ↓
Capability evaluation
 ↓
Policy subsystem boundary
 ↓
Communication boundary
 ↓
Application state
```

Initialization is idempotent and safe to repeat.

The app does not assume that its process remains alive continuously.

## UI

The existing status UI now exposes only foundation information:

- management mode
- enrollment state
- connection state

No remote device-control dashboard or controls were added.

## Security

Phase 4.1 preserves:

- cleartext traffic disabled
- backup disabled
- stable application-generated installation identity
- no hardware fingerprinting
- no plaintext credentials
- no sensitive permission requests
- no direct UI-to-`DevicePolicyManager` calls
- command authorization fail-closed behavior
- controlled Android platform boundary

No covert surveillance, hidden camera/microphone access, hidden screen capture, accessibility abuse, root exploits, bootloader exploits, anti-uninstall bypasses, stealth persistence, credential theft, or unauthorized remote access were added.

## Testing

Added coverage for:

- management mode detection
- capability status mapping
- deterministic/idempotent management initialization
- policy validation boundary
- command expiry validation
- fail-closed command authorization
- Room 3 → 4 management metadata migration

Existing Phase 1–2 persistence, lifecycle, configuration, navigation, security, and migration tests remain in place.

## Deferred work

Phase 4.1 intentionally does not implement:

- backend enrollment
- QR pairing
- device pairing
- remote commands
- live location
- camera streaming
- microphone/audio capture
- screen sharing
- application blocking
- website blocking
- remote lock
- remote wipe
- device restrictions
- parental policies
- Admin dashboard integration
- realtime command delivery
- automatic Device Owner provisioning

Those require later phases and, where applicable, backend/Admin contracts.

## Cross-repository dependencies

No backend or Admin changes were made.

Future phases will require backend contracts for enrollment, authorization, command delivery, and status synchronization. Phase 4.1 establishes local boundaries only.

## Verification

Required verification commands:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

If the repository environment cannot run a command, report it as `BLOCKED` rather than treating it as a pass.
