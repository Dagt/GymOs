# Incident Response Runbook

## Severity

- Sev 1: system unavailable, data loss, payment workflow blocked.
- Sev 2: core workflow degraded, workaround exists.
- Sev 3: non-critical module issue.

## First Response

1. Record time, environment, module and user impact.
2. Check backend health, frontend availability, PostgreSQL, Redis and MinIO.
3. Capture correlation IDs from user-visible errors.
4. Check recent deployment or migration changes.
5. Decide rollback or roll-forward.

## Communication

For the pilot, notify the gym owner or manager with impact, workaround and next update time.

## Evidence

Preserve logs, screenshots, request IDs and database migration state.
