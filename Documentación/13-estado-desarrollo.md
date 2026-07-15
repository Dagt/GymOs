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

Continuar Hito 2:

- Comprobantes en MinIO.
- Validacion de pagos con rechazo y motivo.
- Reporte operativo basico de ventas y pagos.

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

## Hito 2 - Catalogo, ventas y pagos manuales

Estado: completado

Implementado:

- Migracion Flyway `V3__catalog_sales_payments.sql` con `catalog_product`, `membership_plan`, `sale`, `sale_item` y `payment`.
- Permisos base `catalog.manage`, `catalog.read`, `sale.create`, `sale.read`, `payment.manual.create` y `payment.manual.validate`.
- Datos semilla para `Membresia mensual` y `Botella de agua`.
- Modulo backend `catalog` con endpoints `GET /api/v1/catalog/products`, `POST /api/v1/catalog/products`, `GET /api/v1/catalog/plans` y `POST /api/v1/catalog/plans`.
- Modulo backend `sales` con endpoints `GET /api/v1/sales`, `GET /api/v1/sales/reports/summary`, `POST /api/v1/sales`, `GET /api/v1/sales/{saleId}`, `POST /api/v1/sales/{saleId}/payments`, `POST /api/v1/sales/payments/{paymentId}/validate` y `POST /api/v1/sales/payments/{paymentId}/reject`.
- Calculo backend de subtotal, descuentos, total, pagos completados y saldo.
- Pagos manuales `CASH`, `TRANSFER` y `CARD_MANUAL`.
- Estado de pago `PENDING_VALIDATION`, `COMPLETED` y `REJECTED`.
- Auditoria para producto creado, plan creado, venta creada, pago registrado, pago validado y pago rechazado.
- Pantalla frontend `/pos` con catalogo vendible, carrito, busqueda de cliente, pago manual y ventas recientes.
- Pantalla frontend `/catalog` con lista de productos, lista de planes y alta rapida de producto.
- Pantalla frontend `/sales/[saleId]` con detalle de venta, items, pagos, registro posterior de pagos, validacion de pagos pendientes y rechazo con motivo.
- Pantalla frontend `/reports/sales` con resumen de ventas, cobros, saldos, pagos por metodo, ventas por estado y ventas recientes.
- Accesos a POS, catalogo y reportes desde `/reception`.
- Migracion Flyway `V4__memberships_and_receipts.sql` con tabla `membership`.
- Configuracion backend de MinIO con bucket `gymos-receipts`.
- Endpoint backend `POST /api/v1/sales/payments/{paymentId}/receipt` para subir comprobantes reales.
- Activacion automatica de membresia `ACTIVE` cuando una venta de plan queda `PAID`.
- Pantalla frontend `/sales/[saleId]` con subida de comprobante y membresias activadas por la venta.

Verificado localmente:

- Backend tests con Java 25.
- Spring Modulith architecture test.
- Frontend lint.
- Frontend typecheck.
- Frontend production build.
- Docker build backend y frontend.
- Docker Compose up con Flyway aplicado hasta version 3.
- Smoke test `GET /api/v1/catalog/products`.
- Smoke test `GET /api/v1/catalog/plans`.
- Smoke test `POST /api/v1/catalog/products`.
- Smoke test `POST /api/v1/sales` con pago `TRANSFER` pendiente.
- Smoke test `GET /api/v1/sales/{saleId}`.
- Smoke test `POST /api/v1/sales/payments/{paymentId}/validate`.
- Smoke test `GET /api/v1/sales`.
- Smoke test `GET /pos`, `GET /catalog` y `GET /sales/{saleId}`.
- Docker Compose up con Flyway aplicado hasta version 4.
- Smoke test `POST /api/v1/sales/payments/{paymentId}/receipt`.
- Smoke test de objeto existente en MinIO dentro de `gymos-receipts`.
- Smoke test de venta de plan pagada con membresia `ACTIVE` generada.
- Smoke test `POST /api/v1/sales/{saleId}/payments` desde venta ya existente.
- Smoke test `POST /api/v1/sales/payments/{paymentId}/reject` con motivo.
- Smoke test de venta pendiente que ignora pagos `REJECTED` y queda pagada al registrar un nuevo pago `COMPLETED`.
- Smoke test `GET /api/v1/sales/reports/summary` con ventas, pagos y desgloses.
- Smoke test `GET /reports/sales`.

Pendiente del Hito 2:

- Sin pendientes funcionales para el alcance definido del hito.

## Hito 3 - Membresias y kiosco

Estado: completo

Implementado:

