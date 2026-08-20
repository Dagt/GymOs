# Stripe and Automation Operations

## Tenant-scoped diagnosis

Start in Organization settings. The payment operations card reports past-due
mandates, retrying or failed charges, failed webhooks and provider operations
that have remained non-terminal for more than 15 minutes. The Automations page
reports pending events, retries, expired leases and dead letters for the current
branch. Every metric carries `organization_id`; automation backlog metrics also
carry `branch_id`.

Do not paste provider payloads, card data, email addresses or Stripe secrets in
logs, alerts or tickets.

## Recovery after an outage

1. Restore PostgreSQL, network and Stripe connectivity before changing records.
2. Check `/actuator/health` and Prometheus alerts.
3. Open the payment and automation operation cards for the affected tenant.
4. Leave leased work alone until its five-minute lease expires. Workers reclaim
   it using the same idempotency identity.
5. Use the dead-letter retry action only after the underlying cause is fixed;
   provide an auditable reason.
6. Verify that a completed Stripe operation produced exactly one completed
   payment and one membership period. Never extend a membership manually just
   because a Checkout return page says success.

## Emergency scheduler pause

An owner or GymOS administrator with `automation.scheduler.manage` can pause all
scheduled rules and domain-event consumption for one tenant from the
Automations page. Other tenants keep running. Record the incident reason and
resume processing after recovery. Pausing does not delete, unlock or discard
queued work.

## Stripe disconnect

1. Disable Stripe self checkout and recurring billing in Organization settings.
2. Confirm there are no provider operations in a non-terminal state and no
   recurring charges in `PROCESSING` or `AWAITING_PROVIDER`.
3. Export the tenant-scoped mandate, charge and webhook histories for the
   incident record if required.
4. Revoke the connected account or platform access in Stripe only after the
   application is disabled. GymOS retains masked mandate history and audit
   records; it never retains full card data.
5. Do not delete active mandates in SQL. Revoke them through the supported flow
   or complete a controlled data-migration procedure.

## Stripe secret rotation

1. Create a restricted test replacement and validate Checkout, Setup, webhook
   signature verification and an off-session charge in the test environment.
2. Update `GYMOS_STRIPE_SECRET_KEY` in the secret manager and restart the
   backend. Keep the old key active during validation.
3. Roll the webhook endpoint secret separately through
   `GYMOS_STRIPE_WEBHOOK_SECRET`. Stripe may deliver with both endpoint secrets
   during a controlled overlap; deploy the new value before revoking the old
   endpoint secret.
4. Verify tenant metrics and duplicate-webhook idempotency, then revoke the old
   key.

## Browser and proxy deployment

Set `GYMOS_TRUSTED_PROXIES` to the comma-separated CIDRs of load balancers that
connect directly to the backend. When it is empty, GymOS ignores `Forwarded`
and `X-Forwarded-For`. Do not add public or client networks: kiosk rate limits
must use the first untrusted hop found while walking the trusted proxy chain.

The kiosk remains a browser session launched by reception. Its logical
capabilities are configured per branch; there is no computer or terminal ID to
enrol.
