# Incident Response Runbook

Last reviewed: 2026-08-21.

## Severity

- Sev 1: system unavailable, data loss, payment workflow blocked.
- Sev 2: core workflow degraded, workaround exists.
- Sev 3: non-critical module issue.

## First Response

1. Record time, environment, module and user impact.
2. Check backend health, frontend availability, PostgreSQL, Redis and MinIO.
3. Capture correlation IDs from user-visible errors.
4. Check recent deployment or migration changes.
5. For payment incidents, correlate the local operation with the Stripe event, Checkout or Setup Session and signed-webhook delivery. A browser redirect is not proof of success.
6. For email incidents, inspect the durable notification record, outbox attempt count and sanitized SMTP error before retrying.
7. Decide rollback or roll-forward.

## Payment-specific triage

1. Confirm that the backend has a valid Stripe secret and webhook signing secret for the same test or live environment.
2. Check `/actuator/health`, the `stripe` CLI container or deployed webhook endpoint, and the provider event log.
3. Search backend logs by correlation ID and Stripe event or object ID; never paste full secrets or card data into an incident record.
4. Verify idempotency before replaying a webhook or asking the user to retry. Repeated pending rows do not imply repeated settled charges.
5. Preserve the payment operation and audit evidence even when the user session has expired.

## Email-specific triage

1. Determine whether the automation produced a durable notification.
2. Distinguish template/rendering failure, SMTP authentication failure and provider rejection.
3. Confirm the configured sender, host, port and TLS mode without exposing credentials.
4. Retry only through the supported durable delivery path.

## Communication

For the pilot, notify the gym owner or manager with impact, workaround and next update time.

## Evidence

Preserve logs, screenshots, correlation IDs, provider object/event IDs, durable job or notification IDs and database migration state.
