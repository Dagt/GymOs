# 13. Estado de Desarrollo

Estado: en progreso  
Fecha: 13 de julio de 2026

## Hito 0 - Base tecnica vendible

Implementado:

- Monorepo con `backend/`, `frontend/`, `infra/` y `Documentación/`.
- Backend Spring Boot 4.1.0 con Gradle Wrapper y toolchain Java 25.
- Dependencias base: Spring Web MVC, Security, Data JPA, Flyway, PostgreSQL, Redis, Actuator, Validation, OAuth2 Resource Server, Spring Modulith y Springdoc OpenAPI.
- Configuracion backend con PostgreSQL, Redis, Flyway, Actuator, OpenAPI y CORS por variable de entorno.
- Estructura inicial de modulos: `organizations`, `identity`, `security`, `clients`, `audit`, `configuration` y `selfservice`.
- Manejo uniforme de errores con `code`, `message`, `details`, `correlationId` y `timestamp`.
- Filtro de correlation id con header `X-Correlation-Id`.
- Migracion Flyway inicial para organizaciones, sucursales, identidad, clientes, kiosco y auditoria.
- Migracion Flyway para `event_publication` de Spring Modulith.
- Frontend Next.js 16.2.10 con TypeScript, Tailwind y pantalla inicial GymOS.
- Dockerfiles para backend y frontend.
- Docker Compose local con PostgreSQL, Redis, MinIO, backend y frontend.
- CI de GitHub Actions para backend y frontend.

Verificado:

- Frontend lint.
- Frontend typecheck.
- Frontend production build.
- Backend tests con Java 25.
- Docker Compose config.
- Docker Compose build de backend y frontend.
- Docker Compose up con PostgreSQL, Redis, MinIO, backend y frontend.
- Smoke test frontend en `http://localhost:3000`.
- Smoke test backend en `http://localhost:8080/api/v1/system/info`.
- Flyway aplicado en PostgreSQL hasta version 2.

Pendiente de verificar:

- Configuracion global de `JAVA_HOME` y `PATH` para que `java` y `javac` apunten a JDK 25 sin variables temporales.

Bloqueos locales:

- JDK 25 esta instalado en `C:\Program Files\Java\jdk-25.0.3`, pero el PATH global sigue resolviendo Java 8 desde Oracle javapath.

## Siguiente corte

Implementar Hito 1:

- Autenticacion real con login, refresh token y logout.
- Modelo persistente de usuarios, roles, permisos, organizacion y sucursal.
- Onboarding de organizacion y sucursal inicial.
- Alta, busqueda y ficha de cliente.
- Pantallas frontend de login, seleccion de sucursal, recepcion y clientes.
