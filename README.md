# Parento Managed

**Parento Managed** is the Android application installed on an explicitly enrolled and authorized device in the Parento platform.

## Architecture

Parento consists of three strictly separated components:

```
Parento Admin
      ↓
Parento Backend
      ↓
Parento Managed
```

- `gaje9dra/parento-admin` — administrator/controller Android application.
- `gaje9dra/parento-backend` — trusted API, persistence, realtime, and authorization layer.
- `gaje9dra/parento-managed` — this Android managed-device application.

The Managed app does not communicate directly with the Admin app. Future communication is mediated by the backend.

## Current phase

**Phase 1.2 — Managed Android App Architecture & Internal Contracts**

Phase 1.1 established the Android/Gradle foundation. Phase 1.2 adds internal boundaries and domain contracts while keeping all future device-management functionality unimplemented.

## Internal layers

The application is prepared around these responsibilities:

```
Presentation / UI
       |
Device Management
       |
Policy Engine
       |
Communication
       |
Security
       |
Local Data
       |
Android Platform Integration
```

These are architectural boundaries, not a claim that future functionality already exists.

### Presentation / UI

`MainActivity` remains the minimal launch surface. Feature UI belongs here and should consume domain/application interfaces rather than embedding backend or platform logic.

### Device Management

`DeviceManager` defines the boundary for managed-device state and future lifecycle operations.

### Policy Engine

`PolicyEngine` defines the boundary for validating future policy definitions. It does not enforce application, website, network, or device restrictions in this phase.

### Communication

`BackendClient` defines the future backend communication boundary. Phase 1.2 does not connect to `gaje9dra/parento-backend`, authenticate, send commands, or establish realtime communication.

### Security

`SecurityStore` defines where future secure device identity, tokens, and credential operations belong. No production credentials or bypass mechanisms are created.

### Local Data

`LocalDataStore` defines the future persistence boundary for device, enrollment, policy, and synchronization state. No sensitive production credential is persisted and no complete database is implemented.

### Android Platform Integration

`AndroidPlatform` isolates future Android-specific APIs. Sensitive permissions and platform capabilities must be introduced only with the feature that legitimately requires them.

### Background work

`BackgroundWorkScheduler` is the extension point for future authorized synchronization work. Phase 1.2 does not start persistent background services or hidden monitoring.

### Logging

`ManagedLogger` provides a small logging contract with explicit levels. Implementations must not log passwords, authentication tokens, private keys, sensitive credentials, unnecessary personal data, or sensitive device content.

## Domain/state model

The domain layer is Android-independent and currently contains only minimal concepts:

- `ManagedDevice`
- `DeviceStatus`
- `EnrollmentState`
- `PolicyState`
- `ConnectionState`
- `OperationResult`
- `ManagedError`

The device lifecycle/status model can represent:

```
UNENROLLED
ENROLLING
ENROLLED
CONNECTED
DISCONNECTED
REVOKED
ERROR
```

Enrollment and policy states are kept separate from connection state so later implementations can model those concerns independently.

`OperationResult` provides a consistent success/failure shape for future operations. Failure categories include invalid state, network, authentication, authorization, policy, Android platform, storage, and unknown/internal failures.

## Phase 1.2 limitations

The following are **not implemented**:

- Admin authentication
- Managed-device enrollment
- QR pairing
- Device credentials
- Backend authentication
- Location or location history
- Screen sharing
- Microphone/audio capture
- Camera access
- Gallery access
- Application blocking
- Installation blocking
- Website blocking
- DNS/VPN filtering
- Device locking
- Remote commands
- Notifications
- Production policy enforcement
- Backend API calls
- Realtime communication

No sensitive Android runtime permissions were added.

## Project structure

```
app/
  src/
    main/
      java/com/parento/managed/
        MainActivity.kt
        background/
          BackgroundWorkScheduler.kt
        communication/
          BackendClient.kt
        data/
          LocalDataStore.kt
        device/
          DeviceManager.kt
        domain/
          ConnectionState.kt
          DeviceStatus.kt
          EnrollmentState.kt
          ManagedDevice.kt
          ManagedError.kt / OperationResult.kt
          OperationResult.kt
          PolicyState.kt
        logging/
          ManagedLogger.kt
        platform/
          AndroidPlatform.kt
        policy/
          PolicyEngine.kt
        security/
          SecurityStore.kt
      res/
        values/
          strings.xml
          themes.xml
      AndroidManifest.xml
    test/
      java/com/parento/managed/
        ArchitectureTest.kt
        ExampleUnitTest.kt
```

## Dependencies

No new Gradle dependencies were added in Phase 1.2. The existing Phase 1.1 dependencies remain sufficient for these pure Kotlin interfaces and tests.

## Security and privacy baseline

- No passwords, API keys, private keys, or production credentials are stored in source.
- No hidden administrator account or bypass mechanism exists.
- No covert remote-control or surveillance mechanism is implemented.
- No sensitive runtime permissions were introduced.
- Future management capabilities must use Android-supported security/device-management APIs.
- Permission requests must be introduced only alongside the feature that legitimately requires them.

## Backend boundary

Future Managed-app communication will use:

`gaje9dra/parento-backend`

The expected future boundary is an authenticated backend API/realtime interface defined by later backend phases. Phase 1.2 deliberately does not call or authenticate against the backend.

## Cross-repository requirements

No code changes are required in the companion repositories during this phase.

**Cross-repository requirement**

Repository: `gaje9dra/parento-backend`

Requirement: Later expose the concrete managed-device authentication, enrollment, policy, status, event, and command contracts required by the `BackendClient` boundary.

Reason: The Managed app must communicate through the trusted backend rather than directly with the Admin app.

Expected interface: Authenticated HTTPS/realtime contracts under the backend's versioned API, with device authorization enforced server-side.

Repository: `gaje9dra/parento-admin`

Requirement: Later consume the backend contracts for administrator-facing operations.

Reason: The Admin app must remain separated from the Managed app.

Expected interface: Backend-mediated API/realtime interfaces; no direct Admin → Managed connection.

Neither repository was modified in Phase 1.2.

## Development and verification

Open the repository in Android Studio and allow Gradle to sync.

Typical commands:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

On Windows:

```
gradlew.bat assembleDebug
gradlew.bat test
gradlew.bat lint
```

Phase 1.2 should be verified with formatting, lint/static analysis, unit tests, Android build, and application launch.

## Phase status

Phase 1.2 establishes internal architecture and contracts only. It does not implement future device-management capabilities.
