# Release Process

## Versioning

GymOS uses semantic versioning.

- `MAJOR`: incompatible product or data-contract changes.
- `MINOR`: new backwards-compatible functionality.
- `PATCH`: bug fixes and safe operational updates.

## Release Checklist

1. Confirm CI is green.
2. Confirm backend tests pass.
3. Confirm frontend lint, typecheck and build pass.
4. Confirm Docker Compose config and build pass.
5. Tag the release commit.
6. Publish release notes from `CHANGELOG.md`.
7. Record smoke test evidence.

## Pilot Tags

Pilot releases use the format:

```text
v0.x.y-pilot.n
```
