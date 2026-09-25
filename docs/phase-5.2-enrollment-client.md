# Phase 5.2 — Managed Android Enrollment & Pairing Client

## Scope

This phase implements only the Managed Android enrollment client in `gaje9dra/parento-managed`.

It does not modify the backend or Admin repositories and does not implement remote commands, realtime, FCM, telemetry, surveillance, device restrictions, or Android security bypasses.

## Backend contract consumed

The client consumes the Phase 5.1 endpoint:

`POST /api/v1/devices/enrollments/{enrollmentId}/consume`

Request:

```json
{
  "authorizationSecret": "<43-character-base64url-secret>",
  "localInstallationIdentity": "<stable app-generated identity>",
  "name": "<1-100 character display name>",
  "platform": "android"
}
```

Successful response contains:

- `data.enrollment.id`
- `data.enrollment.expiresAt`
- `data.enrollment.status`
- `data.managedDeviceId`

The managed client does not send an administrator identity or administrator credentials.

### Backend-only operations

The Phase 5.1 create/list/status/cancel endpoints require administrator authentication. The Managed app therefore does not call them.

In particular, the Managed app's local **Cancel** action clears its pending local authorization and returns the local state to `UNENROLLED`; it does not claim that the administrator-side enrollment session was cancelled. The Admin side must cancel the backend session when required.

The Phase 5.1 contract also has no managed-client status/recovery endpoint. Therefore, if the process dies after the backend transaction commits but before the success response is durably persisted locally, the client cannot safely reconstruct the ManagedDevice ID. It fails closed rather than guessing or treating local state as backend authority. A future authenticated/recovery endpoint can close this gap without changing the local identity model.

## Flow

```
User-visible enrollment input
        |
        v
EnrollmentViewModel
        |
        v
EnrollmentRepository
        |
        +--> encrypted temporary enrollment store
        |
        v
EnrollmentApiClient
        |
        v
POST /consume
        |
        v
Local state transaction
  managedDeviceId + ENROLLED
```

The repository serializes enrollment operations with a coroutine mutex. Repeated taps and concurrent callers therefore cannot create multiple logical client operations at the same time.

## State

The existing authoritative local enrollment state remains:

- `UNENROLLED`
- `ENROLLING`
- `ENROLLED`
- `REVOKED`
- `ERROR`

Management state remains separate from enrollment state. Device Owner/Profile Owner detection never triggers automatic enrollment.

## Secret handling

- The authorization secret is accepted only through an intentional user-visible flow.
- The UI validates the documented 43-character secret length before saving.
- Pending authorization data is stored using Android encrypted preferences backed by an Android Keystore MasterKey.
- The raw authorization secret is never written to application logs.
- Successful completion clears the encrypted pending secret.
- Production enrollment requires HTTPS.
- The activity uses `FLAG_SECURE` while enrollment input is available to reduce accidental screenshot exposure.
- No password, admin token, IMEI, serial number, MAC address, contacts, location, media, or sensor data is submitted.

Android's current Jetpack Security release documentation lists `security-crypto:1.1.0` as the stable release; its crypto APIs are deprecated in favor of direct platform/Keystore APIs, so the current encrypted-preference boundary is intentionally isolated for later migration rather than introducing custom cryptography.

## Expiration

The client rejects an already-expired locally supplied authorization before making the consume request. Backend expiration remains authoritative.

A backend expiration/state error is mapped to a safe application error and does not trigger blind retries.

## Retry behavior

Enrollment requests are not automatically retried.

This is deliberate because the Phase 5.1 consume operation is one-time and transactional. A timeout can leave the backend outcome unknown; blindly repeating the one-time secret could turn a successful-but-unobserved enrollment into an ambiguous client state.

Network recovery must therefore use an explicitly supported backend recovery contract before automatic retries are added.

## Local persistence and recovery

The existing Room state remains the authoritative local application state for:

- local installation identity;
- backend ManagedDevice ID;
- enrollment state;
- management state;
- connection state.

Pending enrollment authorization is stored separately in encrypted local storage because it is temporary sensitive material.

After process death/reboot:

1. the installation identity is restored;
2. pending enrollment authorization is restored if still within its supplied expiry;
3. Android management state is independently re-evaluated;
4. no automatic enrollment is initiated.

## UI

The Managed app now exposes a user-visible enrollment foundation with:

- enrollment ID input;
- authorization secret input;
- expiry input;
- device name;
- save authorization;
- complete enrollment;
- cancel local enrollment;
- progress/status feedback;
- existing Android management state shown separately.

The secret field is password-style and the Activity uses secure-window presentation.

## Error mapping

The API boundary maps backend outcomes to safe application errors:

- invalid/expired/already-consumed/state-conflict/device-identity-conflict → `INVALID_STATE`
- authorization failure → `AUTHORIZATION_FAILURE`
- rate limit → `RATE_LIMITED`
- timeout/network/DNS/server failure → `NETWORK_FAILURE`
- malformed success payload → `UNKNOWN`

Raw backend error messages are not displayed directly.

## Security review

No code in this phase:

- silently enrolls a device;
- attempts to become Device Owner;
- bypasses Android permissions;
- uses root or hidden APIs;
- abuses Accessibility;
- implements surveillance;
- captures location/camera/microphone/screen;
- starts WebSockets/FCM command handling;
- executes remote commands;
- blocks applications/websites;
- locks/wipes the device.

## Verification

Unit coverage includes:

- legal/illegal enrollment transitions;
- pending authorization persistence;
- successful enrollment persistence;
- secret clearing after success;
- concurrent enrollment serialization.

UI/instrumentation and full Gradle verification remain dependent on the repository CI result and emulator/device environment.
