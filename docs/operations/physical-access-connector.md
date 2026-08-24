# GymFort Local Connector contract

Last reviewed: 2026-08-23.

This document is the implementation handoff for the local process that will
bridge GymFort Cloud with a physical-access provider. The cloud contract is
implemented. No HikCentral SDK/API call is implemented or assumed here.

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
  `GYMOS_CONNECTOR_AUTH_FAILURE_WINDOW`.

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

## 6. Person discovery

To help an operator link existing provider persons:

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

## 8. HikCentral discovery gate

Before writing the adapter, record from the actual client installation:

- HikCentral Professional edition/version and active licenses;
- terminal/controller model and firmware;
- official OpenAPI/Open Platform/SDK availability;
- supported authentication and LAN endpoint/port;
- stable identifiers for persons, doors and access levels;
- create/update person and access-level assignment semantics;
- remote-open and event-query/subscription support;
- rate limits, pagination, time zones and event retention;
- provider behavior during partial failures.

Do not infer endpoints from another HikCentral version or from unofficial
examples.

## 9. Site acceptance gate

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
