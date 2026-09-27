# Phase 12.5 — Managed Android Website & Network Blocking Final Hardening

## Repository boundary

This phase changes only gaje9dra/parento-managed. Backend and Admin repositories are not modified.

## Platform capability audit

The app targets Android API 26+ and detects Device Owner, Profile Owner, unmanaged, and unknown management state through the existing DevicePolicyManager boundary.

The current application has no legitimate Android Enterprise API that universally blocks arbitrary hostnames across all applications and network transports. Therefore Phase 12.5 does not claim universal website filtering.

| Management state | Network-policy enforcement |
| --- | --- |
| Device Owner | UNSUPPORTED |
| Profile Owner | UNSUPPORTED |
| Unmanaged | UNSUPPORTED |
| Unknown / platform error | UNKNOWN |

No partial mechanism is claimed because no supported hostname-filtering mechanism is currently implemented. NetworkPolicyEnforcer remains the only place where a future supported mechanism could be introduced.

## Policy contract

- UUID policy/rule identifiers.
- Positive policy versions.
- ACTIVE or DISABLED status.
- ALLOW / BLOCK rules.
- Exact hostnames or explicit leading *.example.com wildcards.
- Normalized lowercase domains.
- Duplicate normalized domains rejected.
- Maximum 500 rules are accepted client-side, matching the backend Phase 12 policy limit.
- Wildcard matches subdomains but not the apex.
- Malformed URLs, paths, ports, embedded wildcards, invalid labels, and oversized hostnames rejected.

Backend validation remains authoritative.

## Version safety

The device never replaces a retained desired policy with an older server policy for the same policy identity. A different policy assignment may legitimately start at a lower version because versions are scoped to the policy ID.

For a SYNC_NETWORK_POLICY command, the requested policy ID/version must match the authoritative policy fetched through the authenticated device session. A mismatch is reported as STALE.

After device revocation, retained desired/applied policy state is cleared. When a new enrollment later synchronizes, the previous enrollment's version cannot block or authorize the new enrollment's policy.

## Disabled policy and cleanup

A disabled policy is reconciled as a removal operation rather than passed to the active-policy enforcer.

On revocation, the Managed app attempts removal of the previously applied Parento restriction and then clears Parento network-policy state. Removal is deterministic and idempotent at the abstraction boundary. If Android does not support removal, the result is reported as unsupported rather than success.

No unrelated Android or administrator network configuration is modified.

## Command security

SYNC_NETWORK_POLICY and REQUEST_NETWORK_POLICY_STATUS remain behind the existing Phase 6 command processor.

The processor validates authenticated managed-device identity, enrollment/session boundary, command type/version allowlist, JSON payload, expiration, and command replay state.

The network-policy handlers do not execute arbitrary command content.

## Offline, reconnect, and background execution

Network-policy reconciliation uses the existing authenticated transport and WorkManager architecture.

Foreground and connectivity events enqueue one unique network-policy reconciliation job rather than performing an immediate network request on every lifecycle/network event.

The worker requires an enrolled device, requires network connectivity, reuses the existing session connection path, fetches authoritative policy, reconciles versions, applies only supported enforcement, and reports resulting status/capability.

WorkManager unique work prevents duplicate queued reconciliation and existing exponential retry policy limits transient retries.

The latest valid desired state remains in Room while offline. No continuous polling loop or second network client is introduced.

## Process death and reboot

The desired/applied/enforcement/capability state remains in Room schema version 10.

Process death does not turn command receipt into enforcement success. On restart, persisted state is reconstructed and the next authenticated reconciliation uses the server as authoritative.

Reboot re-evaluates Android management state through DevicePolicyManager. Cached management state is not used as proof of Device Owner/Profile Owner.

## Revocation and re-enrollment

When enrollment becomes REVOKED: queued network-policy reconciliation is cancelled; Parento attempts cleanup through NetworkPolicyEnforcer; desired/applied policy IDs and versions are cleared; enforcement state becomes REVOKED; and new policy commands remain blocked by the existing authenticated command boundary.

After re-enrollment, a REVOKED policy state is treated as belonging to the old enrollment and is cleared before comparing a newly fetched policy version.

## Privacy

The implementation stores only policy/reconciliation metadata required for administration.

It does not collect browsing history, visited URLs, DNS query history, search queries, page contents, cookies, credentials, decrypted HTTPS traffic, packet captures, or unrelated network telemetry.

No VPN, proxy, certificate injection, Accessibility abuse, root, shell execution, hidden API, kernel modification, undocumented firewall hook, or browser injection is used.

## Partial enforcement

The domain model can represent PARTIALLY_APPLIED and explicit failure/unsupported states. The current platform adapter does not claim partial enforcement because it has no supported rule-level network mechanism to apply. If a future Android-supported mechanism can enforce only a subset, it must report applied, unsupported, and failed rule counts/reasons without converting unsupported rules into success.

## Manual verification plan

A physical Android Enterprise test device is required for real platform verification.

1. Provision Device Owner using supported Android Enterprise/test provisioning.
2. Confirm management mode is detected.
3. Confirm network capability is reported UNSUPPORTED, not falsely SUPPORTED.
4. Deliver a valid SYNC_NETWORK_POLICY.
5. Confirm policy retrieval and desired-state persistence.
6. Confirm enforcement remains UNSUPPORTED.
7. Deliver a stale policy command and verify STALE.
8. Deliver a duplicate command and verify existing command replay protection.
9. Disable a policy and verify removal is attempted without touching unrelated configuration.
10. Take the device offline and confirm the retained desired state remains unchanged.
11. Restore connectivity and verify WorkManager reconciliation.
12. Kill/restart the process and verify Room recovery.
13. Reboot and verify management state is re-detected.
14. Revoke enrollment and verify cleanup/state clearing.
15. Re-enroll and verify the old policy version cannot become authoritative.
16. Confirm no browsing or traffic history is generated.

These manual tests are a verification plan, not claimed executions, because a provisioned Android Enterprise device is not available in the repository tool environment.

## Platform reference

Android provides Device Owner/Profile Owner APIs for configuring always-on VPN, but hostname-level filtering would require an actual VPN enforcement service. This Phase 12.5 implementation does not introduce traffic interception or a VPN filter, so it reports hostname/network filtering as UNSUPPORTED rather than implying that DevicePolicyManager alone provides universal website blocking.

## Verification commands

The repository has no Gradle wrapper. Use the repository-compatible Gradle/Android Studio environment:

- gradle test
- gradle lint
- gradle connectedDebugAndroidTest
- gradle assembleDebug
- gradle assembleRelease

Do not treat a command as passed unless it has actually executed successfully.