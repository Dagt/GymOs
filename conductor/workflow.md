# Project Workflow

Last reviewed: 2026-08-21.

## Sources of truth

For current behavior, inspect in this order:

1. Executable code and database migrations.
2. Automated tests and API contracts.
3. Current module and operations documentation.
4. Historical Conductor tracks and branch chronology.

A historical `plan.md` records how a completed track was delivered; it does not override current code or architecture.

## Branch and change workflow

1. Work on the branch agreed for the active objective.
2. Inspect existing changes and preserve unrelated user work.
3. Identify the owning module, invariants, permission boundary and tenant/branch scope.
4. Add or update tests in proportion to risk before or with the implementation.
5. Implement the smallest coherent change without bypassing module APIs.
6. Run relevant backend, frontend, architecture, integration and browser checks.
7. Update module, contract, operations and release documentation affected by the change.
8. Review the diff, ensure no secrets are present, commit, push and use a pull request for integration to `main`.

## Quality gates

- Backend: `./gradlew.bat test` plus relevant integration tasks such as `postgresIntegrationTest` or opt-in `stripeTest`.
- Frontend: `npm.cmd test`, `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd run build` and targeted Playwright suites.
- Infrastructure: `docker compose config`; validate optional profiles and pinned third-party images when changed.
- Database: never edit an applied Flyway migration; add a new migration after the latest version.
- Security: verify organization, branch and permission boundaries and avoid secrets or sensitive payment data in code and logs.
- Payments: test idempotency, signed webhooks and authoritative provider lookup; browser redirects are presentation only.
- UX: include loading, empty, success and error states and use the global floating notification system.
- Documentation: update the current-state index, affected module pages and operational runbooks.

## Commit format

```text
<type>(<optional-scope>): <description>
```

Common types are `feat`, `fix`, `docs`, `test`, `refactor` and `chore`.

## Definition of done

A change is done when its behavior and authorization are implemented, relevant automated checks pass, migrations and operational impacts are documented, no unrelated work is included, and the branch is available remotely for review or testing.

## Incident changes

For a critical defect:

1. Preserve evidence and identify user impact.
2. Add a regression test when feasible.
3. Prefer a minimal roll-forward fix; follow the rollback and restore runbooks when required.
4. Revalidate the affected critical path and document the incident and correction.
