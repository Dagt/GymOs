# Infraestructura local

Arranque local:

```powershell
docker compose -f infra/docker-compose.yml up --build
```

Para probar Stripe, copia `.env.example` a `.env`, agrega una llave `sk_test_...`
y descarga/inicia el listener incluido en el perfil `stripe`:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml --profile stripe pull stripe-listener
docker compose --env-file infra/.env -f infra/docker-compose.yml --profile stripe up stripe-listener
```

El listener reenvÃ­a Ãºnicamente los eventos soportados de cuentas Connect al
backend. En su primer arranque imprime un secreto `whsec_...`: cÃ³pialo a
`GYMOS_STRIPE_WEBHOOK_SECRET` en `infra/.env` y recrea el backend para que la
validaciÃ³n de firmas use el mismo secreto:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --force-recreate backend
```

Las llaves de Stripe no deben incluirse en imÃ¡genes, commits ni variables
`NEXT_PUBLIC_*`. El perfil es opcional y no se levanta en el arranque normal.

Servicios:

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- OpenAPI UI: http://localhost:8080/api/v1/docs
- Health: http://localhost:8080/actuator/health
- MinIO Console: http://localhost:9001
- Stripe CLI: perfil `stripe` (salida visible en terminal)

Credenciales locales no productivas:

- PostgreSQL: `gymos` / `gymos`
- MinIO: `gymos` / `gymos-secret`
