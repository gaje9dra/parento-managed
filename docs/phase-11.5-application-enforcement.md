# Phase 11.5 — Managed Android Application Blocking Enforcement

Phase 11.5 implements the Managed Android enforcement boundary only. It consumes the existing authenticated application-policy endpoint and command/SSE architecture; it does not change Backend or Admin.

## Enforcement mechanism

The Managed app uses Android DevicePolicyManager.setPackagesSuspended(...) through AndroidApplicationEnforcementPlatform.

Android documents this API as available to device owners and profile owners. A suspended package cannot start activities and has notifications/recents/toasts/dialogs/ringing suppressed. Android may refuse to suspend protected packages such as the active launcher, package installer/verifier, default dialer, permission controller, or device-admin applications. The implementation treats those failures as enforcement failures rather than claiming success.

No root, shell, hidden API, accessibility service, overlay, exploit, or privilege escalation is used.

## Management modes
- DEVICE_OWNER: application suspension enforcement is supported when the platform API is available.
- PROFILE_OWNER: application suspension enforcement is supported for the managed profile through the same supported API.
- NOT_MANAGED: enforcement fails closed and is reported as UNSUPPORTED_MANAGEMENT_MODE.
- UNKNOWN: no enforcement is attempted.
- Android versions below API 24: application suspension is unsupported.

The Managed app never attempts to promote itself to Device Owner or otherwise escalate privileges.

## Policy flow

Admin → Backend → authenticated Managed session → existing command/SSE transport → policy retrieval → local evaluator → DevicePolicyManager → enforcement-status report.

The existing SYNC_APPLICATION_POLICY command is validated against the existing command schema. The Managed client then retrieves the authoritative current policy from GET /api/v1/device/application-policy over the authenticated device session.

The client distinguishes:
- desired policy: policy ID/version/rules received from Backend;
- accepted version: newest policy version accepted locally;
- applied policy: policy version for which Android enforcement actually completed;
- enforcement status: APPLIED, PARTIALLY_APPLIED, FAILED, PENDING, or STALE.

A policy reference is never treated as successfully enforced merely because a command was delivered.

## Evaluation semantics

Rules are deterministic by package name.

Supported actions:
- ALLOW
- BLOCK

A package present in a BLOCK rule is suspended when installed and not already suspended.

An ALLOW rule causes a package previously managed and suspended by Parento to be unsuspended.

Packages without a rule are not modified unless Parento previously suspended them under the active application-management policy. This prevents the app from unsuspending packages controlled by another administrator or Android system policy.

Conflicting duplicate rules are rejected before any package is modified.

The Managed package itself is never targeted by application-policy suspension.

## Policy removal and disabled policies

When Backend reports no effective policy, the local desired policy is cleared while the set of packages previously suspended by Parento is retained long enough to safely unsuspend those packages.

A policy removal therefore does not blindly unsuspend every package that happens to be suspended on the device.

## Partial enforcement

Each package is processed independently.

The engine preserves successful Android operations when another package fails. It records attempted operations, successful operations, failed operations, and the resulting set of packages actually known to be suspended by Parento.

The overall state is APPLIED when all requested changes are successful, PARTIALLY_APPLIED when some operations succeed and others fail, and FAILED when enforcement cannot be completed.

Backend enforcement reporting is sent only after the Android operation has been attempted and verified through PackageManager state.

## Recovery

Desired policy rules and Parento-managed blocked-package state are persisted locally.

Recovery occurs during Managed app initialization, through the existing WorkManager application inventory cycle, after process restart/reboot, and after connectivity recovery when the normal session is available.

No continuous polling loop or permanent background service is introduced.

## Inventory

The existing PackageManager inventory collector remains bounded and deterministic. It uses normal package visibility behavior and does not add hidden/unauthorized package visibility mechanisms.

Inventory remains limited to package name, label, version information, enabled state, and observation time.

## Command transport

The existing authenticated SSE device stream is consumed by ManagedCommandRuntime. It is not a second realtime system.

Command validation remains allowlisted and version-bound. Application-management commands are never converted into shell commands or executable payloads.

## Persistence migration

Room database version advances from 9 to 10.

Migration 9→10 adds desired policy rules JSON, applied policy ID, applied policy version, and the Parento-managed blocked package set.

Existing data is preserved.

## Manual verification plan

A real managed Android test device should be used to verify:
1. Device Owner enrollment.
2. Managed app startup.
3. Inventory collection.
4. Active policy synchronization.
5. BLOCK policy for an installed application.
6. ALLOW policy restoration.
7. Policy version update.
8. Installation of a blocked package after policy creation.
9. Uninstallation and reinstall.
10. Offline/reconnect behavior.
11. Device reboot.
12. Process death.
13. Protected-package enforcement failure.
14. Partial policy enforcement.
15. Policy removal/disabled policy.
16. Device revocation.
17. Re-enrollment.

These manual cases are not claimed as executed by CI.

## Backend contract issue

No Backend or Admin modification is required for Phase 11.5.

The existing Backend contract already provides the Managed policy retrieval endpoint and enforcement-status reporting endpoint needed by this implementation. The existing command payload contains the policy ID/version; the Managed client retrieves the authoritative rules through its authenticated session.

## Limitations

Application suspension is an Android management capability, not universal application blocking. Android can refuse to suspend protected packages and OS/version/device-management behavior can differ. The app reports those cases as failures/partial enforcement instead of claiming universal blocking.

Phase 11.5 does not implement website filtering, network blocking, camera/microphone controls, location controls, screen sharing changes, remote lock/wipe, arbitrary commands, root, accessibility abuse, hidden APIs, or security bypasses.