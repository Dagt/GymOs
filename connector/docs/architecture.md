# GymFort Local Connector architecture

## Responsibility split

```text
GymFort Cloud                         Gym LAN
-------------------------------      ---------------------------------
commercial truth                     local provider credentials
eligibility and desired rights  -->  GymFort Local Connector
durable jobs and audit               durable execution ledger (SQLite)
tenant/branch isolation              provider-neutral driver
                                      |
                                      +--> HikCentral OpenAPI
```

The Connector never recalculates membership, grace periods, branch coverage or
package balances. It receives an exact set of provider Access Level references
that GymFort owns and reconciles that set locally.

## Provider boundary

`AccessProviderDriver` is the only provider-specific boundary used by the
worker. A driver exposes health/capabilities and accepts an idempotent
`ProviderSyncCommand`. Adding another provider must not add brand conditionals
to the job worker or copy GymFort's business rules.

The HikCentral driver treats `hikcentral.managedAccessLevelRefs` as an ownership
boundary:

- desired references outside it are rejected;
- missing desired references are assigned;
- stale managed references are removed;
- unrelated/manual HikCentral privileges are never removed.

## Delivery guarantees

The Cloud claim token prevents an old worker from completing a recovered lease.
Before a provider call, the Connector inserts the cloud idempotency key in
SQLite. After provider success it persists `SUCCEEDED` before completing Cloud.
If Cloud completion fails, a later claim completes from the local result and
does not call the provider again.

If the process dies after provider mutation but before local success is
persisted, the command may be replayed. Drivers therefore reconcile exact state
rather than performing blind additive mutations.

## Secrets and transport

- Cloud and HikCentral secrets never belong in the properties file.
- Windows persistence uses user-scoped DPAPI; environment variables are the
  non-Windows/runtime-injection path.
- Provider and Cloud TLS verification stays enabled.
- A site-local X.509 certificate can be added as explicit trust through
  `hikcentral.trustedCertificatePath`; system roots and hostname verification
  remain active.
- Logs and health details are sanitized and must never carry biometric data.

## Deliberate V1 limits

- An existing HikCentral person must be linked before a rights job can run.
- Candidate import, automatic person creation and assisted face enrollment are
  separate next increments.
- Provider event ingestion and remote opening are not yet implemented.
- Static Access Levels are suitable for long-lived membership rights. Metered
  visit packages require an authorization round trip that consumes GymFort's
  balance before one physical opening; permanent assignment would be unsafe.
- No live HikCentral release or terminal is certified until the site matrix is
  executed.
