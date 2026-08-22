# Restore Runbook

Last reviewed: 2026-08-21.

## Preconditions

- Confirm the target environment.
- Stop application writes when restoring production or staging.
- Keep the original backup immutable.

## Restore PostgreSQL

```powershell
./scripts/restore-postgres.ps1 -BackupFile backups/gymos-YYYYMMDD-HHMMSS.dump
```

## Post-Restore Checks

1. Start backend.
2. Confirm Flyway state.
3. Check `/actuator/health`.
4. Login with a non-demo user in staging or production.
5. Validate clients, sales, payments, memberships and reports.
6. Reconcile pending payment operations and notifications against their provider state before replaying work.

## Failure

If restore fails, preserve logs, do not retry against production without a new snapshot, and escalate to the technical owner.
