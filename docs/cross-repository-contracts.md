# Cross-Repository Contract Notes — Managed App

Phase 1.5 documents the future relationship without implementing the protocol.

## Repositories

- `gaje9dra/parento-managed` — managed-device Android application.
- `gaje9dra/parento-backend` — backend/API/database/realtime infrastructure.
- `gaje9dra/parento-admin` — administrator/controller Android application.

## Future relationship

Parento Managed will eventually authenticate/enroll with Parento Backend. After authorized enrollment, the managed app will consume backend-mediated device-management contracts.

Parento Admin will eventually authenticate separately with the backend and perform authorized administrator operations through backend contracts.

The Admin application must not directly connect to the Managed application.

## Phase 1.5 boundary

No authentication, enrollment, QR pairing, backend API client, WebSocket, FCM, device identity provisioning, command channel, or policy synchronization is implemented here.

Any backend endpoint or administrator operation required by a future managed feature must be specified and implemented in the appropriate repository in a later phase.

## Security principle

Future sensitive operations must be attributable to an authorized principal, use platform-supported Android/Android Enterprise authorization where applicable, and must not bypass permissions or conceal management behavior.

Only `gaje9dra/parento-managed` is modified by this phase.