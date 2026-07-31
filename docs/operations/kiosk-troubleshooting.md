# Kiosk Troubleshooting

## Common Symptoms

- Terminal cannot start a session.
- Verification always fails.
- Session token rejected.
- Session expired.
- The kiosk shows `GymOS Demo` or another organization.
- The expected logo and brand colors are not displayed.

## Checks

1. Confirm terminal status is active.
2. Confirm terminal belongs to the expected organization and branch.
3. Confirm local clock and backend clock are aligned.
4. Confirm `X-Self-Service-Session-Token` is sent after verification.
5. Check rate limiting and blocked-until state.
6. When opened from reception, confirm that the request to create the session
   sends `X-GymOS-Organization-Id` and `X-GymOS-Branch-Id` for the active
   reception context.
7. Confirm the resolved terminal returns the expected `organizationName`,
   `brandName`, `logoUrl` and colors before attempting member verification.

## The kiosk shows another organization

### Symptoms

- the header displays `GymOS Demo` or a different gym;
- a valid member cannot be verified;
- branding, logo or colors do not match reception;
- the API returns `SELF_SERVICE_VERIFICATION_FAILED` even when contact and date
  of birth are correct.

### Cause

The kiosk session was created with a terminal belonging to another tenant.
During the temporary implementation, reception opens `/kiosk/KIOSK-MAIN`.
`KIOSK-MAIN` is not globally unique and must exist independently inside every
organization.

A kiosk session stores the organization and branch of the resolved terminal.
All subsequent member lookup is isolated by that stored context. If the demo
terminal is resolved, a real member from another organization cannot match.

### Immediate correction

Migration `V61__provision_default_kiosk_per_organization.sql` provisions a
`KIOSK-MAIN` terminal for each organization that does not already have one.
Restart the backend and confirm Flyway applied V61 before repeating the test.

For organizations with several branches, V61 associates the temporary terminal
to the first active branch ordered by `created_at` and `id`. This is only a test
bridge. It is not the final multi-branch configuration.

### Verification queries

```sql
SELECT
    sst.id,
    sst.device_code,
    sst.organization_id,
    sst.branch_id,
    sst.name,
    sst.status,
    o.trade_name,
    o.brand_name,
    o.logo_url,
    b.name AS branch_name
FROM self_service_terminal sst
JOIN organization o ON o.id = sst.organization_id
JOIN branch b ON b.id = sst.branch_id
WHERE sst.device_code = 'KIOSK-MAIN'
  AND sst.deleted_at IS NULL
ORDER BY o.trade_name, b.name;
```

Check the most recent sessions:

```sql
SELECT
    s.id,
    s.organization_id,
    s.branch_id,
    s.terminal_id,
    s.status,
    s.started_at,
    s.expires_at,
    t.device_code,
    t.name AS terminal_name,
    o.trade_name,
    b.name AS branch_name
FROM self_service_session s
JOIN self_service_terminal t ON t.id = s.terminal_id
JOIN organization o ON o.id = s.organization_id
JOIN branch b ON b.id = s.branch_id
ORDER BY s.started_at DESC
LIMIT 20;
```

Do not continue testing member verification while the kiosk header identifies a
different organization.

## Temporary terminal identity

`KIOSK-MAIN` is a temporary code per organization. It is not a globally unique
public identifier and it is not a device credential.

The definitive design must support an independent terminal per branch and per
physical installation, with a globally unique public URL and a provisioned,
rotatable device credential. See
`Documentación/32-transicion-identidad-terminales-kiosco.md`.

## Data Safety

Do not expose whether a specific email or phone exists. Kiosk responses must remain generic.
