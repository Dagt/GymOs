# Database Maintenance

Last reviewed: 2026-08-21. The current Flyway schema version is V77.

## Routine Tasks

- Monitor database size.
- Monitor slow queries.
- Review failed migrations.
- Vacuum and analyze according to PostgreSQL defaults and workload.
- Validate backup restore periodically.

## Migration Rules

- Never edit applied migrations.
- New migrations must start after the current latest version.
- Every migration must be PostgreSQL-compatible.
- Tenant-scoped business tables require `organization_id` and usually `branch_id`.
- Payment, webhook, automation and notification records must retain their idempotency and audit identifiers during maintenance.

## Pre-Pilot Checklist

1. Latest migrations applied.
2. Backup completed.
3. Restore tested.
4. Demo seed disabled outside local.
5. Smoke tests completed.
