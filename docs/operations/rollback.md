# Rollback Runbook

Last reviewed: 2026-08-21.

## Rules

- Do not modify applied migrations.
- Prefer deploying the previous known-good image if no irreversible migration was introduced.
- If a migration is incompatible, roll forward with a compensating migration.

## Steps

1. Identify last known-good tag.
2. Stop frontend and backend.
3. Deploy previous images.
4. Keep database unchanged unless a restore was explicitly approved.
5. Run smoke tests.
6. Verify that signed payment webhooks remain reachable and that no provider event is applied twice.
7. Document the incident and corrective action.
