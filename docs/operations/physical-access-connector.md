# GymFort Local Connector contract

Last reviewed: 2026-08-29.

This document is the implementation and operations contract for the local
process that bridges GymFort Cloud with a physical-access provider. The cloud
contract and the first executable under `connector/` are implemented. The
HikCentral adapter uses the official OpenAPI AK/SK contract for version
discovery and exact Access Level assignment. It is not certified against the
client's live installation yet.

## 1. Trust boundary

The Connector runs in the gym LAN and owns:

- provider URL, port and credentials;
- provider-specific SDK/API payloads;
- local retry for provider calls;
- installation and service lifecycle;
- biometric/provider data, if the provider requires it.

GymFort Cloud owns membership truth, desired access levels, job durability,
idempotency and operator visibility. The Connector must never implement its own
membership rules.

## 2. Activation

An Owner or Manager configures the branch and asks GymFort for a one-time code.
Within 15 minutes, call:

```http
POST /api/v1/physical-connectors/activate
Content-Type: application/json

{
  "connectorKey": "access-centro-01",
  "activationCode": "gfa_<one-time-value>"
}
```

The response includes `connectorSecret` once. Store it in the operating-system
secret store with permissions limited to the service account. Do not write it to
stdout, config files, telemetry or crash dumps.

All subsequent calls send:

```http
X-GymFort-Connector-Key: access-centro-01
X-GymFort-Connector-Secret: gfs_<secret>
```

Invalid, expired or revoked credentials return HTTP 401 with
`CONNECTOR_AUTH_INVALID`. Do not retry an invalid credential indefinitely; stop
provider mutation, surface a local alert and wait for operator rotation.

Cloud throttles repeated activation and authentication failures by source and
by source/connector-key pair. Limited attempts intentionally return the same
`CONNECTOR_AUTH_INVALID`, so the response cannot be used to discover whether a
key exists. Apply exponential backoff with jitter after any 401; a correct
Connector running from a different source is not locked by another source's
failures, and every bucket expires automatically.

Production counters use GymFort's shared Redis rate limiter. `local` and `test`
profiles use an in-process limiter and must not be described as distributed.
Only SHA-256 fingerprints enter limiter keys; the Connector secret and
activation code never do. Operators can tune:

- `GYMOS_CONNECTOR_ACTIVATION_MAX_FAILURES` and
  `GYMOS_CONNECTOR_ACTIVATION_FAILURE_WINDOW`;
- `GYMOS_CONNECTOR_AUTH_MAX_FAILURES` and
  `GYMOS_CONNECTOR_AUTH_FAILURE_WINDOW`;
- `GYMOS_CONNECTOR_ONLINE_MAX_EVENT_AGE` (two minutes by default), which
  prevents delayed provider history from actuating a live door.

Cloud validates the secret on every call. `last_authenticated_at` is an
operational "recently authenticated" marker rather than a per-request ledger:
its PostgreSQL row is updated only after
`GYMOS_CONNECTOR_LAST_AUTH_WRITE_INTERVAL` (five minutes by default), preventing
heartbeat/job polling from incrementing the credential version on every call.

## 3. Heartbeat

Send at least once per minute:

```http
POST /api/v1/physical-connectors/heartbeat

{
  "status": "HEALTHY",
  "connectorVersion": "1.0.0",
  "instanceId": "windows-service-centro",
  "lastError": null
}
```

Use `DEGRADED` and a sanitized `lastError` when the Connector is alive but the
provider is unavailable. GymFort derives `OFFLINE` after five minutes without a
heartbeat.

## 4. Durable job loop

Claim work:

```http
POST /api/v1/physical-connectors/jobs/claim

{ "limit": 20, "leaseSeconds": 90 }
```

Each job contains:

- `id` and `idempotencyKey`;
- `action`: `UPSERT`, `REVOKE` or `RECONCILE`;
- `externalPersonRef`;
- the complete `desiredAccessLevelRefs` set;
- `attempts`, `leaseUntil` and a per-claim `claimToken`.

Processing rules:

1. Persist the idempotency key locally before mutating the provider. Keep the
   `claimToken` only with that active attempt; a reclaim returns a new token.
2. Treat desired levels as an exact GymFort-owned set, not an additive command.
3. `UPSERT` creates/updates the provider person assignment.
4. `REVOKE` removes all levels managed by GymFort for that person.
5. `RECONCILE` reads provider state and repairs it to the desired set.
6. Do not remove provider levels that are explicitly outside GymFort ownership;
   define that namespace during site discovery.
7. Extend a lease before it expires if a provider operation is still running:

```http
POST /api/v1/physical-connectors/jobs/{jobId}/lease

{ "claimToken": "<token returned by claim>", "leaseSeconds": 90 }
```

8. Confirm success:

```http
POST /api/v1/physical-connectors/jobs/{jobId}/complete

{
  "claimToken": "<token returned by claim>",
  "externalOperationRef": "provider-operation-id"
}
```

9. Report a safe failure:

```http
POST /api/v1/physical-connectors/jobs/{jobId}/fail

{
  "claimToken": "<token returned by claim>",
  "error": "Provider timed out while assigning access levels",
  "retryable": true,
  "retryAfterSeconds": 30
}
```

Completion is idempotent. A reclaimed lease rotates `claimToken`, so a late
worker cannot complete, fail or extend the new attempt. Jobs for the same person
are serialized; a newer desired version waits for the active attempt and then
supersedes stale pending/retry jobs.

## 5. Physical events

After the provider adapter can read authoritative events, publish:

