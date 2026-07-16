# 13. Estado de Desarrollo

Estado: en progreso  
Fecha: 15 de julio de 2026

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

## Hito 3.5 - Estabilizacion arquitectonica de membresias, ventas y kiosco

Estado: completado tecnico

Implementado:

- Rama `feature/hito-3-5-architecture-stabilization` creada desde `main` con Hito 3 completo.
- API interna publica `SalesOperations` para renovaciones y cambios de plan de membresia.
- `memberships` dejo de escribir directamente en tablas `sale`, `sale_item` y `payment`.
- `sales` concentra creacion de ventas, partidas, pagos, validacion, eventos y numeros de venta.
- Comandos y resultados de application separados de DTOs HTTP en ventas, membresias, clientes, catalogo, organizaciones, seguridad y autoservicio.
- Eventos versionados de ventas/pagos/membresias en `common.events`.
- Eventos versionados movidos a APIs publicas de sus modulos propietarios: `sales.api.events` y `memberships.api.events`.
- API publica `MembershipOperations` para renovacion/cambio de plan consumida por `selfservice`.
- API publica `ClientLookupOperations` para verificacion de identidad y perfil limitado de kiosco.
- `selfservice` ya no depende de `memberships.application` ni consulta tablas `client`/`person`.
- `MembershipService` y `SelfServiceService` ya no usan `JdbcClient`; la persistencia queda en repositorios de su modulo.
- Consumo idempotente de `PaymentCompletedV1` mediante `processed_domain_event`.
- Idempotencia comercial en `sale` y `payment` con `idempotencyKey` y restricciones unicas.
- Hash de solicitud para ventas y pagos idempotentes mediante `sale.idempotency_request_hash` y `payment.idempotency_request_hash`.
- Kiosco endurecido con `sessionToken`, hash de token, header `X-Self-Service-Session-Token`, verificacion por contacto + fecha de nacimiento, expiracion, rate limiting por puerto Redis/local, contador de intentos y bloqueo temporal.
- RBAC granular por endpoint interno.
- Migracion `V2_1__demo_organization_prerequisite.sql` para que una base limpia pueda ejecutar datos demo historicos.
- Migracion `V6__hito3_5_architectural_stabilization.sql`.
- Migracion `V7__permissions_and_kiosk_security.sql`.
- Migracion `V8__idempotency_request_hashes.sql`.
- Seed local idempotente para completar admin, rol y permisos aunque la organizacion demo ya exista.
- Frontend actualizado para enviar idempotency keys, token de kiosco y fecha de nacimiento en verificacion.

Verificado:

- `backend`: `./gradlew.bat test` con Java 25.
- Spring Modulith architecture test.
- Reglas ArchUnit adicionales.
- Pruebas de seguridad MVC para permisos internos y acceso publico del kiosco.
- Prueba unitaria de rate limiting local.
- Testcontainers con PostgreSQL y Flyway.
- `frontend`: `npm.cmd run lint`.
- `frontend`: `npm.cmd run typecheck`.
- `frontend`: `npm.cmd run build`.

Riesgos residuales controlados:

- Algunos servicios application siguen usando `JdbcClient` internamente; el uso queda listado por prueba de arquitectura y debe extraerse a command repositories en un hito tecnico posterior.
- `frontend/src/lib/api.ts` sigue siendo un cliente API grande; se mantiene funcional y tipado, pero conviene dividirlo por dominio en una refactorizacion frontend dedicada.
- Aun falta una suite amplia de concurrencia real para pagos/renovaciones; la proteccion base existe por restricciones unicas, hashes de idempotencia, transacciones y eventos procesados.

## Hito 4 - Operacion del gimnasio

Estado: completado

Implementado:

