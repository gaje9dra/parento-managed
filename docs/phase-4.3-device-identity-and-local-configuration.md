# Phase 4.3 — Device Identity, Enrollment State & Secure Local Configuration

## Scope

Phase 4.3 is limited to `gaje9dra/parento-managed`.

It establishes the local foundation needed for a future secure enrollment/pairing flow. It does not implement enrollment, pairing, backend registration, remote commands, realtime communication, monitoring, or device-control features.

## Identity model

### Installation identity

`installationId` is a locally generated UUID representing this Parento Managed installation.

- generated only when needed
- persisted in the Room local-state record
- stable across normal process restarts
- protected from concurrent duplicate generation by the repository mutex
- not derived from IMEI, phone number, MAC address, advertising ID, serial number, or other hardware identifiers
- not an authorization credential

A missing identity in an unmanaged/un-enrolled fresh local state may be generated. If identity storage is missing while enrollment or a backend-assigned managed-device identity already exists, recovery fails closed with a storage error rather than silently creating a duplicate installation.

### Managed-device identity

`managedDeviceId` is nullable local state reserved for the future backend-assigned identifier.

Phase 4.3 never invents or generates it.

### Android management identity

`ManagementMode` remains authoritative from Android:

- `NOT_MANAGED`
- `PROFILE_OWNER`
- `DEVICE_OWNER`
- `UNKNOWN`

Cached management metadata never overrides a fresh `DevicePolicyManager` result.

## State separation

The project now keeps these concepts independent:

| Concept | Owner | Examples |
|---|---|---|
| Android management | Android platform detector | Device Owner, Profile Owner |
| Installation identity | Local identity repository | installationId |
| Enrollment | Enrollment domain | UNENROLLED, ENROLLING, ENROLLED, REVOKED, ERROR |
| Connection | Communication/runtime state | UNKNOWN, CONNECTING, CONNECTED, DISCONNECTED |
| UI | ViewModel | immutable presentation state |

Connection state is not encoded as enrollment state. Therefore an enrolled device can be disconnected without becoming unenrolled.

## Persistence

Room schema is version 5.

The local singleton record contains:

- installation identity and creation timestamp
- nullable managed-device identity
- enrollment state
- independent connection state
- Android management metadata
- initialization/synchronization metadata

Migration `4 → 5` adds independent connection-state and managed-device identity columns without destructive migration.

## Repository boundaries

The local data layer exposes explicit contracts for:

- `DeviceIdentityRepository`
- `EnrollmentStateRepository`
- `ConnectionStateRepository`
- `ManagedDeviceStateRepository`

The existing `LocalStateRepository` implementation composes these boundaries while retaining the Room persistence boundary. UI code does not access Room directly.

The managed-device identity is read-only at this stage. A future enrollment flow will be responsible for assigning the backend-provided identifier.

## State transitions

Enrollment transitions are:

```
UNENROLLED → ENROLLING → ENROLLED → REVOKED
```

with explicit `ERROR` handling.

Connection transitions remain independent:

```
UNKNOWN → DISCONNECTED → CONNECTING → CONNECTED
```

and reconnect/disconnect transitions are handled by the connection-state contract.

A connection failure does not mutate enrollment state.

## Secure local configuration

Build-time configuration is centralized through `BuildConfiguration` and `AppConfig`.

Current configuration includes:

- environment
- backend base URL
- logging configuration
- feature flags
- HTTPS/debug-diagnostics security configuration

No real credentials are stored in source, Git, BuildConfig values, or local Room state.

The repository currently has no enrollment/session secret to persist. The existing `SecurityStore` contract remains the security boundary for future sensitive request material; custom cryptography is not introduced in this phase.

## Backup

Android application backup remains disabled with `android:allowBackup="false"`.

The current persisted local-state record contains no authentication tokens, passwords, private keys, enrollment secrets, or other credential material. Future sensitive enrollment/session data must not be placed in ordinary local-state columns or restored onto another device.

## Logging

Identity values and future credentials are not logged. Initialization logs report only generic success/failure messages.

Debug diagnostics remain environment-controlled and production diagnostics are disabled by build configuration.

## Lifecycle

### Process restart

The persisted installation identity and enrollment state are recovered. Connection state is reconstructed as runtime state and starts from `UNKNOWN` after process initialization.

### Reinstall

A complete uninstall may remove local persistence. The architecture therefore allows a fresh installation to receive a new installation ID. No hidden persistence mechanism is used.

### Factory reset

The application does not assume local identity survives an Android factory reset. Device Owner provisioning and Parento enrollment remain separate lifecycle concepts.

## Security boundary

Phase 4.3 does not add:

- hardware fingerprinting
- root access
- hidden APIs
- Accessibility abuse
- covert camera/microphone/location/screen capture
- remote commands
- anti-uninstall bypass
- QR enrollment or pairing
- backend enrollment APIs
- remote policy enforcement

## Verification

The repository does not contain a Gradle wrapper. CI uses repository-compatible Gradle 8.13.

Required verification commands:

```text
gradle test
gradle lint
gradle connectedDebugAndroidTest
gradle assembleDebug
gradle assembleRelease
```

This document records the intended verification commands; the final phase report must use the actual CI results and must not infer success from source inspection.
