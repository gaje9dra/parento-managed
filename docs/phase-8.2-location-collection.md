# Phase 8.2 — Managed Android Location Collection & Secure Reporting

## Scope

This phase adds Android-side location collection and reporting only. It uses legitimate Android location APIs, the existing authenticated managed-device communication session, WorkManager, and a single latest-location Room record.

The Admin map, location history, geofencing, route analytics, and later device-control features are not implemented.

## Architecture

Android LocationManager → LocationProvider → LocationCoordinator → Room LocationRepository → LocationReporter → DeviceCommunicationSessionManager → Existing HTTPS DeviceTransport → POST /api/v1/device/location

No second networking stack, realtime connection, authentication mechanism, or device identity was introduced.

## Permissions

The manifest declares ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, and ACCESS_BACKGROUND_LOCATION. Foreground permission is requested through the Android runtime permission API. When foreground access exists but scheduled background access is still required, the managed app directs the user to Android application settings rather than bypassing the OS permission model.

Location services and provider availability are checked independently of permission state.

## Collection behavior

Android LocationManager is used. On Android 11+ the implementation uses getCurrentLocation with a bounded 20-second timeout. On older supported Android versions, a recent provider location is used when available.

Coordinates are rejected if non-finite or outside legal latitude/longitude ranges. Accuracy, altitude, bearing, and speed remain optional and are preserved only when Android reports valid values. Missing values are not converted to zero.

No continuous GPS polling is used.

## Background execution

A unique WorkManager periodic job runs no more frequently than Android's supported periodic-work minimum (15 minutes) and requires network connectivity. The worker stops successfully when the device is unenrolled or when required permissions are unavailable, and uses bounded WorkManager retry behavior for transient collection/reporting failures.

No persistent foreground service was introduced because Phase 8.2 uses bounded periodic collection rather than continuous tracking.

## Local persistence

Only one location_state row is retained. It stores report identifier, availability, latest valid coordinates when available, optional accuracy/altitude/bearing/speed, original observed timestamp, last report status, last report attempt time, and last successful report time.

There is no location history table and no unbounded retry queue.

## Reporting and authentication

Location reports use the existing authenticated device session. The client does not send a client-supplied managed-device ID as an authorization mechanism.

The request matches the Phase 8.1 contract: reportId, availability, optional coordinates/accuracy, and observedAt. The server remains authoritative for receivedAt.

Authentication or authorization failures clear the local session through the existing communication manager. Expired/revoked devices therefore cannot continue successfully reporting.

## Availability states

Internal capability states distinguish AVAILABLE, PERMISSION_REQUIRED, PERMISSION_DENIED, BACKGROUND_PERMISSION_REQUIRED, LOCATION_SERVICES_DISABLED, PROVIDER_UNAVAILABLE, TEMPORARILY_UNAVAILABLE, and ERROR.

Connection state remains separate from location state. A device may have a valid local location while offline, or be connected while location permission is unavailable.

## Privacy and logging

Coordinates are not routinely logged. No new local debug endpoint exposes location data. Only the latest location is retained locally.

## Testing

Unit tests cover coordinate validation, bounded persistence, replacement of the latest state, and report-status tracking. Instrumentation coverage includes the Room 7→8 migration. Manual verification should use emulator/mock locations or controlled test devices rather than real user locations.

## Manual verification

1. Fresh install.
2. Open the managed app.
3. Grant foreground location permission.
4. Grant background location access through Android settings when requested.
5. Enable location services.
6. Provide an emulator/test-device mock location.
7. Confirm local capability reports AVAILABLE.
8. Confirm the scheduled worker can execute with network connectivity.
9. Disable network and verify no unbounded queue is created.
10. Restore network and verify the latest stored report can be sent using its original observed timestamp.
11. Deny/revoke location permission and verify collection stops.
12. Disable location services and verify the capability becomes LOCATION_SERVICES_DISABLED.
13. Revoke/un-enroll the managed device and verify successful reporting stops.
14. Verify no full coordinates appear in routine logs.

## Out of scope

Admin location UI, location history, geofencing, route tracking, location analytics, covert tracking, permission bypasses, hidden APIs, camera, microphone, screen capture, remote wipe/lock/shell/device-control features, backend repository changes, and admin repository changes.
