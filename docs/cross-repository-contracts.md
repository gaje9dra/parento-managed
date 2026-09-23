# Cross-Repository Contract Notes — Managed App

Phase 2 documents the future relationship without implementing the protocol.

## Repositories

- `gaje9dra/parento-managed` — managed-device Android application.
- `gaje9dra/parento-backend` — backend/API/database infrastructure.
- `gaje9dra/parento-admin` — administrator/controller Android application.

## Current managed boundary

The Managed app currently owns local installation identity, local enrollment lifecycle state, and runtime connection state. It does not authenticate, enroll with, or communicate with the backend in Phase 2.

The Admin app does not directly connect to the Managed app. Future management communication must be backend-mediated and explicitly authorized.

## Phase 2 compatibility notes

### Identity

- Backend `admins.id`, `managed_devices.id`, and `enrollments.id` are server-owned UUIDs.
- Backend `managed_devices.stable_identifier` is a separate server-side stable identifier.
- Managed `installationId` is a locally generated UUID identifying the Managed application installation.
- Admin `installationId` is a locally generated UUID identifying the Admin application installation.
- Android installation IDs are not authenticated backend identities and must not be used as authorization credentials.

### State

The Managed enrollment lifecycle is a local device-side model:

`UNENROLLED`, `ENROLLING`, `ENROLLED`, `CONNECTED`, `DISCONNECTED`, `REVOKED`, `ERROR`.

The separate Managed connection state is runtime-only:

`UNKNOWN`, `DISCONNECTED`, `CONNECTING`, `CONNECTED`.

The backend currently persists separate enrollment and operational status concepts. A future API contract must explicitly map those server states to device-side lifecycle state; enum names must not be assumed equivalent.

Admin `UNCONFIGURED` / `READY` is local Admin setup state only. It is not equivalent to backend administrator status, Managed enrollment status, or Managed connection state.

### Persistence and timestamps

Backend timestamps use PostgreSQL `TIMESTAMPTZ`. Managed local persistence uses epoch-millisecond numeric timestamps.

A future API contract must serialize backend timestamps explicitly (for example UTC/ISO-8601) and convert them at the Android boundary. Local Room fields are not wire-format contracts.

### Errors

Backend HTTP errors contain a stable error code, message, and request ID. Managed currently exposes domain-level `ManagedError` values and does not translate backend errors.

A future API client must map backend error codes to domain errors without exposing raw HTTP, PostgreSQL, Room, or infrastructure exceptions to UI.

## Future relationship

Parento Managed will eventually authenticate/enroll with Parento Backend. After authorized enrollment, the managed app will consume backend-mediated device-management contracts.

Parento Admin will eventually authenticate separately with the backend and perform authorized administrator operations through backend contracts.

The Admin application must not directly connect to the Managed application.

## Phase 2 boundary

No authentication, enrollment, QR pairing, backend API client, WebSocket, FCM, device identity provisioning, command channel, or policy synchronization is implemented here.

Any backend endpoint or administrator operation required by a future managed feature must be specified and implemented in the appropriate repository in a later phase.

## Security principle

Future sensitive operations must be attributable to an authorized principal, use platform-supported Android/Android Enterprise authorization where applicable, and must not bypass permissions or conceal management behavior.

Only `gaje9dra/parento-managed` is modified by this phase.
