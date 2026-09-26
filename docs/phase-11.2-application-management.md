# Phase 11.2 — Managed Android Application Inventory & Management Foundation

## Scope

This phase is implemented only in `gaje9dra/parento-managed`.

It establishes:
- deterministic installed-application inventory collection through Android PackageManager
- package-name normalization and validation
- bounded, authenticated inventory synchronization
- WorkManager-based periodic inventory scheduling
- local synchronization metadata and policy-reference state
- allowlisted REQUEST_APPLICATION_INVENTORY and SYNC_APPLICATION_POLICY command handlers
- explicit desired-policy vs enforcement state separation
- Room migration for the new local state

Final application blocking is NOT implemented.

## Inventory architecture

The flow is:

PackageManager -> ApplicationInventoryCollector -> ApplicationInventorySync -> DeviceTransport -> Phase 11.1 backend

The collector queries applications visible to the Android process through supported PackageManager APIs, maps only management-relevant metadata, validates package identifiers, removes duplicate package identifiers, sorts by package name, applies a bounded application count, and attaches one observation timestamp.

No application private data, APK contents, databases, credentials, messages, or files are collected.

The full inventory is not persisted locally. Only synchronization metadata is persisted.

## Package visibility

This implementation does not add a blanket package-visibility bypass. PackageManager returns the applications visible under the platform/package-visibility rules applicable to the managed application.

If production deployment requires broader visibility for the legitimate enterprise management use case, that requirement must be reviewed against the Android version and distribution policy before adding a visibility declaration.

## Synchronization

Inventory uploads use the existing authenticated DeviceCommunicationSessionManager and DeviceTransport. No new HTTP client, authentication mechanism, realtime client, or credential store is introduced.

Backend endpoint: POST /api/v1/device/applications/inventory

The payload uses schema version 1, a synchronization UUID, an ISO-8601 observation timestamp, and normalized application records.

A successful collection is not considered synchronized until the authenticated backend request succeeds.

The periodic worker uses the existing WorkManager dependency and the repository's bounded exponential retry policy. It requires network connectivity and storage availability and does not maintain an unlimited pending queue.

## Resource limits

The collector is bounded to 1000 applications.

The sync layer rejects payloads above 60 KiB before upload. This leaves margin below the Phase 11.1 backend request limit.

The current Phase 11.1 backend contract does not define multi-part inventory synchronization. Therefore this phase does not invent client-side chunking that could overwrite earlier inventory snapshots.

## Local state

Room schema version increases from 8 to 9.

Persisted application-management metadata includes inventory sync status, last observed inventory timestamp, last successful inventory synchronization timestamp, desired application policy ID/version, highest accepted application policy version, policy synchronization status, and application enforcement status.

The installed application list itself is not persisted.

## Policy synchronization

The Managed command boundary now allowlists REQUEST_APPLICATION_INVENTORY version 1 and SYNC_APPLICATION_POLICY version 1.

REQUEST_APPLICATION_INVENTORY requires an empty JSON object and performs an authenticated inventory synchronization.

SYNC_APPLICATION_POLICY requires exactly policyId and policyVersion.

The handler rejects malformed payloads, invalid UUIDs, invalid versions, conflicting equal-version policy IDs, and stale policy versions.

A newer policy reference is stored as desired state and changes local enforcement state to PENDING. The app never reports APPLIED merely because a policy reference was received.

### Backend contract limitation

The Phase 11.1 backend command currently sends only the policy ID and version for SYNC_APPLICATION_POLICY. The backend currently exposes no Managed-device endpoint to retrieve the full application policy/rules.

Therefore this phase intentionally does not invent a policy-fetch endpoint and does not pretend that the policy rules were downloaded. The Managed app records the authoritative desired policy reference and reports PENDING/unavailable enforcement state until a later approved contract supplies the policy rules.

No backend repository was modified as part of Phase 11.2.

## Enforcement boundary

This phase does not implement force-stop, package disabling, uninstall enforcement, launcher hacks, Accessibility-based blocking, root access, hidden APIs, or anti-uninstall bypasses.

If Android enforcement is not actually performed, the local state remains non-APPLIED.

## Security and privacy

The application-management boundary reuses existing ManagedDevice identity, enrollment state, encrypted device credential/session stores, authenticated transport, command validation/replay protection, and WorkManager scheduling.

Inventory synchronization requires enrolled state, a persisted ManagedDevice ID, a valid authenticated session, and Android managed mode (DEVICE_OWNER or PROFILE_OWNER).

No authentication tokens or full inventory payloads are added to logs.

## Manual Android test plan

1. Enroll a supported managed device.
2. Verify Device Owner/Profile Owner management state.
3. Run inventory collection.
4. Verify package names are normalized and sorted.
5. Verify system applications visible to the managed app are not removed by an undocumented blacklist.
6. Verify the inventory is bounded.
7. Verify successful inventory synchronization updates the local successful-sync timestamp.
8. Disable backend connectivity and verify the sync is marked failed and retries remain bounded.
9. Restore connectivity and verify a subsequent scheduled/command-triggered sync succeeds.
10. Send a valid REQUEST_APPLICATION_INVENTORY command.
11. Send an invalid inventory command payload and verify rejection.
12. Send a newer SYNC_APPLICATION_POLICY reference and verify desired policy version advances.
13. Send a duplicate policy reference and verify it is idempotent.
14. Send an older policy version and verify the newer desired policy is preserved.
15. Send an equal-version policy with a different policy ID and verify rejection.
16. Reboot the device and verify persisted application-management metadata is recovered.
17. Kill/restart the app process and verify no application inventory history is reconstructed from stale in-memory state.
18. Revoke the device and verify inventory synchronization is not authorized.
19. Verify application private data is never collected.
20. Verify no application metadata is emitted into notifications.

## Android-version limitations

Application visibility is subject to Android package-visibility rules. The implementation intentionally does not bypass those rules.

PackageInfo.longVersionCode is used where supported; older supported API levels use the compatible version-code field.

The inventory model does not assume that every application exposes a category or non-null version name.

## Completion boundary

Phase 11.2 provides the Managed Android inventory and management foundation. Final application blocking/enforcement remains a later Phase 11 subphase.