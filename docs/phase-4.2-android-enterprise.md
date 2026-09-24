# Phase 4.2 — Android Enterprise & Device Owner Integration Foundation

## Scope

Phase 4.2 is limited to `gaje9dra/parento-managed`.

It establishes legitimate Android Enterprise integration without implementing remote device-control features.

## Platform boundary

The Managed app keeps Android platform APIs behind:

```text
UI
 ↓
ViewModel
 ↓
Managed-device state
 ↓
DeviceManagementManager / ManagementModeDetector
 ↓
AndroidDeviceManagementPlatform
 ↓
DevicePolicyManager
```

Higher layers never receive a raw `DevicePolicyManager` instance.

The platform adapter queries the Android operating system for Device Owner and Profile Owner status. Android is authoritative; local persistence is not used as proof of management.

## Management modes

The detector produces:

- `DEVICE_OWNER`
- `PROFILE_OWNER`
- `NOT_MANAGED`
- `UNKNOWN`

An `UNKNOWN` result is accompanied by a structured error:

- `MANAGEMENT_STATE_UNAVAILABLE`
- `PLATFORM_API_ERROR`
- `UNSUPPORTED_API`
- `SECURITY_EXCEPTION`
- `INVALID_MANAGEMENT_STATE`

Conflicting Device Owner and Profile Owner signals are rejected as an invalid management state rather than being guessed or resolved optimistically.

## Capability discovery

Phase 4.2 reports only capabilities supported by current platform state.

- Device Owner is available only when Android reports the app as Device Owner.
- Profile Owner is available only when Android reports the app as Profile Owner.
- Device policy platform support is derived from the Android device-admin system feature and an available `DevicePolicyManager`.
- Future control and monitoring capabilities are not claimed merely because application code may eventually implement them.

Remote lock, wipe, app management, network restrictions, camera, microphone, location, screen capture, and policy enforcement remain deferred.

## DeviceAdminReceiver

`ManagedDeviceAdminReceiver` is a minimal subclass of Android's official `DeviceAdminReceiver`.

The manifest protects it with:

```text
android.permission.BIND_DEVICE_ADMIN
```

The permission is system-only. The receiver handles no commands and performs no sensitive operation.

The device-admin XML contains no policy declarations because Phase 4.2 does not enforce a device-admin policy.

## Lifecycle and reboot behavior

Application startup:

1. Initializes local state.
2. Re-queries Android management state.
3. Evaluates capabilities from the same platform snapshot.
4. Persists only diagnostic management metadata.
5. Publishes the fresh result to lifecycle-aware UI observers.

After reboot/process recreation, management status is queried again from Android.

After reinstall, local persistence is not treated as evidence of Device Owner status. Legitimate Android provisioning state remains authoritative.

## Error handling

Expected `SecurityException` and platform failures are translated into safe domain state. The application does not retry indefinitely, expose stack traces, or convert errors into an optimistic managed state.

## Testing

Unit tests cover:

- Device Owner
- Profile Owner
- unmanaged
- unsupported platform
- security exception
- unexpected platform exception
- conflicting owner signals
- fail-closed capability discovery

Room instrumentation migration coverage remains in place.

A specially provisioned Device Owner device is not required for normal unit tests.

## Manual Device Owner test plan

For development/testing, use a supported Android Enterprise provisioning flow or an appropriate development/test provisioning mechanism.

Verify:

1. Install the application using the supported provisioning mechanism.
2. Confirm Android reports the application as Device Owner.
3. Launch Parento Managed.
4. Confirm the UI displays **Device Owner**.
5. Confirm capability discovery reflects the authoritative management mode.
6. Reboot the device.
7. Relaunch the application and confirm Device Owner status is re-read from Android.
8. Remove/reinstall only according to the supported provisioning lifecycle and verify the application does not use cached state as proof of ownership.

Do not provision Device Owner status from inside the application and do not bypass Android security.

## Security review

Phase 4.2 adds no:

- root access
- hidden APIs
- Accessibility Service abuse
- covert sensor access
- screen capture
- audio recording
- camera access
- location collection
- anti-uninstall bypass
- unauthorized system settings
- remote command execution

No backend or Admin repository changes are required by this phase.

## Verification commands

The repository currently does not contain a Gradle wrapper. In a repository-compatible Gradle/Android Studio environment run:

```text
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
```

Record the actual result of each command. Do not mark Phase 4.2 complete from source inspection alone.

## Deferred features

The following remain out of scope:

- Device Owner provisioning implementation
- remote lock/wipe/reboot/kiosk
- app installation/removal/blocking
- website/network blocking
- password policy enforcement
- camera/microphone/audio capture
- screen capture/sharing
- location tracking
- backend command execution
- remote policy synchronization/enforcement
- telemetry/monitoring