- Rama de trabajo `feature/hito-4-gym-operations` actualizada sobre Hito 3.5.
- Migracion Flyway `V9__hito4_access_control.sql` con `access_device`, `access_temporary_pass`, `access_log`, permisos de acceso y dispositivo semilla `MAIN-DOOR`.
- Modulo backend `access` con endpoints `GET /api/v1/access/devices`, `GET /api/v1/access/logs`, `POST /api/v1/access/requests`, `POST /api/v1/access/commands/open` y `POST /api/v1/access/commands/temporary`.
- Evaluacion de acceso por cliente o identificador contra membresia activa en sucursal y periodo de gracia.
- Acceso por pase temporal con expiracion, numero maximo de usos y consumo automatico.
- Apertura manual con motivo obligatorio y comando simulado `SIMULATED_OPEN`.
- Bitacora de accesos concedidos/denegados con dispositivo, cliente, tipo, motivo, comando y correlacion.
- Auditoria de `ACCESS_GRANTED`, `ACCESS_DENIED` y `ACCESS_TEMPORARY_PASS_CREATED`.
- Pantalla frontend `/access` para recepcion: busqueda de cliente, evaluacion, apertura manual, pase temporal y bitacora reciente.
- Acceso directo a `/access` desde `/reception`.
- Migracion Flyway `V10__hito4_inventory.sql` con `inventory_stock`, `inventory_movement`, permisos de inventario e inventario semilla para `BOTELLA-AGUA`.
- Modulo backend `inventory` con endpoints `GET /api/v1/inventory`, `GET /api/v1/inventory/{productId}`, `POST /api/v1/inventory/movements`, `GET /api/v1/inventory/movements` y `GET /api/v1/inventory/low-stock`.
- Movimientos de inventario `INITIAL`, `PURCHASE`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `WASTE`, `RETURN` y `SALE`.
- Validacion de producto fisico activo, bloqueo de existencias negativas, minimo de inventario y costo promedio ponderado para entradas con costo.
- Auditoria de `INVENTORY_MOVEMENT_CREATED`.
- Pantalla frontend `/inventory` para existencias, alertas, movimientos, ajustes, mermas y costo unitario.
- Acceso directo a `/inventory` desde `/reception`.
- Migracion Flyway `V11__hito4_resources_bookings.sql` con `resource`, `booking`, permisos de recursos/reservas y recurso demo `Consultorio evaluacion`.
- Migracion Flyway `V12__hito4_inventory_sale_idempotency.sql` con indice unico para evitar doble descuento de inventario por venta y producto.
- Consumidor de `PaymentCompletedV1` en inventario para descontar automaticamente productos fisicos al pagarse una venta.
- Descuento automatico de inventario idempotente por organizacion, sucursal, venta y producto.
- Modulo backend `bookings` con endpoints `GET /api/v1/resources`, `POST /api/v1/resources`, `GET /api/v1/resources/{resourceId}/availability`, `GET /api/v1/bookings`, `GET /api/v1/bookings/{bookingId}`, `POST /api/v1/bookings`, `POST /api/v1/bookings/{bookingId}/confirm`, `POST /api/v1/bookings/{bookingId}/check-in`, `POST /api/v1/bookings/{bookingId}/complete` y `POST /api/v1/bookings/{bookingId}/cancel`.
- Validacion de disponibilidad por capacidad, horario del recurso y traslapes activos.
- Pantalla frontend `/bookings` para agenda diaria, disponibilidad, creacion de recursos, creacion de reservas y transiciones operativas.
- Acceso directo a `/bookings` desde `/reception`.

Verificado localmente:

- Backend tests con Java 25.
- Testcontainers PostgreSQL y Flyway hasta `V12`.
- Spring Modulith architecture test.
- Prueba unitaria de descuento automatico de inventario por evento de pago completado.
- Frontend lint, typecheck y production build.
- Docker Compose config.

Pendiente fuera del alcance central del Hito 4:

- Recursos y reservas: edicion de recursos, politicas avanzadas de cancelacion y cobro asociado a reservas.
- Integracion futura con hardware real de torniquetes o lectores fisicos.

## Hito 5 - Servicios avanzados

Estado: completo

Implementado:

