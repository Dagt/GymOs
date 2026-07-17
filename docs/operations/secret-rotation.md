# Secret Rotation Runbook

## Secrets

- JWT signing secret.
- PostgreSQL password.
- Redis credentials when enabled.
- MinIO access and secret keys.
- SMTP credentials.
- External integration keys.

## Rotation Procedure

1. Generate the new secret outside the repository.
2. Update the environment secret store.
3. Restart affected services.
4. Validate login, refresh token behavior and object storage access.
5. Revoke the old secret.

## Notes

Rotating the JWT secret invalidates existing access and refresh tokens.
