# Phase 10.5 — Managed Android Audio Security, Reliability & Phase 10 Completion

## Scope

This phase hardens the existing Phase 10.2 Managed Android implementation. Only `gaje9dra/parento-managed` is modified.

### Security boundary

- Audio commands remain strictly allowlisted to `START_AUDIO_ACCESS` and `STOP_AUDIO_ACCESS`.
- Generic command validation rejects malformed audio payloads and requires the deterministic idempotency key before command acknowledgement.
- ManagedDevice identity is checked by the existing command validator against the authenticated/current device session context.
- Audio command handlers require an exact UUID `audioSessionId` payload.
- Terminal audio states cannot be restarted locally.
- Duplicate START for an already ACTIVE session is idempotent; START while STARTING is treated as already in progress.
- Device/session mismatch is rejected.
- No Admin or ManagedDevice ID from the audio payload is trusted as identity.

## Microphone permission

The implementation uses official `RECORD_AUDIO` permission checks and the existing Android permission/UI boundary.

A background command does not silently launch runtime permission UI. When permission is unavailable, the command returns `PERMISSION_REQUIRED` and the app surfaces a visible notification that leads to the normal application UI.

Device Owner status is not treated as a microphone-permission bypass.

## Capture lifecycle

`AndroidAudioCaptureController` uses `AudioRecord` with bounded in-memory PCM frames.

Stop/failure cleanup releases:

- `AudioRecord`
- microphone capture
- capture worker
- temporary frame buffer
- transport

The foreground service is `START_NOT_STICKY`, so process death does not silently recreate microphone capture.

## Foreground service

The audio service is non-exported and declares the microphone foreground-service type. It displays an ongoing user-visible notification while capture is active.

The service fails closed if:

- authorization state is not STARTING for the requested session;
- the session identifier is invalid;
- microphone permission is missing;
- the media transport is unavailable;
- the managed device communication connection is lost;
- microphone permission is revoked while active.

A five-second authorization/permission watchdog stops the service when the authenticated device connection or microphone authorization is lost.

## Transport boundary

The transport interface is explicitly session-bound:

`start(sessionId)` and `send(sessionId, frame, length, timestamp)`.

Frame length is bounded at the transport boundary. No audio is written to Room, SharedPreferences/DataStore, files, logs, or crash metadata.

The current repository intentionally retains `UnavailableAudioTransport` because the backend Phase 10 contract does not yet define a production audio-byte/media protocol. The Managed app therefore refuses to capture/transmit live audio rather than inventing a WebSocket/RTP/WebRTC/media-server protocol.

This is a deliberate fail-closed compatibility limitation.

## Lifecycle and recovery

- Duplicate active START is idempotent.
- START after STOPPED/FAILED/EXPIRED is rejected locally.
- STOP is safe and idempotent when no capture is active.
- Backend/session expiration is represented as an explicit local `EXPIRED` state.
- Existing application-level connection-loss handling stops audio.
- Foreground-service process death does not automatically resume audio.
- Reboot does not start the audio service.
- Reinstall cannot restore in-memory authorization.
- Network/connection loss terminates active audio instead of buffering indefinitely.

## Privacy

No raw audio, PCM, encoded media, transport credentials, or microphone content is logged or persisted.

Only bounded in-memory frame buffers exist inside the capture path.

## Manual Android verification plan

1. Enroll a Managed device.
2. Verify `RECORD_AUDIO` permission state.
3. Issue an authorized audio session from Admin.
4. Deliver a valid START command.
5. Confirm missing permission produces `PERMISSION_REQUIRED` without background permission bypass.
6. With legitimate permission and a defined production media transport, confirm capture starts and the microphone foreground-service notification is visible.
7. Issue STOP and confirm microphone release/service shutdown.
8. Expire the authoritative audio session and confirm capture stops.
9. Revoke the device and confirm capture stops.
10. Disconnect the managed-device communication session and confirm capture stops.
11. Kill the process and confirm capture does not restart.
12. Reboot and confirm capture does not restart.
13. Replay a command and confirm it is rejected/idempotently handled.
14. Change the audio session ID or payload shape and confirm rejection.
15. Confirm no audio file/database/log artifact is left behind.
16. Verify exported Android components cannot directly trigger audio capture.

## Phase status

The Managed-side security/reliability hardening is implemented, but full Phase 10 live-audio completion remains blocked until the backend/client contract defines a production audio-byte transport. No speculative media transport is introduced in this phase.
