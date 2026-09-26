# Phase 9.2 — Managed Android Screen Capture Foundation

## Scope

This phase adds the Managed Android foundation for an authorized screen-sharing session in `gaje9dra/parento-managed`.

It uses Android's official `MediaProjection` flow. Device Owner/Profile Owner status does not bypass the Android consent flow.

## Architecture

```
Authenticated command boundary
        |
        v
ScreenShareManager
        |
        +--> authorization notification/activity
        |
        +--> MediaProjectionManager.createScreenCaptureIntent()
        |
        v
ScreenCaptureForegroundService
        |
        v
AndroidScreenCaptureController
        |
        +--> MediaProjection
        +--> VirtualDisplay
        +--> ImageReader / Surface
        |
        v
ScreenFrameSource
```

`ScreenFrameSource` is deliberately only an output boundary. Frames are acquired and immediately released. This phase does not encode, persist, upload, archive, or display screen frames remotely.

## Authorization

For every capture session:

1. A valid authenticated command requests a screen-sharing session.
2. The managed device verifies enrollment and a live device communication session.
3. The app records `AUTHORIZATION_REQUIRED` locally.
4. A visible notification opens the screen-sharing activity.
5. The activity launches `MediaProjectionManager.createScreenCaptureIntent()`.
6. The user/Android OS decides whether to grant screen capture.
7. Only the result of that current consent flow is passed to the foreground service.
8. The foreground service creates the `MediaProjection` and one `VirtualDisplay`.

No MediaProjection consent Intent is persisted or reused. Android 14+ requires consent for each capture session and does not permit reusing a projection instance for another virtual display. See the Android platform guidance: https://developer.android.com/about/versions/14/behavior-changes-14

## Foreground service

The service is declared with:

- `android:foregroundServiceType="mediaProjection"`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`

The service starts only after the user has completed the MediaProjection authorization flow. It displays an ongoing user-visible notification while capture is active.

## Capture lifecycle

Android capture state is separate from any backend session state:

- `UNAVAILABLE`
- `REQUESTED`
- `AUTHORIZATION_REQUIRED`
- `AUTHORIZED`
- `STARTING`
- `ACTIVE`
- `STOPPING`
- `STOPPED`
- `FAILED`
- `REVOKED`
- `EXPIRED`

The state machine rejects unsafe direct transitions. Process restart invalidates persisted `STARTING`, `ACTIVE`, `STOPPING`, and `AUTHORIZED` state rather than attempting to resume capture.

## MediaProjection callback and cleanup

The controller registers `MediaProjection.Callback.onStop()`. Projection termination releases:

- `VirtualDisplay`
- `ImageReader`
- capture `Surface`
- `MediaProjection`
- configuration callbacks
- runtime capture state

Configuration changes use the existing projection/display and resize the virtual display rather than creating a second projection instance.

## Command integration

The existing command processor remains the command execution boundary. Phase 9.2 adds allowlisted handlers for:

- `START_SCREEN_SHARE` v1
- `STOP_SCREEN_SHARE` v1

Commands still pass the existing device/session validation, schema/version validation, expiry validation, duplicate-command persistence, and authenticated transport path.

A start command that reaches the managed app enters `AUTHORIZATION_REQUIRED` and exposes a user-visible authorization notification. A stop command is idempotent and releases the active service/resources.

No arbitrary command execution was added.

## Transport boundary

No second HTTP, authentication, WebSocket, or realtime system was added.

The capture output is isolated behind `ScreenFrameSource`. Because this repository does not contain a finalized Phase 9.1 media-frame transport contract, no speculative encoder, streaming protocol, or native media library was introduced.

Command delivery uses the existing `DeviceTransport.receiveNextCommand()` boundary.

## Local persistence

Only minimal recovery/diagnostic metadata is stored:

- session ID
- capture state
- state timestamp
- safe error category

The app never persists:

- screen frames
- screenshots
- video
- MediaProjection authorization Intents
- MediaProjection objects
- credentials or reusable capture authorization

## Device Owner / Profile Owner

Device Owner/Profile Owner status is not treated as implicit MediaProjection consent. The normal Android OS authorization flow remains mandatory.

The implementation does not use root, shell commands, hidden APIs, reflection, Accessibility services, privileged permissions, or permission-grant bypasses.

## Manual real-device test plan

Do not mark these tests passed without a physical/emulator run that actually exercises MediaProjection:

1. Enroll the device.
2. Establish the managed communication session.
3. Issue an authorized screen-sharing start request.
4. Confirm the authorization-required state and user-visible notification.
5. Deny the Android MediaProjection prompt; verify capture remains inactive.
6. Request a new session and grant authorization.
7. Verify the foreground-service notification appears.
8. Verify capture reaches `ACTIVE`.
9. Stop screen sharing and verify the service, virtual display, surface, and projection are released.
10. Test Android/OS termination of MediaProjection and verify `STOPPED`.
11. Rotate/configure the device and verify the virtual display resizes without creating another projection.
12. Kill the process during authorization and during active capture; verify capture does not silently resume.
13. Reboot and verify capture does not resume.
14. Test network loss/reconnection without creating a second communication system.
15. Test enrollment revocation/cancellation when the surrounding backend lifecycle integration is available.
16. Test a duplicate start and duplicate stop command.

## Verification boundary

Automated unit tests cover the capture state-transition contract. CI must run the repository's existing Gradle unit tests, lint, connected Android tests, debug build, and release build.

A physical MediaProjection test cannot be claimed from CI unless an actual Android device/emulator has exercised the OS authorization dialog and projection lifecycle.
