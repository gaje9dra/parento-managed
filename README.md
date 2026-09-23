# Parento Managed

**Parento Managed** is the Android application installed on an explicitly enrolled and authorized device in the Parento platform.

## Architecture

Parento consists of three strictly separated components:

```text
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

## Phase 1.1 scope

This phase establishes only a clean Android application foundation:

- Reproducible Gradle/Android configuration
- Standard application identity
- Minimal launchable UI
- Basic Android project structure
- Test foundation
- Documentation and security boundaries

The following are intentionally **not implemented**:

- Device enrollment or pairing
- Administrator/device authentication
- Device identity
- Location or location history
- Screen sharing
- Audio or camera functionality
- Installed-app inventory
- App blocking or installation restrictions
- Website/network filtering
- Device restrictions or device lock
- Policy synchronization
- Realtime communication
- Notifications
- Audit/security events
- Backend API calls

No sensitive runtime permissions are requested in Phase 1.1.

## Project structure

```text
app/
  src/
    main/
      java/com/parento/managed/
        MainActivity.kt
      res/
        values/
          strings.xml
          themes.xml
      AndroidManifest.xml
    test/
      java/com/parento/managed/
        ExampleUnitTest.kt
```

The future architecture will separate UI, device-management, policy, communication, security, local data, background execution, and Android platform integration. Phase 1.1 does not create speculative implementation classes for those future systems.

## Requirements

- Android Studio with a compatible Android SDK
- JDK 17
- Android SDK Platform 36

## Local development

Open the repository in Android Studio and allow Gradle to sync.

To build from a terminal:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.gradlew.bat assembleDebug
```

Run unit tests:

```bash
./gradlew test
```

Run lint:

```bash
./gradlew lint
```

## Security principles

- No passwords, API keys, private keys, or production credentials are stored in source.
- No hidden administrator account or bypass mechanism exists.
- No covert remote-control or surveillance mechanism is implemented.
- Future management capabilities must use Android-supported security/device-management APIs.
- Sensitive runtime permissions are introduced only alongside the feature that legitimately requires them.

## Backend boundary

Future Managed-app communication will use:

`gaje9dra/parento-backend`

Phase 1.1 deliberately does not authenticate, enroll, call, or open a realtime connection to the backend.

## Admin boundary

The Managed app does not directly connect to:

`gaje9dra/parento-admin`

The future architecture is Admin → Backend → Managed.

## Cross-repository policy

Only `gaje9dra/parento-managed` is modified by this phase. Any future dependency on the backend or Admin app must be documented and implemented in its respective repository during its own phase.
