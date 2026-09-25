# Phase 6.3 — Device Information & Monitoring Foundation

## Scope

Phase 6.3 collects only operational device information already available through supported Android APIs. It runs only in `parento-managed` and preserves the Phase 6.2 communication/command boundaries.

## Collected metrics

- Parento ManagedDevice ID, when enrolled
- local installation identity, where available
- Android management mode: NOT_MANAGED, PROFILE_OWNER, DEVICE_OWNER, or UNKNOWN
- Android release/version and API level
- Parento application version and version code
- battery percentage, charging state, and derived battery status
- network state: UNKNOWN, OFFLINE, WIFI, CELLULAR, OTHER
- accessible storage total/available/used bytes from the app's filesystem
- runtime memory total/available/low-memory state
- collection timestamp
- known initialization/synchronization timestamps from existing local state

No hardware identifiers are used as application identity.

## Architecture

```
Android APIs
    ↓
Provider interfaces
    ↓
CollectDeviceMonitoringSnapshot
    ↓
MonitoringRepository
    ↓
Room latest-state snapshot
    ↓
ManagedStatus UI
```

Providers isolate Android framework APIs:

- DeviceInfoProvider
- BatteryInfoProvider
- NetworkInfoProvider
- StorageInfoProvider
- MemoryInfoProvider
- ManagementInfoProvider

The monitoring use case is independent of Android UI and stores an immutable snapshot through the existing Room architecture.

## Persistence

Room schema version 7 adds one `monitoring_snapshot` singleton row and migration 6→7. Monitoring does not create an unlimited history. Each successful collection replaces the latest snapshot.

Authentication credentials and session tokens are not part of the monitoring entity.

## Scheduling

WorkManager runs a unique periodic monitoring worker every 30 minutes. The interval is intentionally conservative and uses WorkManager rather than a permanent service or polling loop. Duplicate periodic work is prevented with `KEEP`.

The worker exits successfully when the device is not enrolled, and retries when a collection/persistence operation fails.

## Partial failures

Battery, network, storage, memory, and management collectors use explicit unavailable/unknown values when an individual source cannot be read. Useful data from other collectors is retained. A failure to obtain the core device-information model is treated as a platform failure because identity/version context is required for a meaningful snapshot.

## Communication

No direct HTTP or socket operation exists inside monitoring collectors. Phase 6.2's communication layer remains the transport boundary.

The Phase 6.1 backend contract currently does not provide an approved device-monitoring synchronization endpoint. Therefore Phase 6.3 persists the local monitoring snapshot and exposes it locally; it does not invent a new backend endpoint or modify `parento-backend`.

## UI

The existing managed-device status screen can display management/enrollment/connection state plus the latest local monitoring snapshot. This is not an administrator dashboard.

## Privacy and security

Explicitly excluded:

- precise/background location
- camera and microphone/audio
- screen capture/streaming
- contacts, SMS, call logs
- browser history
- arbitrary files or media
- passwords or messages
- network credentials or traffic contents
- IMEI, serial number, MAC address as application identity
- covert monitoring
- application/website blocking
- network filtering
- remote lock/wipe
- arbitrary command execution

The existing secure window behavior and non-exported-by-default monitoring code paths are preserved. No credentials, session tokens, or pairing secrets are logged.

## Platform limitations

Android exposes runtime memory and filesystem statistics as operational values; they are not treated as guaranteed physical hardware specifications. Network transport classification can be UNKNOWN when Android does not expose sufficient capabilities. Battery broadcasts can also lack complete fields on some devices.

## Backend dependency

A future approved backend contract would be needed to synchronize monitoring snapshots. The required contract is a device-authenticated, bounded device-status/monitoring payload endpoint that accepts the already-established ManagedDevice identity and communication session. That contract is intentionally not implemented in this phase.
