# Phase 7.2 — Managed Android Device Information & Monitoring Collection

## Scope

Phase 7.2 productionizes the existing Phase 6.3 monitoring foundation in parento-managed. It collects a bounded operational snapshot and reports it through the existing authenticated managed-device session to the backend.

No direct Managed Android → Admin communication is introduced.

## Collected metrics

- Android version and API level
- managed application version/build
- existing Parento installation and ManagedDevice identity references
- Android management mode
- battery percentage, charging state, and derived battery status
- network transport state
- storage total/available/used
- memory total/available/low-memory state
- initialization and last successful synchronization timestamps

The app does not collect location, contacts, SMS, calls, browsing data, packet contents, credentials, camera, microphone, screen contents, arbitrary files, or hardware identifiers such as IMEI/serial/MAC.

## Collector isolation

Each platform provider returns a controlled OperationResult. A failed battery, network, storage, memory, or management collector is represented as unavailable data rather than failing the complete snapshot.

Device/software identity remains the foundational collector because it supplies the authenticated ManagedDevice correlation required for reporting.

## Telemetry contract

MonitoringTelemetrySerializer emits schema version 1 matching the Phase 7.1 backend DeviceMonitoringRequest.

The backend session determines the authenticated device. The managed app sends its existing ManagedDevice ID only as a contract field; DeviceCommunicationSessionManager verifies it against locally enrolled identity before transmission.

No separate telemetry credential or networking stack exists.

## Reporting flow

MonitoringWorker:

1. exits successfully when the installation is not enrolled;
2. collects the current snapshot;
3. persists the latest snapshot locally;
4. obtains/reuses an authenticated managed-device communication session;
5. submits the snapshot to POST /api/v1/device/monitoring;
6. records the successful synchronization timestamp only after the server accepts the request;
7. returns a retry result for transient collection/communication failure.

The worker does not retain an unlimited telemetry queue.

## Scheduling

Monitoring uses unique WorkManager periodic work every 30 minutes.

Constraints:
- network must be connected;
- exponential retry backoff starts at 30 seconds;
- duplicate periodic workers are prevented with ExistingPeriodicWorkPolicy.KEEP;
- WorkManager owns persistence across process death and reboot.

No permanent background loop or hidden persistence mechanism is used.

## Offline and recovery behavior

When the network is unavailable, WorkManager waits for the network constraint instead of aggressively waking the device. A failed submission does not mark the synchronization timestamp as successful.

An expired/missing managed-device session is re-established through the existing device credential/session architecture. Session credentials remain in the existing encrypted store.

A revoked or invalid device session is not given a parallel telemetry authentication path.

## Local persistence

Only the latest monitoring snapshot is retained locally. No telemetry history is created.

The existing Room database schema remains the source for recovery of the latest snapshot. Successful telemetry synchronization uses the existing lastSynchronizationTimestamp field in local application state.

## Android management compatibility

Management mode is obtained from the existing DevicePolicyManager-based detection layer. The app does not infer Device Owner/Profile Owner status from unrelated device properties.

Metrics unavailable on a particular API/device are represented as null/unknown rather than fabricated.

## Security and privacy

Telemetry:
- uses the authenticated managed-device session;
- uses HTTPS in production;
- contains no session token or device credential;
- does not log request payloads or credentials;
- does not add sensitive Android permissions;
- does not introduce direct Admin communication;
- does not implement sensitive device-control or surveillance features.

## Testing

Phase 7.2 adds coverage for:
- partial collector failure;
- nullable/unavailable telemetry metrics;
- schema serialization;
- authenticated telemetry submission;
- ManagedDevice identity mismatch rejection;
- successful synchronization timestamp persistence.

Repository CI remains responsible for formatting, lint, unit tests, instrumented persistence tests, and debug/release builds.

## Backend dependency

Phase 7.1 already provides the required managed-device endpoint:

POST /api/v1/device/monitoring

No backend change is required for Phase 7.2.

## Deferred

Not implemented:
- telemetry history
- location
- camera/microphone
- screen capture
- application/website blocking
- device lock/wipe
- arbitrary remote execution
- hidden surveillance