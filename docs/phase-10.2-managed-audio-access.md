# Phase 10.2 — Managed Android Audio Access & Microphone Capture Foundation

## Scope

This phase adds the Managed Android security and lifecycle foundation for explicitly authorized live audio access. It does not add Admin playback, recording/history, covert microphone access, permission bypasses, or hidden Android APIs.

## Android authorization

The app declares:

- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`

Microphone access is never treated as implied by Device Owner or Profile Owner state. The runtime permission is checked immediately before capture/service startup. When the backend command arrives without permission, the device enters `PERMISSION_REQUIRED` and posts a visible notification that opens the existing Managed activity; the runtime permission request is launched from that visible activity rather than from a background command handler.

The microphone foreground service is non-exported and uses the Android `microphone` foreground-service type with a persistent, truthful notification.

## Local state

Managed local capture state is separate from the backend audio-session lifecycle:

`IDLE`, `PERMISSION_REQUIRED`, `STARTING`, `ACTIVE`, `STOPPING`, `STOPPED`, `FAILED`.

No active microphone session is restored after process death or reboot because capture state is kept in memory only.

## Command binding

The existing Phase 6 command processor remains the security boundary. Audio commands are allowlisted as:

- `START_AUDIO_ACCESS`
- `STOP_AUDIO_ACCESS`

Payload parsing accepts exactly:

`{ "audioSessionId": "<uuid>" }`

The command processor still validates the authenticated device session, expected managed-device identity, command expiry, idempotency, and handler allowlist before the audio handler executes.

## Capture architecture

The microphone layer is isolated behind `AudioCaptureController` and uses Android `AudioRecord` with `MediaRecorder.AudioSource.MIC`, mono PCM 16-bit capture, and configuration fallback from 48 kHz to 16 kHz.

The capture loop uses a fixed approximately 20 ms frame buffer and immediately passes each frame to the transport boundary. There is no disk, Room, SharedPreferences, cache, log, or analytics storage of microphone bytes.

Capture initialization and read failures release `AudioRecord` deterministically.

## Transport boundary

Phase 10.1 supplies authenticated audio-session lifecycle APIs and command delivery, but it deliberately does not define a production media-byte transport. Therefore this phase introduces the `AudioTransport` abstraction and an explicit unavailable production implementation.

When no media transport exists, the Managed app fails closed with `AUDIO_TRANSPORT_UNAVAILABLE` and does not start microphone capture. This prevents accidental local recording or an invented incompatible media protocol.

The authenticated device command stream is the existing backend SSE channel. Audio session lifecycle acknowledgements are wired to:

- `POST /api/v1/device/audio-sessions/{sessionId}/started`
- `POST /api/v1/device/audio-sessions/{sessionId}/stopped`

No audio bytes are sent to these control endpoints.

## Lifecycle and failure behavior

- Duplicate starts for the same active session are idempotent.
- A different active session is rejected.
- Stop is idempotent and releases capture/service resources.
- Device communication disconnect stops local audio access.
- Missing or revoked `RECORD_AUDIO` permission fails closed.
- A stale/mismatched session ID cannot start the service.
- Service restart without a matching in-memory `STARTING` session fails closed.
- The service is `START_NOT_STICKY`; reboot/process restart does not silently restart microphone access.
- Capture buffers are bounded to a single frame and are never queued indefinitely.
- Raw audio is never logged or persisted.

## Security review

The audio foreground service is `exported=false`. No external activity, service, or broadcast receiver can invoke microphone capture directly.

No accessibility API, root operation, hidden Android API, OEM workaround, or Device Owner permission bypass is used.

The foreground-service implementation follows Android's microphone FGS requirements: the app must have `RECORD_AUDIO` before the microphone service is started, and the service declares the microphone FGS type. citeturn0search0turn0search2

## Manual test plan

1. Grant microphone permission in the visible Managed app.
2. Deny microphone permission and verify `PERMISSION_REQUIRED`.
3. Revoke microphone permission while an audio operation is pending and verify fail-closed behavior.
4. Deliver a valid `START_AUDIO_ACCESS` command and verify exact session binding.
5. Deliver an invalid/legacy payload and verify rejection.
6. Deliver a duplicate start and verify no second capture instance.
7. Deliver `STOP_AUDIO_ACCESS` twice and verify idempotent cleanup.
8. Disconnect the device session and verify local audio access stops.
9. Kill the process and verify no audio state is restored.
10. Reboot the device and verify microphone capture does not restart.
11. Verify the microphone foreground notification is visible and truthful.
12. Verify no audio bytes occur in logs or persistent storage.
13. Verify a real media transport implementation is required before live audio bytes can be transmitted.

## Verification boundary

Unit tests can validate command parsing, local states, and the fail-closed transport boundary. Real `AudioRecord`, Android permission dialogs, microphone privacy controls, foreground-service behavior, process death, reboot, and Device Owner/Profile Owner behavior require a physical Android device or emulator run and are not claimed as CI-verified.

## Cross-repository requirements

No backend or Admin repository was modified.

The only remaining backend-side requirement for full live audio is a documented production media-byte transport contract. Phase 10.2 does not invent that protocol. The existing Phase 10.1 control/session contract is consumed as-is.
