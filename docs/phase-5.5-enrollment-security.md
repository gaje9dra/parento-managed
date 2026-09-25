# Phase 5.5 — Managed Android Enrollment Security Hardening

## Scope

Phase 5.5 is limited to gaje9dra/parento-managed. It hardens the Managed Android enrollment client established in Phase 5.2 and does not add Phase 6 functionality.

## Identity model

The Managed app keeps four identities separate:

1. Local installation identity — an app-generated UUID persisted in Room.
2. ManagedDevice ID — the backend-assigned managed-device record ID, persisted only after successful enrollment.
3. Android management identity — NOT_MANAGED, PROFILE_OWNER, DEVICE_OWNER, or UNKNOWN, detected from DevicePolicyManager.
4. Enrollment identity/session — the temporary backend enrollment ID and one-time authorization secret.

No IMEI, serial number, MAC address, advertising ID, or hidden hardware fingerprint is used as a primary identity.

## Enrollment state model

The persistent Parento enrollment state is:

- UNENROLLED
- ENROLLING
- ENROLLED
- REVOKED
- ERROR

Valid recovery paths are:

- UNENROLLED -> ENROLLING
- ENROLLING -> ENROLLED
- ENROLLING -> ERROR
- ENROLLING -> UNENROLLED
- ENROLLED -> REVOKED
- ENROLLED -> ERROR
- ERROR -> UNENROLLED
- ERROR -> ENROLLING

REVOKED is terminal in the current local state model. It cannot be silently changed to ENROLLED.

Connection state remains a separate model: UNKNOWN, DISCONNECTED, CONNECTING, CONNECTED. A connected state is never treated as enrollment proof, and enrollment is never treated as proof of a live connection.

Android management state is also independent. Device Owner/Profile Owner status is detected from Android and is never inferred from Parento enrollment.

## One-time enrollment authorization

The backend consume operation is one-time. The Managed client therefore treats an API failure as an uncertain outcome. After a consume attempt fails, the temporary authorization is cleared and local enrollment moves to ERROR. The same one-time secret is never automatically retried or restored after process death.

A new attempt requires a fresh administrator-issued enrollment authorization.

This is intentional because the current backend contract has no Managed-side status/recovery endpoint that can safely determine whether an ambiguous consume request committed.

## Process-death recovery

A pending authorization is stored only in Android encrypted storage so an ordinary process restart can resume an enrollment that has not yet been consumed.

On startup:

- ENROLLING + valid encrypted authorization resumes the pending enrollment.
- UNENROLLED + valid encrypted authorization is repaired to ENROLLING.
- ENROLLING + missing authorization becomes ERROR.
- expired authorization is cleared and the state returns to UNENROLLED.
- ERROR + leftover authorization is cleared and returned to UNENROLLED; it is never replayed.
- ENROLLED or REVOKED never restores a stale pending authorization.

Only the minimum temporary enrollment data is persisted.

## Reboot and reinstall semantics

A normal reboot/application restart retains the Room installation identity and backend ManagedDevice ID. Android management mode is re-evaluated from DevicePolicyManager; cached management metadata is diagnostic only.

Application reinstall/data clear removes application-local Room and encrypted enrollment storage. The Managed app does not attempt to reconstruct the old installation identity from hardware identifiers.

Device Owner/Profile Owner state remains controlled by Android Enterprise provisioning and is not fabricated by the app.

## Network contract

The Managed app consumes only:

POST /api/v1/devices/enrollments/{enrollmentId}/consume

Request fields:

- authorizationSecret
- localInstallationIdentity
- name
- platform: android

The client does not call the administrator-only create/list/status/cancel enrollment endpoints.

Successful responses must contain data.managedDeviceId, data.enrollment.id, and data.enrollment.expiresAt. The returned enrollment ID must match the authorization being consumed and the managed-device ID must be non-blank before local enrollment is completed.

Relevant error mapping:

- 400 -> invalid enrollment state
- 401 / 403 -> authorization failure
- 404 ENROLLMENT_NOT_FOUND -> invalid enrollment state
- 409 / 410 -> invalid/consumed/expired enrollment
- 429 / RATE_LIMITED -> rate limited
- 408 / 5xx -> network/transient failure
- other responses -> unknown failure

The client does not expose server response bodies to logs or users.

## Transport security

Production configuration requires an HTTPS backend URL. The application manifest disables cleartext traffic globally.

No certificate-pinning or custom trust-store logic is introduced. Platform TLS and hostname verification remain authoritative.

Enrollment requests use bounded connect/read timeouts and a 64 KiB maximum response body to avoid unbounded memory consumption.

## Sensitive-data handling

Temporary enrollment authorization is stored with EncryptedSharedPreferences using an AndroidX Security MasterKey and AES-256-GCM value encryption. It is cleared after completion, cancellation, expiration, invalid terminal recovery, or an uncertain consume failure.

The secret is not logged, included in analytics, placed in crash messages, or copied to the clipboard.

The Activity uses FLAG_SECURE, so enrollment content is treated as secure window content and is prevented from appearing in screenshots or non-secure displays.

Application backup remains disabled.

## Duplicate prevention

The enrollment repository serializes operations with a coroutine Mutex. The ViewModel also ignores duplicate actions while busy.

The repository, rather than the UI alone, rejects enrollment operations from invalid local states.

## No background enrollment polling

No permanent enrollment polling or realtime communication was added. WorkManager remains only the existing scheduling boundary and does not run enrollment work.

## Android Enterprise boundary

The Managed app uses legitimate DevicePolicyManager/Android Enterprise detection. It does not use root, hidden APIs, accessibility abuse, permission bypasses, covert persistence, or security-policy bypasses.

## Testing focus

Phase 5.5 adds security-focused repository tests covering:

- one-time authorization clearing after network failure
- no replay of leftover authorization after an ERROR state
- fail-closed handling of a successful response whose enrollment ID does not match the consumed authorization

Existing lifecycle, persistence, management-state, and migration tests remain in the repository verification workflow.

## Backend dependency

No backend change was made.

The current Managed-side limitation is that the backend contract does not expose a Managed-device recovery/status operation after an ambiguous consume request. Therefore the client intentionally fails closed rather than retrying a possibly consumed one-time authorization.

## Phase 6 boundary

No WebSockets, Socket.IO, FCM command handling, remote lock/wipe, camera, microphone, audio streaming, screen capture, live location, application blocking, website filtering, network filtering, remote policy enforcement, or remote device configuration is implemented in Phase 5.5.
