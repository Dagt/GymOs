# Backup Runbook

## Objective

Protect PostgreSQL data before pilot operation, imports and releases.

## Targets

- Database: PostgreSQL.
- Object storage: MinIO bucket `gymos-receipts`.
- Configuration: environment variable set and reverse proxy configuration.

## RPO and RTO

- Pilot RPO: 24 hours.
- Pilot RTO: 4 hours.

## PostgreSQL Backup

Use:

```powershell
./scripts/backup-postgres.ps1
```

The script creates a compressed custom-format dump under `backups/` and removes files older than the configured retention window.

## Retention

- Local pilot: 14 daily backups.
- Production target: 30 daily backups plus weekly snapshots.

## Validation

At least once before pilot launch, restore the latest backup into a clean database and run smoke tests.
