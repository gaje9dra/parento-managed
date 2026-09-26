# Phase 9.5 — Managed Android Screen Sharing Security, Reliability & Phase 9 Completion

## Scope

This phase hardens the existing Phase 9.2 Managed Android implementation only. It does not add Phase 10+ functionality and does not modify backend or Admin repositories.

## Security audit findings fixed

### Backend command payload binding

The backend Phase 9 contract requires screen-sharing commands to contain exactly one payload field:

- `screenSessionId`

Managed command handling now parses that exact field and rejects the legacy `sessionId` field or additional payload fields.

The local operation remains bound to the screen-session identifier carried by the command and the locally pending authorization state.

### MediaProjection lifecycle race

The capture controller now serializes start/stop/release operations. If Android terminates the projection while a `VirtualDisplay` is being created, the display is released instead of becoming orphaned.

Cleanup remains deterministic for:

- `MediaProjection`
- `VirtualDisplay`
- `ImageReader`
- capture surface
- projection callback
- configuration callbacks

The ImageReader queue remains bounded to two images and acquired images are immediately released.

### Foreground-service session binding

The foreground service now accepts a capture authorization result only when:

1. the authorization result is structurally valid;
2. the supplied session ID is a UUID;
3. the supplied session ID matches the locally pending screen session; and
4. the local capture state is `STARTING`.

A stale or mismatched service Intent fails closed.

### Authorization loss and revocation

The Managed app now continuously evaluates the local managed-device authorization state. Loss of any required authorization condition terminates an in-progress screen-sharing operation and records a terminal `REVOKED` state without retaining the active session identifier.

The authorization conditions require:

- enrolled device state;
- a managed-device identifier;
- Device Owner or Profile Owner management state with no management-detection error; and
- an active authenticated device communication connection.

Device Owner/Profile Owner status does not grant MediaProjection consent. Android OS MediaProjection authorization remains a separate security boundary.

### Process restart

Persisted local states that represent an in-progress capture are normalized to a safe `FAILED / PROCESS_RESTARTED` state without retaining the stale session identifier. The app never silently resumes a previous projection after process death or reboot.

### Terminal-state cleanup

Controller/service cleanup callbacks cannot overwrite `REVOKED` or `EXPIRED` with `STOPPING` or `STOPPED`. Resource cleanup still occurs when the foreground service is stopped.

## Backend contract compatibility

The backend Phase 9 service currently defines:

- `POST /api/v1/devices/:deviceId/screen-sessions`
- `GET /api/v1/screen-sessions/:sessionId`
- `POST /api/v1/screen-sessions/:sessionId/stop`
- `POST /api/v1/device/screen-sessions/:sessionId/started`
- `POST /api/v1/device/screen-sessions/:sessionId/stopped`

It also creates `START_SCREEN_SHARE` and `STOP_SCREEN_SHARE` commands whose payload is `{ "screenSessionId": "<uuid>" }`.

The Managed command parser is compatible with the command payload contract.

### Remaining integration limitation

The current Managed `DeviceTransport.receiveNextCommand()` implementation returns no command and the transport abstraction does not yet expose the backend screen-session `started` / `stopped` acknowledgement endpoints.

Those are transport/integration gaps that cannot be safely invented or implemented as a second protocol inside this repository. They require an explicit backend/transport contract change. No backend repository was modified by Phase 9.5.

## Local/backend state separation

Managed local capture state remains distinct from the backend screen-session lifecycle. The local model represents MediaProjection/capture resources; backend session states remain server-authoritative.

The Managed app must not infer backend expiration merely from a local capture state. A backend expiration notification/command must use the existing authenticated command/transport boundary.

The manager exposes an explicit `handleBackendSessionExpired(sessionId)` path for an authoritative expiration event when the existing transport layer supplies one.

## Privacy and resource boundaries

The implementation does not:

- persist screenshots or video;
- persist MediaProjection authorization Intents;
- log screen contents or raw frames;
- encode or upload frames;
- introduce a media server;
- add WebRTC/media libraries;
- capture microphone, camera, or audio;
- execute arbitrary remote code.

## Manual test matrix

| # | Scenario | Expected result |
|---|---|---|
| 1 | Authorization accepted | Current session enters `STARTING` and capture may enter `ACTIVE`. |
| 2 | Authorization denied | Capture does not start; state becomes `FAILED` with a safe authorization error. |
| 3 | Authorization cancelled | Capture does not start; no foreground capture service remains active. |
| 4 | Start session | Exactly one matching local capture operation is created. |
| 5 | Stop session | Capture resources are released and state becomes `STOPPED`. |
| 6 | Android terminates projection | Projection callback releases resources and capture stops. |
| 7 | Network disconnected | Existing device communication handles loss; capture is not silently restarted. |
| 8 | Network restored | Reconnection does not itself authorize or create a replacement capture session. |
| 9 | Backend session expires | Authoritative expiration must terminate capture; no replacement session is created. |
| 10 | Device revoked | Local authorization loss terminates capture and leaves a terminal `REVOKED` state. |
| 11 | Admin access becomes invalid | Backend/device authorization failure must terminate capture through the existing communication boundary. |
| 12 | App process killed | Old local active state is normalized on next process start; capture is not resumed. |
| 13 | Service restarted | A service Intent without a matching pending `STARTING` session is rejected. |
| 14 | Device rebooted | Capture does not silently resume. |
| 15 | Management state changes | Loss of Device Owner/Profile Owner authorization causes capture termination on state observation. |
| 16 | Orientation/configuration change | Existing VirtualDisplay is resized; no second MediaProjection is created. |
| 17 | Duplicate start command | Duplicate command delivery is handled by existing command idempotency; same active session is not duplicated. |
| 18 | Duplicate stop command | Existing command idempotency and local stop handling make repeated stop safe. |
| 19 | Invalid session | Command/session mismatch is rejected without starting capture. |
| 20 | Backend unavailable | No automatic replacement capture session or authorization bypass occurs. |

## Verification boundary

Automated tests can validate state machines, command payload validation, and pure lifecycle rules.

Real MediaProjection authorization, Android projection callbacks, VirtualDisplay behavior, service lifecycle, rotation, reboot, and actual device-management transitions require an Android emulator/device run. They must not be represented as physically verified unless that run actually occurred.

## Phase boundary

No Phase 10+ functionality is included. In particular, this phase does not add:

- audio or microphone capture;
- camera access;
- application management;
- website/network blocking;
- device restrictions;
- location expansion;
- screenshot/video export or storage;
- arbitrary remote execution;
- covert monitoring.