```http
POST /api/v1/physical-connectors/events

{
  "providerKey": "HIKCENTRAL",
  "externalEventId": "unique-provider-event-id",
  "externalPersonRef": "opaque-person-ref",
  "externalDeviceRef": "opaque-door-ref",
  "occurredAt": "2026-08-23T12:30:00-06:00",
  "decision": "GRANTED",
  "eventType": "ACCESS_ATTEMPT",
  "reasonCode": "PROVIDER_GRANTED",
  "metadata": { "readerMode": "LOCAL" }
}
```

The device must already be linked to the authenticated connection. Retrying the
same `externalEventId` is safe and does not duplicate `access_log`.

Never send photos, images, face/fingerprint templates, biometric match data,
passwords, tokens, provider credentials or LAN addresses. Cloud rejects
sensitive metadata keys and diagnostic values that look like a URL, IP address
or credential. Reusing an `externalEventId` returns the original event result
and performs no secondary update, even if a retry payload differs.

## 6. Person discovery contract (not yet consumed by the local adapter)

A future candidate-import loop can help an operator link existing provider
persons through:

```http
POST /api/v1/physical-connectors/person-candidates

{
  "externalPersonRef": "opaque-person-ref",
  "externalDisplayName": "Display name",
  "email": "normalized-contact@example.test",
  "phone": "+525500000000",
  "externalEmployeeNumber": null
}
```

GymFort may suggest one exact email/phone match, report an ambiguity or leave it
unmatched. It never links by fuzzy name. A privileged human confirms the match.

## 7. Local implementation requirements

- Run as a least-privilege Windows/Linux service, not as an interactive admin.
- Use TLS and validate GymFort Cloud certificates.
- Encrypt local provider configuration and Connector secret at rest.
- Apply bounded exponential backoff with jitter for Cloud/provider outages.
- Keep a small local idempotency ledger and prune only after a documented
  retention window.
- Sanitize logs and cap their retention/size.
- Expose local health without exposing secrets.
- Package signed/versioned updates and a rollback procedure.
- Preserve the provider's local decision capability during Internet outages.
- Never open a door merely because Cloud is unreachable.

## 8. HikCentral compatibility and discovery gate

The adapter targets the stable HikCentral OpenAPI boundary instead of a
terminal model or a hard-coded HikCentral release. The OpenAPI package installed
at the site still has to be compatible with that HikCentral Professional
release. Before enabling provider mutation, `doctor` must verify the live
version, credentials and every Access Level owned by GymFort.

Record from the actual client installation:

- HikCentral Professional edition/version and active licenses;
- terminal/controller model and firmware;
- official OpenAPI/Open Platform/SDK availability;
- supported authentication and LAN endpoint/port;
- stable identifiers for persons, doors and access levels;
- create/update person and access-level assignment semantics;
- remote-open and event-query/subscription support;
- rate limits, pagination, time zones and event retention;
- provider behavior during partial failures.

Do not disable TLS verification to work around the site's local certificate.
Configure the site's exported public certificate through
`hikcentral.trustedCertificatePath`; system trust and hostname verification
remain enabled.
Never use an OpenAPI package copied from an unrelated HikCentral release.

## 9. Face enrollment and GymFort identity link

Face templates remain inside HikCentral and its terminals. GymFort stores only
the opaque HikCentral person identifier. The safe enrollment flow is:

1. the receptionist creates or locates the person in HikCentral and captures
   the face with the site's supported enrollment terminal;
2. the Connector imports the opaque person as a candidate; exact email/phone
   matches may be suggested but are never accepted automatically;
3. an Owner or Manager confirms the candidate against the exact GymFort client
   (manual paste remains available as a fallback);
4. linking stores `externalPersonRef` and enqueues the complete desired Access
   Level set, regardless of the branch where the membership was sold;
5. the Connector reconciles only the Access Levels declared as GymFort-owned.

Automatic person creation and assisted facial capture remain later contract
increments. A job without `externalPersonRef` is blocked intentionally; the
Connector never guesses identity by a fuzzy name. Face templates remain local.

Long-lived membership rights can be distributed as Access Levels. A metered
visit-package access cannot be represented as a permanent HikCentral Access
Level because that would bypass GymFort's atomic visit consumption. It uses
`GYMFORT_ONLINE`: recognition event → idempotent GymFort eligibility and
consumption → one audited opening command. This path is automated with the
simulator; until the matched HikCentral profile and real hardware pass the site
matrix, visit packages must not be advertised as physically enforced there.
Benefits used exclusively by online devices are excluded from autonomous Access
Level synchronization, and one benefit cannot mix `PROVIDER_MANAGED` with
`GYMFORT_ONLINE` inside the same connection. The HikCentral person can remain
enrolled for recognition, but no local Access Level may open that online door.
Online events also have a configurable maximum age (two minutes by default).
Historical events and pending authorizations that expire never actuate a door
or consume a visit; a confirmed successful opening cannot later be downgraded
by a contradictory acknowledgement.

## 10. Site acceptance gate

Do not label the integration productive until the real site passes:

1. activation, rotation and immediate revocation;
2. heartbeat/offline/degraded transitions;
3. new member grant and renewal without duplicate provider records;
4. plan change with grant and revoke;
5. expiration, inactive client and full revoke;
6. missing mapping/person/device blocked safely;
7. concurrent claim, lease expiry and Connector restart;
8. duplicate jobs and events;
9. Internet, Cloud, Connector and HikCentral outage/recovery;
10. correct branch/turnstile isolation;
11. privacy/log review with no credentials or biometric material;
12. operator runbook rehearsal and client sign-off.
