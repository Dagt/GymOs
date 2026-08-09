# Kiosk Troubleshooting

## Common Symptoms

- Terminal cannot start a session.
- Verification always fails.
- Session token rejected.
- Session expired.

## Checks

1. Confirm the branch kiosk profile and its capabilities are active in Organization settings.
2. Confirm the reception account selected the expected organization and branch, opened `/kiosk/launch`, and allowed the new tab. No computer ID is registered.
3. Confirm the kiosk arrived at `/kiosk/reception`. Opening that route directly is intentionally rejected because it lacks the one-time browser handoff.
4. Confirm local clock and backend clock are aligned.
5. Confirm `X-Self-Service-Session-Token` is sent after verification.
6. Check rate limiting and blocked-until state.

For wellness, only active services with `price_cents = 0` are returned. A paid
service is rejected by the backend even if a client attempts to submit its ID.

## Data Safety

Do not expose whether a specific email or phone exists. Kiosk responses must remain generic.

## Recurring Payments

Use the tenant-scoped histories in Organization settings before inspecting the
database. They list mandates, recurring charges and webhook outcomes without
provider payloads or full payment-method identifiers.

Operational metrics are available to authenticated monitoring clients through
`/actuator/metrics` and `/actuator/prometheus`:

- `gymos.payments.recurring.worker`;
- `gymos.payments.recurring.charges`;
- `gymos.payments.webhooks`;
- `gymos.payments.reconciliation`;
- `gymos.automation.executions` and `gymos.automation.execution.duration`.
- `gymos.automation.backlog`, `gymos.automation.expired_leases`,
  `gymos.payment.operations` and `gymos.recurring.expired_leases`.

Every series includes `organization_id`. Provider, event type, trigger type and
outcome are bounded tags; client, sale, charge, webhook and PaymentIntent IDs
must never become metric tags.

## Infrastructure Verification

Run PostgreSQL concurrency and lease recovery tests on a machine with Docker:

```powershell
cd backend
.\gradlew.bat postgresIntegrationTest
```

Run the live sandbox contract only with a Stripe test key. A connected test
account is optional for platform-account tests and required to reproduce the
production Connect topology:

```powershell
$env:GYMOS_STRIPE_TEST_SECRET_KEY = "sk_test_..."
$env:GYMOS_STRIPE_TEST_ACCOUNT_ID = "acct_..."
cd backend
.\gradlew.bat stripeTest
```

The Stripe suite rejects live keys by construction and is skipped when the test
secret is absent. It covers idempotent replay, an interruption between create
and confirm, successful off-session confirmation, `requires_action` and an
insufficient-funds decline. The regular unit suite separately verifies signed
webhook deduplication and provider timeout mapping.
