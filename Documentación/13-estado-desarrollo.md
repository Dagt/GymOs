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
- Frontend Next.js 16.2.10 con TypeScript, Tailwind y pantalla inicial GymOS.
- Dockerfiles para backend y frontend.
- Docker Compose local con PostgreSQL, Redis, MinIO, backend y frontend.
- CI de GitHub Actions para backend y frontend.

Verificado:

- Frontend lint.
- Frontend typecheck.
- Frontend production build.

Pendiente de verificar:

- Backend tests y build con JDK 25.
- Docker build del backend y stack completo con Docker Desktop activo.

Bloqueos locales:

- El equipo tiene Java 8 configurado y Gradle 9.5.1 requiere JVM 17 o superior.
- Docker Desktop no estaba corriendo al intentar compilar backend con imagen JDK 25.

## Siguiente corte

Implementar Hito 1:

- Autenticacion real con login, refresh token y logout.
- Modelo persistente de usuarios, roles, permisos, organizacion y sucursal.
- Onboarding de organizacion y sucursal inicial.
- Alta, busqueda y ficha de cliente.
- Pantallas frontend de login, seleccion de sucursal, recepcion y clientes.