- Rama de trabajo `feature/hito-5-advanced-services` creada desde Hito 4 completo.
- Migracion Flyway `V13__hito5_coaches_workouts.sql` con `coach`, `workout`, `workout_version`, `workout_assignment`, permisos y datos demo.
- Migracion Flyway `V14__hito5_wellness_notifications_automations.sql` con `professional`, `professional_service`, `professional_availability`, `notification_log`, `automation_rule`, `automation_execution`, permisos y datos demo.
- Modulo backend `coaching` con endpoints `GET /api/v1/coaches`, `POST /api/v1/coaches`, `GET /api/v1/workouts`, `POST /api/v1/workouts`, `GET /api/v1/workouts/{workoutId}`, `POST /api/v1/workouts/{workoutId}/versions`, `POST /api/v1/workouts/{workoutId}/assign`, `GET /api/v1/clients/{clientId}/workouts` y `GET /api/v1/reports/coaches`.
- Rutinas versionadas: cada asignacion apunta a una version concreta para preservar historial.
- Validaciones de coach activo, cliente activo, nivel de rutina, JSON de rutina y rango de fechas.
- Modulo backend `wellness` con profesionales de bienestar, servicios y disponibilidad por sucursal.
- Modulo backend `notifications` con registro interno de notificaciones operativas.
- Modulo backend `automations` con reglas habilitables, ejecucion manual y bitacora de ejecuciones.
- Modulo backend `reporting` con dashboard del dueno para ingresos, ventas, clientes, membresias, accesos, reservas, inventario, coaches, rutinas y profesionales.
- RBAC para `coach.read`, `coach.manage`, `workout.read`, `workout.manage`, `report.coaches.read`, `professional.read`, `professional.manage`, `notification.read`, `notification.manage`, `automation.read`, `automation.manage` y `report.owner.read`.
- Seed local actualizado para incluir permisos de Hito 4 y Hito 5 en el administrador demo.
- Pantalla frontend `/coaches` con alta de coach, alta de rutina, busqueda de cliente, asignacion de rutina y metricas base.
- Pantalla frontend `/wellness` con alta de profesional, servicios, disponibilidad y consulta por profesional.
- Pantalla frontend `/automations` con alta de reglas, habilitar/deshabilitar, ejecucion manual y notificaciones internas.
- Pantalla frontend `/dashboard` con metricas de dueno por rango de fechas.
- Accesos directos a `/coaches`, `/wellness`, `/automations` y `/dashboard` desde `/reception`.

Verificado localmente:

- Backend tests con Java 25.
- Testcontainers PostgreSQL y Flyway hasta `V14`.
- Pruebas de seguridad MVC para permisos de coaches, rutinas, profesionales, automatizaciones y notificaciones.
- Frontend lint.
- Frontend typecheck.
- Frontend build de produccion.
- Docker Compose config.

Pendiente fuera del alcance central del Hito 5:

- Integracion real con proveedores externos de email, SMS o WhatsApp; la V1 deja registro interno y contratos preparados.
- Automatizaciones programadas por scheduler; la V1 permite ejecucion manual y deja la estructura persistente lista.
- Agenda completa de servicios profesionales con reservas pagadas queda para la evolucion posterior sobre el modulo de reservas.

## Hito 6 - Productizacion por codigo

Estado: completo

Implementado en rama `feature/hito-6-productization`:

- Migracion Flyway `V15__hito6_productization_onboarding_imports.sql` con `client_import_batch`, `client_import_row` y permisos `onboarding.manage` y `client.import`.
- Modulo backend `onboarding` con endpoints `GET /api/v1/onboarding/checklist` y `POST /api/v1/onboarding/pilot-setup`.
- Checklist vendible para organizacion, sucursal, planes, productos, kiosco y clientes.
- Setup piloto idempotente para actualizar organizacion/sucursal activa y crear catalogo base y kiosco si faltan.
- Importacion inicial de clientes por CSV con endpoint `POST /api/v1/clients/imports`, dry-run, deteccion de duplicados, resultado por fila y bitacora persistente.
- RBAC granular para onboarding e importacion.
- Pantalla frontend `/onboarding`.
- Pantalla frontend `/clients/import`.
- Accesos directos desde `/reception`.

Verificado localmente:

- Backend tests con Java 25.
- Testcontainers PostgreSQL y Flyway hasta `V15`.
- Pruebas de seguridad MVC para permisos `onboarding.manage` y `client.import`.
- Frontend lint.
- Frontend typecheck.
- Frontend build de produccion.
- Docker Compose config y build.
- Docker Compose up con backend `UP` y frontend `/onboarding` respondiendo 200.
- Smoke autenticado: login, seleccion de sucursal, checklist, setup piloto, importacion dry-run e importacion real.

Pendiente fuera del alcance central del Hito 6:

- Las filas de smoke agregadas a la base local son datos de prueba; no forman parte de migraciones ni del repositorio.
- La importacion Excel directa queda para una evolucion posterior; la V1 usa CSV por ser suficiente para el piloto inicial.

## Hito 7 - Preparacion administrativa y piloto comercial

