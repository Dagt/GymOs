# GymOS

GymOS es una plataforma modular para la operacion integral de gimnasios y centros de bienestar.

## Estructura

- `backend/`: API Java 25 con Spring Boot, Spring Modulith, Flyway, PostgreSQL, Redis y MinIO.
- `frontend/`: aplicacion web Next.js, React, TypeScript y Tailwind CSS.
- `infra/`: Docker Compose local, reverse proxy example y observabilidad base.
- `Documentación/`: documentacion maestra del producto y arquitectura.
- `docs/operations/`: runbooks de despliegue, backup, restore, incidentes y mantenimiento.
- `scripts/`: utilidades operativas locales.

## Requisitos locales

- Docker Desktop.
- Node.js 24 para trabajar en el frontend fuera de Docker.
- JDK 25 para compilar el backend fuera de Docker.

Si `java -version` no muestra Java 25, usar temporalmente:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Arranque rapido

```powershell
docker compose -f infra/docker-compose.yml up --build
```

URLs locales:

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- OpenAPI UI: http://localhost:8080/api/v1/docs
- Health: http://localhost:8080/actuator/health
- MinIO: http://localhost:9001

Credenciales locales de desarrollo:

- Usuario: `admin@gymos.local`
- Password: `Admin123!`

El seed demo esta restringido al perfil Spring `local`. En staging y produccion debe usarse:

```text
SPRING_PROFILES_ACTIVE=staging
GYMOS_SEED_ENABLED=false
```

## Operacion piloto

- Variables de entorno base: `infra/.env.example`.
- Reverse proxy HTTPS: `infra/caddy/Caddyfile.example`.
- Prometheus: `infra/monitoring/prometheus.yml`.
- Backup PostgreSQL: `scripts/backup-postgres.ps1`.
- Restore PostgreSQL: `scripts/restore-postgres.ps1`.
- Runbooks: `docs/operations/`.

## Validacion local

```powershell
cd backend
.\gradlew.bat clean test --no-daemon

cd ..\frontend
npm ci
npm run lint
npm run typecheck
npm run build

cd ..
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

## Flujo de trabajo

- Rama estable: `main`.
- Desarrollo: ramas `feature/*`.
- Commits: convencionales, por ejemplo `feat: add client registration`.
- No hacer push directo a `main` en operacion real; usar PR con CI verde.
- Cada hito debe incluir pruebas, migraciones, OpenAPI y documentacion actualizada.
