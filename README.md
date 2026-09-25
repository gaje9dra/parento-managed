# Parento Managed

## Phase 6.2 — Device Communication & Secure Command Execution Foundation

This repository is the managed-device Android application. Phase 6.2 adds the communication/session foundation and local command-processing security boundary while preserving Phases 1–5.

### Repository boundary
- gaje9dra/parento-managed is the only repository modified by this phase.
- gaje9dra/parento-backend and gaje9dra/parento-admin are read-only references.

### Identity boundaries
- local installation UUID
- backend ManagedDevice ID
- Android Device Owner/Profile Owner management identity
- enrollment authorization
- persistent device credential
- short-lived communication session

The device credential and session token are stored only in Android encrypted preferences. They are never placed in Room, UI state, or logs.

### Communication architecture
Application → communication session manager → DeviceTransport → HTTPS transport → Parento backend

Business logic depends on DeviceTransport rather than HttpURLConnection. Production communication requires HTTPS and platform certificate/hostname validation. Cleartext traffic remains disabled.

Connection state is independent from enrollment:
- UNKNOWN
- DISCONNECTED
- CONNECTING
- AUTHENTICATING
- CONNECTED
- RECONNECTING
- DISCONNECTING
- FAILED

A process restart does not treat a cached session token as proof of a live connection. The app re-establishes a session using the encrypted device credential.

### Command architecture
Transport → command model → validation → strict allowlist → idempotency/replay check → authorization → handler → acknowledgement → result

Room schema version 6 adds managed_commands for minimum lifecycle/idempotency persistence. Command IDs are unique and processing is serialized with a coroutine Mutex.

Validation covers command/device identity, type/version, JSON payload, payload size, timestamps/expiration, correlation/idempotency lengths, and the expected ManagedDevice ID.

There is no generic payload executor, shell, eval, script runner, dynamic code loading, remote-code mechanism, or permission bypass.

The only backend command type currently defined is FUTURE_COMMAND version 1. It is an infrastructure placeholder and is deliberately reported as an unsupported/failed result without performing a device action.

### Backend contract dependency
Phase 6.1 exposes session lifecycle and command acknowledgement/start/result endpoints. The enrollment consume response supplies the one-time device credential; Phase 6.2 persists it immediately in encrypted storage before clearing the temporary enrollment authorization.

Phase 6.1 does not expose a device-side command delivery/realtime endpoint. This phase therefore does not invent polling, WebSockets, SSE, FCM, or another undocumented transport. DeviceTransport.receiveNextCommand() is the explicit extension boundary for the later approved delivery transport.

### Recovery and background behavior
- expired session material is cleared
- process death/reboot does not imply a live connection
- foreground lifecycle can refresh management state and heartbeat an active session
- no hidden foreground service or perpetual background loop was added
- no aggressive polling or reconnect storm was introduced

### Deferred features
Camera, microphone/audio, screen capture/streaming, live location, app or website blocking, network filtering, remote lock/wipe/reboot, policy enforcement, covert monitoring, arbitrary command execution, and security/permission bypasses remain outside Phase 6.2.

### Verification
The repository GitHub Actions workflow runs unit tests, lint, instrumented Room tests, debug build, and release build. A check is only considered complete after its actual workflow result is available.