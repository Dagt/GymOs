# Physical Access Troubleshooting

Last reviewed: 2026-08-23.

Status: GymFort Cloud control plane is implemented. The Local Connector,
HikCentral adapter and real turnstile acceptance are not implemented yet.

## Logical decision checks

1. Confirm the client is active.
2. Confirm the membership is active or inside its grace period for the current
   GymFort business date.
3. Confirm the current plan grants the required `ACCESS` benefit.
4. Confirm the device is active, belongs to the current organization/branch and
   requires exactly one benefit.
5. For a multi-branch plan, confirm that the target branch belongs to the same
   organization and is eligible.
6. If using a temporary authorization, confirm status, expiration and remaining
   uses. Consumption is conditional and atomic.
7. Review `access_log` and the correlated audit event.

The Physical configuration view lists every point with one of these explicit
states:

- `Correcto — 1/1 beneficio ACCESS`;
- `Configuración incompleta — 0/1 beneficios ACCESS`;
- `Configuración inválida — N/1 beneficios ACCESS`.

For an invalid legacy point, select the affected point and choose the single
benefit that should remain. GymFort does not auto-fix or choose the first legacy
benefit. `SIMULATED` is shown separately as diagnostic and is never evidence of
physical production readiness.

## Connector and synchronization checks

Use the Physical configuration view as Owner or Manager:

1. Confirm the branch has one enabled connection and the expected provider.
2. Confirm every productive point is linked to the correct external resource.
3. Confirm every effective `ACCESS` benefit has an external access-level
   mapping.
4. Confirm the client has an opaque external-person link.
5. Read the desired state:
   - `BLOCKED` means a cloud prerequisite is missing;
   - `PENDING` means a durable job awaits delivery;
   - `FAILED` means the Connector reported a terminal failure;
   - `SYNCED` means the latest desired version was confirmed.
6. Check credential status, heartbeat, Connector version, pending/failed jobs and
   last sanitized error.
7. If heartbeat is older than five minutes, treat the Connector as offline and
   restore the local service/network first.
8. After recovery, run a full reconciliation and verify that the queues drain.

Do not manually change membership data to clear a delivery error. Commercial
truth and physical delivery are intentionally separate.

## Credential incident

1. Revoke the Connector credential from GymFort immediately.
2. Stop the local service and inspect its secure secret store; never paste the
   secret into logs, tickets or chat.
3. Generate a rotation code. It expires after 15 minutes and is shown once.
4. Activate the new credential locally and verify heartbeat.
5. Run reconciliation and review authentication-rejection audit events.

Repeated invalid attempts are temporarily throttled. They continue returning
`CONNECTOR_AUTH_INVALID` whether the key is unknown, wrong or revoked. Stop
automatic retries, correct/rotate the credential and wait for the configured
failure window; do not try multiple connector keys. Production throttling is
shared through Redis, while local/test counters are only per process.

## Privacy boundary

GymFort Cloud must not receive provider passwords, LAN URLs, photos, facial or
fingerprint templates, biometric matching payloads, API tokens or other secrets.
Connector event metadata containing sensitive field names is rejected.

## Manual opening and current hardware status

Manual open requires `access.manual.open`, an explicit device and a reason. It
currently records `SIMULATED_OPEN`; it does not open a real device. Do not mark a
site ready for physical production until the installation-specific Connector and
HikCentral adapter pass grant, revoke, reconciliation, duplicate-event and
disconnect/recovery acceptance tests against the actual turnstile.
