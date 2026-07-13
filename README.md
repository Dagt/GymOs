# GymOS

GymOS es una plataforma SaaS modular para la operacion integral de gimnasios y centros de bienestar.

## Estructura

- `backend/`: API Java 25 con Spring Boot, Spring Modulith, Flyway, PostgreSQL y Redis.
- `frontend/`: aplicacion web Next.js, React, TypeScript y Tailwind CSS.
- `infra/`: Docker Compose local con PostgreSQL, Redis y MinIO.
- `Documentación/`: documentacion maestra del producto y arquitectura.

## Requisitos locales

- Docker Desktop.
- Node.js 24 para trabajar en el frontend fuera de Docker.
- JDK 25 para compilar el backend fuera de Docker.

El equipo local detectado inicialmente tenia Java 8, por lo que el backend debe ejecutarse con Docker o con JDK 25 instalado/configurado.

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

## Flujo de trabajo

- Rama estable: `main`.
- Desarrollo: ramas `feature/*`.
- Commits: convencionales, por ejemplo `feat: add client registration`.
- Cada hito debe incluir pruebas, migraciones, OpenAPI y documentacion actualizada.
