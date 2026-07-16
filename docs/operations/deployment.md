# Deployment Runbook

## Scope

This runbook describes the minimum deployment path for a GymOS pilot environment.

## Environments

- `local`: developer machine, demo seed allowed.
- `test`: automated tests, seed disabled.
- `development`: shared non-production validation.
- `staging`: production-like pilot rehearsal.
- `production`: real gym data, seed disabled.

## Required Configuration

Set every value through environment variables. Do not use demo credentials outside `local`.

- `SPRING_PROFILES_ACTIVE`
- `GYMOS_DB_URL`
- `GYMOS_DB_USERNAME`
- `GYMOS_DB_PASSWORD`
- `GYMOS_REDIS_HOST`
- `GYMOS_REDIS_PORT`
- `GYMOS_MINIO_ENDPOINT`
- `GYMOS_MINIO_ACCESS_KEY`
- `GYMOS_MINIO_SECRET_KEY`
- `GYMOS_MINIO_BUCKET`
- `GYMOS_JWT_SECRET`
- `GYMOS_CORS_ALLOWED_ORIGINS`
- `GYMOS_SEED_ENABLED=false`
- `NEXT_PUBLIC_API_BASE_URL`

## Deployment Steps

1. Build backend and frontend images from a tagged commit.
2. Apply database migrations automatically at backend startup.
3. Start PostgreSQL, Redis, object storage, backend and frontend.
4. Check `/actuator/health` and `/api/v1/system/info`.
5. Run smoke tests for login, branch selection, client creation, sale, payment and reporting.
6. Confirm `GYMOS_SEED_ENABLED=false` in staging and production.

## Roll Forward

Prefer forward fixes with a new tagged image and migration. Do not edit applied migrations.