- Rama de trabajo `feature/hito-3-memberships-kiosk` creada desde `main` con Hito 2 integrado.
- Modulo backend `memberships` con endpoints `GET /api/v1/clients/{clientId}/memberships` y `GET /api/v1/memberships/{membershipId}`.
- Consulta de membresias por organizacion, sucursal activa y cliente.
- Pantalla frontend `/clients/[clientId]` con resumen de membresias del cliente y enlace a la venta que activo cada membresia.
- Endpoint backend `POST /api/v1/memberships/{membershipId}/renew` para renovacion acumulativa con venta y pago manual.
- Endpoint backend `POST /api/v1/memberships/{membershipId}/change-plan` para cambio de plan con venta, pago completado, cancelacion de membresia anterior y activacion de nuevo plan.
- Activacion acumulativa de membresias de plan: si existe una membresia vigente del mismo plan, la nueva inicia al dia siguiente del vencimiento anterior.
- Migracion Flyway `V5__hito3_plans_and_kiosk_seed.sql` con plan trimestral y terminal demo `KIOSK-MAIN`.
- Endpoints backend publicos de kiosco `POST /api/v1/self-service/terminals/{terminalCode}/sessions`, `POST /api/v1/self-service/sessions/{sessionId}/verify`, `GET /api/v1/self-service/sessions/{sessionId}/summary`, `GET /api/v1/self-service/sessions/{sessionId}/membership-options`, `POST /api/v1/self-service/sessions/{sessionId}/membership-renewals/{membershipId}`, `POST /api/v1/self-service/sessions/{sessionId}/membership-change-requests/{membershipId}` y `POST /api/v1/self-service/sessions/{sessionId}/end`.
- Sesiones temporales de kiosco con expiracion de 15 minutos, verificacion por email o telefono y auditoria `SELF_SERVICE_KIOSK`.
- Pantalla frontend `/clients/[clientId]` con renovacion manual y cambio de plan desde la ficha.
- Pantalla frontend `/kiosk/[terminalCode]` para verificacion de cliente, resumen limitado, pagos pendientes, renovacion y cambio de plan.
- Acceso al kiosco demo desde `/reception`.

Verificado localmente:

- Backend tests con Java 25.
- Spring Modulith architecture test.
- Frontend lint.
- Frontend typecheck.
- Frontend production build.
- Docker build backend y frontend.
- Smoke test `GET /api/v1/clients/{clientId}/memberships`.
- Smoke test `POST /api/v1/memberships/{membershipId}/renew` con pago `CASH` completado.
- Smoke test de renovacion acumulativa: membresia inicial termina `2026-08-13` y renovacion inicia `2026-08-14`.
- Smoke test `GET /clients/{clientId}` con membresias y renovacion visible.
- Smoke test Docker con Flyway V5 aplicada.
- Smoke test Docker `POST /api/v1/memberships/{membershipId}/change-plan` con pago `CASH` completado.
- Smoke test Docker `POST /api/v1/self-service/terminals/KIOSK-MAIN/sessions`.
- Smoke test Docker `POST /api/v1/self-service/sessions/{sessionId}/verify`.
- Smoke test Docker `GET /api/v1/self-service/sessions/{sessionId}/summary`.
- Smoke test Docker `GET /api/v1/self-service/sessions/{sessionId}/membership-options`.
- Smoke test Docker `POST /api/v1/self-service/sessions/{sessionId}/membership-renewals/{membershipId}`.
- Smoke test Docker `POST /api/v1/self-service/sessions/{sessionId}/membership-change-requests/{membershipId}`.
- Smoke test Docker `POST /api/v1/self-service/sessions/{sessionId}/end`.
- Smoke test Docker `GET /kiosk/KIOSK-MAIN`.

## Hito 4 - Operacion del gimnasio

Estado: en progreso

Implementado:

- Rama de trabajo `feature/hito-4-gym-operations` creada desde `main` con Hito 3 integrado.
- Migracion Flyway `V6__hito4_access_control.sql` con `access_device`, `access_temporary_pass`, `access_log`, permisos de acceso y dispositivo semilla `MAIN-DOOR`.
- Modulo backend `access` con endpoints `GET /api/v1/access/devices`, `GET /api/v1/access/logs`, `POST /api/v1/access/requests`, `POST /api/v1/access/commands/open` y `POST /api/v1/access/commands/temporary`.
- Evaluacion de acceso por cliente o identificador contra membresia activa en sucursal y periodo de gracia.
- Acceso por pase temporal con expiracion, numero maximo de usos y consumo automatico.
- Apertura manual con motivo obligatorio y comando simulado `SIMULATED_OPEN`.
- Bitacora de accesos concedidos/denegados con dispositivo, cliente, tipo, motivo, comando y correlacion.
- Auditoria de `ACCESS_GRANTED`, `ACCESS_DENIED` y `ACCESS_TEMPORARY_PASS_CREATED`.
- Pantalla frontend `/access` para recepcion: busqueda de cliente, evaluacion, apertura manual, pase temporal y bitacora reciente.
- Acceso directo a `/access` desde `/reception`.

Verificado localmente:

- Backend tests con Java 25.
- Spring Modulith architecture test.
- Frontend lint.
- Frontend typecheck.
- Frontend production build con ruta `/access`.
- Docker build backend y frontend.
- Smoke test Docker con Flyway V6 aplicada y dispositivo `MAIN-DOOR`.
- Smoke test Docker `GET /api/v1/access/devices`.
- Smoke test Docker `POST /api/v1/access/requests` con membresia activa y decision `GRANTED`.
- Smoke test Docker `POST /api/v1/access/requests` sin membresia ni pase temporal y decision `DENIED`.
- Smoke test Docker `POST /api/v1/access/commands/temporary`.
- Smoke test Docker `POST /api/v1/access/requests` con pase temporal vigente y decision `GRANTED`.
- Smoke test Docker `POST /api/v1/access/commands/open` con comando simulado.
- Smoke test Docker `GET /api/v1/access/logs`.
- Smoke test Docker `GET /access`.

Pendiente del Hito 4:

- Inventario: existencias, movimientos, mermas, alertas y descuento posterior por venta.
- Recursos y reservas: recursos reservables, disponibilidad, booking, check-in, finalizacion y cancelacion.