Estado: planificado

Alcance trasladado desde Hito 6 por decision de producto:

- Capacitacion y manual operativo del gimnasio piloto.
- Scripts, politica y checklist de backups.
- Checklist de venta, instalacion y produccion.
- Entorno staging formal.
- Preparacion administrativa del piloto real.
- Registro de feedback, soporte, precios y procesos comerciales.

## Hito 6.5 - Rediseño frontend y experiencia comercial

Estado: completado frontend

Implementado en rama `feature/hito-6-5-frontend-redesign`:

- Sistema visual global con tokens de marca, superficies, tarjetas, botones, navegacion y estados.
- Componente `BrandTheme` para personalizar nombre visible, URL de logo, color principal, acento y fondo.
- Componente `AppNavigation` con accesos agrupados por Operacion, Gestion y Direccion.
- Portada `/` convertida en centro operativo del producto.
- Login `/login` redisenado con formularios y textos mas claros para usuarios comunes.
- Recepcion `/reception` redisenada como tablero operativo con acciones criticas, contexto de sucursal, gestion, reportes y modulos.
- Dashboard `/dashboard` alineado al nuevo layout.
- Reporte de ventas `/reports/sales` alineado al nuevo layout y navegacion.
- Configuracion `/settings/organization` alineada al nuevo layout y con nota de marca visual.
- Sin cambios de contratos HTTP ni reglas de negocio frontend.

Verificado localmente:

- Frontend lint.
- Frontend typecheck.
- Frontend build de produccion.

Pendiente de evolucion posterior:

- Persistir tema visual por organizacion desde backend.
- Dividir `frontend/src/lib/api.ts` por dominio en una refactorizacion dedicada.
- Extender el nuevo layout a pantallas secundarias largas que todavia conservan estructura local propia.

Correccion aplicada despues de revision visual:

- Se elimino de `/` toda informacion tecnica de stack, hitos y desarrollo.
- `/` queda como entrada de producto por rol: administrador, recepcion y pantalla de socios.
- `/reception` deja de mostrar todos los modulos como tarjetas principales.
- `/reception` se reorganiza como consola administrativa con barra lateral, acciones frecuentes, reportes rapidos y herramientas secundarias plegadas.
- Los permisos del usuario pasan a una seccion plegable.
- Se removio el logotipo flotante permanente para evitar duplicidad visual; la personalizacion queda en el boton `Marca`.
- Se agregaron tonos visuales diferenciados para caja, socios, reportes y kiosco.

Verificado despues de la correccion:

- Frontend lint.
- Frontend typecheck.
- Frontend build de produccion.

## Hito 7 - Production readiness

Estado general: IN_PROGRESS

### Estado Hito 7.x

| Hito | Estado | Evidencia actual |
| ---- | ------ | ---------------- |
| 7.1 Calidad y regresion | IN_PROGRESS | Auditoria realizada; suite amplia pendiente. |
| 7.2 Refactorizacion tecnica | IN_PROGRESS | Cliente API frontend dividido con fachada compatible; backend application todavia usa JdbcClient en algunos servicios. |
| 7.3 Operacion y produccion | IN_PROGRESS | Runbooks, env example, Caddy example, Prometheus config, scripts backup/restore y seed local-only agregados. |
| 7.4 Cierre funcional minimo | IN_PROGRESS | Beneficios, paquetes de visitas y reembolsos manuales funcionales parcialmente; reservas pagadas y scheduler funcional pendientes. |
| 7.5 Preparacion del piloto | PLANNED | Manuales, feedback, soporte y pricing pendientes. |

### Matriz de modulos

| Modulo | Estado | Pruebas | Frontend | Documentacion | Produccion |
| ------ | ------ | ------- | -------- | ------------- | ---------- |
| identity/security | STABILIZED | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| organizations | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| clients | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| catalog | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| sales/payments | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| memberships | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| selfservice/kiosk | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| access | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| inventory | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| bookings | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| coaching | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| wellness | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| notifications | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| automations | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| reporting | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| onboarding/imports | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| benefits | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| visits | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| refunds | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| operations | IN_PROGRESS | MANUAL | N/A | FUNCTIONAL | IN_PROGRESS |

Estados permitidos: PLANNED, IN_PROGRESS, FUNCTIONAL, STABILIZED, PILOT_READY, PRODUCTION_READY.
