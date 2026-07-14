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

Implementar Hito 2:

- Catalogo de productos, planes y beneficios.
- Venta con sale items.
- Pagos manuales en efectivo, transferencia y tarjeta registrada manualmente.
- Comprobantes en MinIO.
- Validacion manual de pagos.
- POS basico conectado a clientes y membresias.

## Hito 1 - Identidad, seguridad y clientes

Estado: completado funcionalmente

Implementado:

- Endpoints backend `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me` y `POST /api/v1/auth/select-branch`.
- JWT HMAC local con expiracion corta.
- Refresh tokens revocables almacenados en Redis.
- Filtro Bearer token integrado en Spring Security.
- Respuestas de autenticacion alineadas al formato de errores estandar.
- Seed local de organizacion, sucursal, persona, usuario administrador, rol y permisos base.
- Pantalla frontend `/login` consumiendo la API real.
- Endpoints backend `GET /api/v1/organizations/current` y `GET /api/v1/organizations/current/branches`.
- Endpoints backend `PUT /api/v1/organizations/current` y `PUT /api/v1/organizations/current/branches/{branchId}`.
- Auditoria persistente para login, logout, seleccion de sucursal, cambios de organizacion, cambios de sucursal, alta de cliente y actualizacion de cliente.
- Endpoints backend `GET /api/v1/clients`, `POST /api/v1/clients`, `GET /api/v1/clients/{clientId}` y `PUT /api/v1/clients/{clientId}`.
- Pantalla frontend `/reception` con sesion local, resumen de organizacion, selector de sucursal y permisos cargados.
- Pantalla frontend `/clients` con busqueda, lista y alta rapida.
- Pantalla frontend `/clients/[clientId]` con ficha editable y timeline operativo.
- Pantalla frontend `/settings/organization` para editar organizacion y sucursal inicial.

Credenciales locales de desarrollo:

- Usuario: `admin@gymos.local`
- Password: `Admin123!`
- Sucursal semilla: `22222222-2222-2222-2222-222222222222`

Verificado:

- Backend tests con Java 25.
- Spring Modulith architecture test.
- Docker build backend.
- Docker restart backend.
- Smoke test `POST /api/v1/auth/login`.
- Smoke test `GET /api/v1/auth/me`.
- Smoke test `POST /api/v1/auth/select-branch`.
- Smoke test `POST /api/v1/auth/refresh`.
- Frontend lint, typecheck y production build.
- Docker build frontend.
- Smoke test `GET /login`.
- Smoke test `GET /api/v1/organizations/current`.
- Smoke test `GET /api/v1/organizations/current/branches`.
- Smoke test `GET /reception`.
- Smoke test `PUT /api/v1/organizations/current`.
- Smoke test `PUT /api/v1/organizations/current/branches/{branchId}`.
- Smoke test `POST /api/v1/clients`.
- Smoke test `GET /api/v1/clients`.
- Smoke test `GET /api/v1/clients/{clientId}`.
- Smoke test `PUT /api/v1/clients/{clientId}`.
- Smoke test `POST /api/v1/auth/logout`.
- Smoke test `GET /clients`, `GET /clients/{clientId}` y `GET /settings/organization`.

Pendiente de producto fuera de Hito 1:

- RBAC granular por endpoint.
- Gestion avanzada de usuarios internos y roles desde UI.
- Importacion masiva de clientes.
- Fotos reales de cliente con almacenamiento de objetos.
