# Phase 12.2 — Managed Android Website & Network Blocking Enforcement Foundation

## Scope

This phase is limited to `gaje9dra/parento-managed`. The backend and Admin repositories are not modified. The implementation consumes the Phase 12.1 authenticated managed-device contract and existing Phase 6 command architecture.

## Platform capability finding

The current application targets Android API 26+ and uses Device Owner/Profile Owner detection through `DevicePolicyManager`. The inspected architecture does not expose a legitimate Android Enterprise API that universally blocks arbitrary hostnames across applications and transports.

Accordingly, Phase 12.2 does **not** simulate blocking and does not install a VPN, certificate, proxy, root firewall, hidden API, Accessibility-based blocker, shell rule, packet interceptor, or TLS interception layer.

For Device Owner, Profile Owner, and unmanaged devices the current network-policy capability is reported as `UNSUPPORTED` with `supported=false`. If Android management detection itself is unavailable or in transition, capability is reported as `UNKNOWN`. No capability is claimed without a platform mechanism that can actually enforce it.

## Policy model

The Managed client validates backend policy rules before storing or acting on them.

- Policy ID and positive version are required.
- Rules are `ALLOW` or `BLOCK`.
- Domains are trimmed/lowercased using the backend semantics.
- Exact domains and explicit leading `*.example.com` wildcards are supported.
- Wildcards match subdomains only; the apex is not matched.
- Other wildcard placement, URLs, ports, paths, control characters, malformed labels, and insufficient DNS labels are rejected.
- Duplicate normalized domains are rejected.
- Policy data is treated as data, never as executable content.

The authoritative rules are retrieved through the existing authenticated device session after a `SYNC_NETWORK_POLICY` command. A command carries only the policy ID/version; it is never interpreted as a command script.

## Enforcement boundary

`NetworkPolicyEnforcer` isolates platform-specific enforcement from synchronization and command handling.

The current Android implementation is deliberately fail-closed:

- capability discovery uses the existing DevicePolicyManager management state;
- known management modes report unsupported network-policy enforcement;
- unknown management state reports unknown capability;
- policy application returns `UNSUPPORTED`;
- removal returns `UNSUPPORTED`;
- no success state is synthesized.

Therefore a policy is never marked `APPLIED` merely because it was received or persisted.

## Desired / applied / enforcement state

Room schema version 10 adds a singleton `network_policy_state` record containing only the minimum reconciliation state:

- desired policy ID/version and validated policy JSON;
- applied policy ID/version;
- enforcement state;
- capability mode/support/version;
- last synchronization timestamp;
- pending synchronization flag;
- bounded error code.

The last authoritative policy is retained for offline/restart recovery. No browsing history, visited URLs, DNS history, page contents, TLS data, credentials, or packet contents are collected.

## Synchronization and version safety

The existing authenticated `DeviceTransport` is extended for:

- `GET /api/v1/device/network-policy`
- `POST /api/v1/device/network-policy/status`
- `POST /api/v1/device/network-policy/capability`

No second HTTP client or realtime system is introduced.

`SYNC_NETWORK_POLICY` and `REQUEST_NETWORK_POLICY_STATUS` are added to the existing command allowlist. Command identity, expiration, device identity, authorization, idempotency, and replay protection continue to be enforced by the Phase 6 command processor.

Before applying a policy, the client fetches the authoritative policy and compares it with the command's expected ID/version. A mismatched delivery is reported as `STALE`. A remote policy older than the locally retained desired version is never installed over the newer local state.

Policy removal is represented by an authoritative empty policy response. The client removes only Parento-managed policy state and never resets unrelated device networking.

## Offline, lifecycle, and recovery

The implementation uses the existing authenticated session and Room persistence. No continuous polling is added.

- Offline failures leave the last authoritative desired state intact.
- Duplicate commands are safe because the existing command database rejects replayed command IDs.
- Process death/app restart recovers the retained policy state from Room.
- Reboot re-evaluates Android management state through the existing DevicePolicyManager path.
- Revocation remains governed by existing enrollment/session lifecycle; new commands are rejected by the existing authenticated command boundary.
- Future supported enforcement can be added behind `NetworkPolicyEnforcer` without spreading platform-specific code into command processing.

## Privacy and security

This feature does not collect or infer browsing activity. It does not inspect URLs visited by the user, DNS history, packet contents, page contents, TLS traffic, credentials, or search queries.

The manifest gains no network-interception, VPN, Accessibility, root, shell, or hidden permissions. Backend policy input is validated before use, and logs do not contain full policy payloads.

## Android management limitations

Device Owner is not equivalent to unrestricted network interception. Profile Owner has narrower management authority. Unmanaged devices do not receive enterprise enforcement. The current phase therefore reports unsupported capability rather than claiming universal website blocking.

## Verification plan

The real-device plan is:

1. Provision Device Owner using a supported Android Enterprise/test provisioning flow.
2. Confirm the app reports Device Owner and network-policy capability as unsupported.
3. Deliver a valid `SYNC_NETWORK_POLICY` command through the existing command system.
4. Confirm authoritative policy retrieval and local desired-state persistence.
5. Confirm enforcement is reported as `UNSUPPORTED`, not `APPLIED`.
6. Deliver an older command version and confirm stale handling.
7. Remove the policy and confirm only Parento network-policy state is cleared.
8. Disable connectivity and confirm the last authoritative desired state remains.
9. Restart the process and confirm Room recovery.
10. Revoke enrollment and confirm no new policy command is accepted.
11. Re-enroll and confirm capability is reassessed before synchronization.

A real Android device/emulator was not available in this repository tool environment, so these manual tests are a documented plan, not claimed executions.
