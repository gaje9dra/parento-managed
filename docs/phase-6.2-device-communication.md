# Phase 6.2 — Managed Android Device Communication

## Scope

This phase adds the managed-device session and command-execution foundation. It does not add sensitive device-control features.

## Session architecture

Enrollment, Android management state, and communication are separate. The client retains the persistent device credential in encrypted preferences and obtains a short-lived session token from POST /api/v1/device/sessions. Session state is exposed through StateFlow and persisted only as a local diagnostic snapshot.

Connection lifecycle: UNKNOWN → DISCONNECTED → CONNECTING → AUTHENTICATING → CONNECTED, with controlled RECONNECTING, DISCONNECTING, and FAILED transitions.

A process restart never treats a cached token as proof of a live connection. The client clears expired session material and reconnects using the device credential. Foreground lifecycle heartbeats an active session.

## Transport boundary

DeviceTransport owns session lifecycle and command lifecycle reporting. HttpsDeviceTransport is the production HTTPS adapter and uses platform TLS validation. No cleartext transport, trust-all certificates, hostname-verification bypass, WebSocket, SSE, FCM, or undocumented endpoint is introduced.

The Phase 6.1 backend currently has no device-side command delivery endpoint. receiveNextCommand() therefore returns no command rather than inventing a transport. A later approved delivery adapter can implement the same boundary without changing command business logic.

## Command boundary

Commands are validated for UUID/device identity, type/version, JSON object payload, size, timestamps, expiration, and metadata lengths. A strict handler map provides the allowlist. Duplicate command IDs are persisted in Room and serialized with a coroutine Mutex.

Only FUTURE_COMMAND version 1 is currently defined by the backend, and its client placeholder handler deliberately fails as unsupported. It performs no device action.

## Persistence and recovery

Room schema version 6 adds managed_commands with lifecycle, result, correlation, idempotency, and timestamp fields. MIGRATION_5_6 is explicit. Authentication material is never stored in Room.

## Security boundary

No arbitrary shell/code execution, dynamic loading, covert sensor access, security bypass, or future monitoring/control feature is implemented.

## Backend dependency

The Phase 6.1 backend must later expose an approved command-delivery/realtime mechanism before the managed app can actually receive server-issued commands. The existing session and acknowledgement/result endpoints are consumed read-only from this repository.
