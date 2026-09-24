# Phase 4.4 — Device Lifecycle, Background Execution & Reliability Foundation

## Scope

This phase applies only to `gaje9dra/parento-managed`. It establishes Android-supported lifecycle, startup, recovery, connectivity observation, and WorkManager boundaries. It does not implement remote control, surveillance, enrollment, or policy enforcement.

## Startup

The application initializes configuration and the Room database before constructing managed-device infrastructure. Startup then initializes local state and runs the existing management-state initializer. The centralized `ApplicationInitializationState` reports `NOT_STARTED`, `INITIALIZING`, `READY`, or `FAILED` and identifies a failed subsystem without exposing secrets.

Initialization is serialized and idempotent. The UI observes the result through application state and can request a controlled retry after failure.

No network request is made during startup.

## Foreground / Background

`ProcessLifecycleOwner` provides application-level foreground/background observation. Foreground transitions after successful initialization trigger a management-state refresh. Background transitions only update lifecycle state and do not keep the process alive.

No foreground service, boot receiver, wake lock, watchdog, self-restart loop, or hidden persistence mechanism was added.

## Process Death and Reboot

Durable identity, enrollment, connection, and management metadata remain in Room. A new process reconstructs runtime state through normal application initialization and re-queries Android's authoritative management APIs.

There is no boot receiver. The app therefore does not automatically start work or network communication on device boot. Android and application startup mechanisms remain responsible for reconstruction when the app is launched.

## Management State Recovery

Phase 4.2 management detection remains platform-authoritative. Initialization and foreground refresh call the existing `ManagedDeviceInitializer`, which re-queries Device Owner/Profile Owner state instead of trusting cached management metadata.

Stored management state is diagnostic persistence, not authorization.

## Identity and Enrollment Recovery

Phase 4.3 local identity remains stable in Room and is not regenerated after process death or temporary network errors. Missing identity recovery remains fail-closed when enrollment or a managed-device ID already exists.

Enrollment and connection state remain independent. Network availability is never treated as proof of backend authentication, enrollment, or authorization.

## Connectivity

`ConnectivityManager.registerDefaultNetworkCallback` provides event-driven network availability observation. There is no polling loop.

Network availability is diagnostic only. The observer does not mark the backend connection as authenticated/connected. This preserves the Phase 4.3 distinction between network availability and backend connection state.

## WorkManager

A reusable `BackgroundWorkScheduler` boundary now uses WorkManager for explicitly requested future one-time jobs. Jobs use unique work with `KEEP` semantics to prevent duplicate scheduling.

Supported constraints include:
- network availability
- charging
- storage-not-low

No background synchronization job is scheduled by this phase. Future workers must be bounded, idempotent, cancellable, and independently authorized.

## Retry Strategy

`BackgroundRetryPolicy` classifies failures as:
- transient
- permanent
- configuration
- authorization

Only transient failures are eligible for bounded retry. Permanent, configuration, and authorization failures do not retry indefinitely. WorkManager's exponential backoff is exposed as the default scheduling policy for future workers.

## Battery and Resource Safety

The phase adds no periodic keep-alive work, WebSocket, foreground service, wake lock, or repeated DevicePolicyManager/network polling.

Connectivity callbacks are registered once and unregistered during application termination. Long-lived application work uses a `SupervisorJob`-backed application scope.

## Security Boundary

Lifecycle transitions do not grant authority. Process recreation, foreground refresh, network restoration, or reboot cannot create enrollment, authorize commands, or bypass management checks.

No:
- covert persistence
- hidden background execution
- accessibility persistence
- root or hidden APIs
- process-resurrection tricks
- remote commands
- surveillance
- sensor capture
- policy enforcement

was added.

## Testing

Unit coverage includes:
- initialization state model
- foreground/background lifecycle transitions
- bounded retry classification
- existing management, identity, enrollment, connection, and migration behavior

Instrumentation remains responsible for Room migration/persistence validation and Android-specific behavior.

## Verification

CI is the authoritative build environment because the repository uses a Gradle-installed GitHub Actions workflow rather than a checked-in Gradle wrapper.

Expected checks:
- `gradle test`
- `gradle lint`
- `gradle connectedDebugAndroidTest`
- `gradle assembleDebug`
- `gradle assembleRelease`

## Phase Boundary

Deferred to later phases:
- backend enrollment
- QR pairing
- remote command delivery
- realtime communication
- location
- camera/microphone/audio capture
- screen sharing
- app/website blocking
- remote locking/wipe
- policy enforcement
