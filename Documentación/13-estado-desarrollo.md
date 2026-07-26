# 13. Estado de Desarrollo

Estado: en progreso  
Fecha: 25 de julio de 2026

## Cierre de endurecimiento operativo y UX de Inventario - 25 de julio de 2026

Implementado en rama `feature/inventory-operations-hardening`:

- **Transferencias entre Sucursales & Seguridad de Gerentes:**
  - Corrección de la regla de Spring Security en `SecurityConfig` (se acotó `branch.update` a HTTP `PUT`) para habilitar la consulta `GET /api/v1/organizations/current/branches/all` en cuentas de Gerente.
  - Publicación automática de fichas de catálogo (`catalog.attachBranch`) al recibir transferencias de productos pertenecientes a la organización que aún no estuvieran publicados en la sucursal destino.
  - Validación preventiva en tiempo real de disponibilidad de stock en la sucursal de origen al crear o solicitar una transferencia.
- **Auditorías de Conteo Físico & Mapeo de Partidas:**
  - Mapeo completo de partidas (`items`) en la consulta `list()` de `InventoryTransferRepository` e `InventoryPhysicalCountRepository`.
  - Captura directa de conteo físico real con cálculo de varianzas en tiempo real y ajuste automático de stock al aprobar.
- **Rediseño UI/UX y Sistema de Diseño GymOS:**
  - Estandarización de la pantalla `/inventory` utilizando las primitivas de GymOS (`app-card`, `app-input`, `app-button`).
  - Navegación organizada en **4 pestañas independientes**: `Existencias y Alertas`, `Historial de Movimientos`, `Gestión de Transferencias` y `Auditorías`.
  - Alineación de altura entre Existencias y Alertas mediante CSS Flexbox, fijación de tamaño constante (`shrink-0 h-fit`) en las tarjetas de alerta de stock e invisibilización de las barras de desplazamiento con `[scrollbar-width:none] [&::-webkit-scrollbar]:hidden`.
  - Ampliación de columnas de menú desplegable en el buscador de `/catalog` para prevenir cortes de texto en etiquetas y placeholders.

Verificado:

- Frontend: `npm.cmd run typecheck`, `npm.cmd run test`, `npm.cmd run build`.
- Backend: `./gradlew.bat test`.
- Docker: `docker compose -f infra/docker-compose.yml up -d --build`.

## Mejora integral de catálogo e inventario - 23 de julio de 2026

Implementado en `feature/catalog-inventory-enhancement-plan`:

- metadatos comerciales, archivado lógico, categorías, etiquetas, código de barras e inventario opcional;
- puertos de aplicación y vistas que eliminan dependencias de API/aplicación hacia infraestructura;
- detalle, edición, estados explícitos, archivado, imágenes MinIO y búsqueda paginada;
- inventario, POS y onboarding respetan `inventory_tracked` y productos archivados;
- `/catalog` incorpora filtros URL, debounce, tabla, métricas, edición, categorías, imagen y permisos;
- decisiones y contratos técnicos documentados en `18-implementacion-catalogo-inventario.md`.

## Cierre de auditoria puntos 2-5 del refactor general - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- Se agrega cobertura E2E con Playwright para validar que cada rol entra a su consola correcta:
  - Admin GymOS a `/platform/gymnasiums`;
  - dueno del gimnasio a `/dashboard`;
  - gerente a `/operations`;
  - recepcion a `/reception`;
  - Admin GymOS en soporte con banda visible y regreso a administracion de gimnasios.
- Las pruebas E2E mockean la API sobre `/api/v1/**` y validan menu/experiencia por permisos, no por nombre de usuario.
- `OwnerDashboardController` deja de exponer el record de infraestructura `OwnerDashboardRecord`; el contrato REST usa `OwnerDashboardResult` de application.
- Se agrega prueba de arquitectura para impedir que `reporting.api` dependa de `reporting.infrastructure`.
- La revision de repositorios grandes confirma que `SalesRepository` y `CatalogRepository` quedan como fachadas delegadas, y que reporting mantiene un repositorio SQL focalizado para queries agregadas.
- Queda documentada la regla de auditoria final: API -> application -> infrastructure, con DTOs/resultados de application en los contratos internos y externos.

Verificado:

- Frontend E2E: `npm.cmd run test:e2e`.
- Frontend: `npm.cmd run test`, `npm.cmd run lint`, `npm.cmd run typecheck`, `npm.cmd run build`.
- Backend: `./gradlew.bat test`.
- Docker: `docker compose -f infra/docker-compose.yml up -d --build`.
- Smoke local: frontend responde en `http://localhost:3000/login` y backend queda `UP` en `http://localhost:8080/actuator/health`.

## Correccion regresion de usuarios y recursos reservables - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- El rol `Gerente` recibe permisos `user.read` y `user.manage` mediante la migracion `V31__manager_can_manage_operational_staff.sql`, manteniendo las restricciones de negocio existentes: solo puede crear o administrar usuarios operativos permitidos, no duenios, otros gerentes ni administradores GymOS.
- El formulario de usuarios bloquea y preselecciona la unica sucursal disponible cuando el gerente solo tiene alcance en una sucursal, evitando altas sin `branchIds`.
- La lista de roles del gerente queda limitada a `Recepcion` y `Coach`; las pruebas de application validan que no pueda ver ni gestionar duenios o gerentes.
- La persistencia de recursos reservables sale de `BookingRepository` y queda en `BookingResourceRepository`; `BookingRepository` conserva reservas, disponibilidad y pagos vinculados.
- Se agregan `PUT /resources/{resourceId}` y `DELETE /resources/{resourceId}` para editar y eliminar recursos reservables. La eliminacion es logica y se bloquea si existen reservas activas.
- La UI de reservas muestra acciones de editar/eliminar recurso solo a Admin GymOS en soporte o duenio del gimnasio con permiso `resource.manage`; gerencia y recepcion no pueden administrar recursos desde esa pantalla.
- Se agregan pruebas enfocadas de recursos, elegibilidad de membresia y protecciones de usuarios internos.

Verificado:

- Backend focal: `./gradlew test --tests "com.gymos.bookings.application.*" --tests "com.gymos.identity.application.UserAdministrationProtectionTests"`.
- Frontend: `npm.cmd run test`, `npm.cmd run lint`, `npm.cmd run typecheck`.

## Saneamiento application y reservas con membresia - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `clients.application` deja de usar `JdbcClient` directamente; las escrituras de alta, actualizacion e importacion viven ahora en `ClientCommandRepository` y `ClientImportRepository`.
- `organizations.application` deja de usar `JdbcClient` directamente; altas/ediciones de organizacion, sucursal y acceso por sucursal viven en `OrganizationCommandRepository`.
- `onboarding.application` deja de usar `JdbcClient` directamente; la preparacion piloto, productos base, planes, kiosco y checklist quedan encapsulados en `OnboardingRepository`.
- La prueba de arquitectura `applicationLayerDoesNotUseJdbcClient` ya no depende de allowlist: cualquier nuevo uso directo de `JdbcClient` en `application` falla la suite.
- Se agrega prueba de integracion PostgreSQL/Testcontainers `BookingMembershipPlanIntegrationTests` para cubrir el flujo catalogo -> plan de membresia -> socio con membresia activa -> recurso restringido -> reserva permitida.
- La misma prueba valida el rechazo cuando un socio no tiene membresia activa para reservar un recurso restringido por plan.

Verificado:

- Backend: `./gradlew.bat clean test`.

## Saneamiento catalogo e infraestructura de pruebas - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `CatalogRepository` queda como fachada estable para los consumidores existentes del modulo `catalog`.
- Las consultas de productos/servicios se separan en `CatalogProductQueryRepository`.
- Las consultas de planes de membresia se separan en `CatalogPlanQueryRepository`.
- La verificacion de SKU duplicado se separa en `CatalogSkuLookupRepository`.
- Los mapeos JDBC compartidos quedan en `CatalogRowMappers`, evitando duplicacion entre consultas de lista y detalle.
- Se agrega prueba de arquitectura `CatalogRepositoryArchitectureTests` para impedir que la fachada vuelva a contener SQL directo o `JdbcClient`.
- La configuracion `application-test.yml` mantiene viva la base H2 durante el apagado del contexto (`DB_CLOSE_DELAY=-1`) y evita `create-drop`; con esto desaparece el warning no bloqueante de Spring Modulith al consultar `event_publication` durante shutdown.

Verificado:

- Backend: `./gradlew.bat test` completo. Resultado exitoso sin warning de `event_publication`.

## Saneamiento operaciones de membresia - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `MembershipRenewalService` y `MembershipPlanChangeService` dejan de duplicar reglas de carga, estado activo, plan disponible, idempotencia y resolucion de resultado.
- Se agregan servicios internos pequenos:
  - `MembershipOperationPolicy` para validar membresia activa, plan disponible, cambio a plan distinto y pago completado requerido;
  - `MembershipOperationIdempotency` para normalizar o generar llaves de idempotencia por operacion;
  - `MembershipSaleResultResolver` para mapear la venta creada y, si el pago completo activo membresia, adjuntar el resumen resultante.
- Se agrega `MembershipApplicationArchitectureTests` para evitar que las operaciones de renovacion/cambio de plan vuelvan a depender directamente de `MembershipRepository`.
- No cambia ningun contrato HTTP ni API publica interna; se conserva el comportamiento de renovacion, cambio de plan, idempotencia y activacion por pago.

## Saneamiento reembolsos, activacion y catalogo - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `SalesRefundService` queda como fachada del caso de uso y delega responsabilidades en:
  - `SalesRefundIdempotencyService` para llaves de idempotencia y reintentos estables;
  - `SalesRefundValidationService` para monto, pago seleccionado y saldo reembolsable;
  - `SalesRefundProcessor` para persistencia y auditoria del reembolso procesado.
- `MembershipActivationService` conserva el contrato transaccional de consumo de `PaymentCompletedV1`, pero delega:
  - calculo de ventanas de activacion/renovacion en `MembershipActivationWindowCalculator`;
  - publicacion de eventos versionados en `MembershipActivationEventPublisher`.
- `CatalogService` queda como fachada estable del modulo catalogo.
- La escritura SQL de productos y planes sale de application y vive en `CatalogCommandRepository`.
- La logica de productos/servicios y la logica de planes de membresia quedan separadas en `CatalogProductService` y `CatalogMembershipPlanService`.
- El frontend agrega un modelo explicito de catalogo (`CatalogModel.ts`) para mantener productos/servicios vendibles separados de productos tecnicos `MEMBERSHIP_PLAN`.
- Se fortalece la prueba de arquitectura para que `catalog.application.CatalogService` ya no este autorizado a usar `JdbcClient`.
- Se agrega prueba de arquitectura para evitar que `SalesRefundService` vuelva a concentrar repositorio, command repository y auditoria directamente.

Intencion:

- Mantener intactos contratos HTTP y flujos existentes.
- Reducir acoplamiento entre application e infrastructure.
- Evitar que productos de membresia se mezclen de nuevo con productos/servicios vendibles.

## Saneamiento cliente, catalogo y activacion de membresias - 22 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `clients/[clientId]/ClientDetailSections.tsx` deja de concentrar toda la UI visual de ficha de socio.
- La ficha de socio queda separada en componentes presentacionales:
  - `ClientProfileCard.tsx` para datos personales;
  - `MembershipHistory.tsx` para historial, renovacion y cambio de plan;
  - `ClientSummaryAside.tsx` para contacto y timeline;
  - `ClientDetailDrawers.tsx` para paneles laterales de edicion y acciones;
  - `ClientDetailMetric.tsx` para metricas compactas reutilizables.
- `/catalog` extrae la orquestacion a `useCatalogController.ts`: carga de catalogo, alta de producto, alta/edicion de planes, mensajes, drawers y productos vendibles.
- `catalog/page.tsx` queda como composicion de alto nivel con toolbar, listas y drawers; ya no mezcla estado remoto con estructura visual.
- `memberships.application` separa el consumo de `PaymentCompletedV1` en `MembershipActivationService`.
- `MembershipService` queda enfocado en casos de uso invocados por usuarios/API interna: consulta, renovacion y cambio de plan.
- La activacion, renovacion por evento, cambio de plan por evento, auditoria y publicacion de eventos versionados quedan encapsulados en un servicio transaccional dedicado.
- Los tests de idempotencia de eventos apuntan ahora a `MembershipActivationService`, manteniendo cobertura sobre reprocesamiento de pagos.

Verificado:

- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run lint`.
- Backend: `./gradlew.bat test`.

Observacion:

- Backend pasa completo. Persiste un warning no bloqueante de apagado de Spring Modulith en pruebas H2 cuando intenta consultar `event_publication` despues de cerrar una base efimera; sigue recomendado resolverlo en el bloque backend profundo de infraestructura/test configuration.

## Refactor frontend reservas - 21 de julio de 2026

Implementado:

- `/bookings/page.tsx` conserva la orquestacion de datos, estado y casos de uso de reservas.
- Se extrae UI presentacional de reservas a `frontend/src/app/bookings/components.tsx`: modales, resumen del dia, calendario mensual y detalle por recurso.
- Se extraen formularios drawer de nueva reserva y nuevo recurso a `frontend/src/app/bookings/forms.tsx`.
- Se extraen helpers compartidos de fechas, horarios, colores de recursos y serializacion de horarios a `frontend/src/app/bookings/booking-utils.ts`.
- El resumen de recursos queda reutilizable y mantiene tarjetas compactas en dos columnas con scroll interno invisible.
- El refactor no cambia contratos HTTP ni reglas de negocio; reduce duplicacion entre la pagina, el calendario y los formularios.

Verificado:

- `npm.cmd run typecheck`
- `npm.cmd run lint`
- `npm.cmd run build`

## Refactor frontend plataforma - 21 de julio de 2026

Implementado:

- `/platform/gymnasiums/page.tsx` conserva carga de datos, seleccion de tenant, soporte, alta y suspension.
- Se extrae la lista de gimnasios y el panel de detalle/sucursales a `frontend/src/app/platform/gymnasiums/PlatformGymnasiumPanels.tsx`.
- El refactor no cambia rutas ni contratos HTTP; prepara mejoras posteriores de edicion, soporte y escalabilidad visual.

Verificado:

- `npm.cmd run typecheck`
- `npm.cmd run lint`
- `npm.cmd run build`

## Refactor frontend equipo y accesos - 21 de julio de 2026

Implementado:

- `frontend/src/app/users/UsersSections.tsx` reemplaza inputs locales del drawer de creacion de usuarios por `TextField`, `EmailField`, `PhoneField`, `PasswordField` y `SelectField`.
- Se elimina el componente `Field` duplicado para mantener estilos, validaciones y mensajes alineados con los formularios comunes de GymOS.
- El refactor no cambia contratos HTTP ni permisos; conserva creacion, suspension y cambio de rol de usuarios internos.

Verificado:

- `npm.cmd run typecheck`
- `npm.cmd run lint`
- `npm.cmd run build`

## Correccion encabezado y kiosco tenant - 20 de julio de 2026

Implementado:

- En perfiles `Administrador GymOS` en soporte y `Dueno del gimnasio`, el encabezado oculta la tarjeta estatica `Sucursal` cuando ya existe selector `Operar en`.
- Gerente, recepcion y coach conservan tarjeta `Sucursal`; si solo tienen una sucursal asignada, no se muestra selector redundante `Operar en`.
- El kiosco envia contexto de organizacion/sucursal cuando se abre desde una sesion autenticada.
- Migracion `V28__tenant_default_kiosk_terminals.sql` crea `KIOSK-MAIN` para gimnasios con sucursal activa que aun no tenian terminal.
- El backend resuelve primero terminal por sucursal, luego por gimnasio, antes de usar la resolucion publica por codigo.

Verificado:

- `npm.cmd run lint`
- `npm.cmd run typecheck`
- `./gradlew.bat test`
- Smoke API kiosco con headers devuelve `Appex Hub Deportivo`.

## Correccion UX reservas - 20 de julio de 2026

Implementado:

- `/bookings` reemplaza el flujo anterior por calendario mensual con detalle del dia seleccionado por recurso.
- Nueva reserva usa busqueda de socio por texto en lugar de desplegable con todos los clientes.
- El formulario solo captura recurso, socio, dia/hora de inicio y notas; la hora final se calcula con la duracion (`slotMinutes`) del recurso.
- La creacion de recurso oculta campos tecnicos de tipo/capacidad, permite duracion manual en minutos u horas y captura dias L M M J V S D con horario por dia.
- Se elimina la seccion visible de pago de reserva del flujo base; las reservas se gobiernan por membresias permitidas.
- Disponibilidad y agenda quedan integradas en un solo calendario visual.
- El campo de hora valida formato `HH:mm` antes de calcular la hora final para evitar errores al capturar minutos.
- Los horarios de recursos se normalizan a `HH:mm` aunque la API devuelva segundos, y los dias pasados solo permiten consulta historica sin crear reservas.

Verificado:

- `npm.cmd run lint`
- `npm.cmd run typecheck`

## Correccion permisos recepcion y marca de gimnasio - 20 de julio de 2026

Implementado:

- Migracion `V27__reception_booking_scope_and_gym_branding.sql`.
- El rol `Recepcion` conserva lectura de recursos y gestion de reservas, pero pierde `resource.manage`; ya no puede crear recursos reservables.
- `/bookings` oculta el alta rapida de recursos para recepcion, manteniendo el flujo de reservas.
- La marca visual queda exclusivamente en el gimnasio: sucursales, kiosco, recepcion y consolas internas heredan `brandName`, `logoUrl`, `primaryColor`, `accentColor` y `surfaceColor` desde `organization`.
- El dueño del gimnasio vuelve a iniciar con la primera sucursal activa permitida para evitar el error `BRANCH_REQUIRED` en el dashboard.

Verificado:

- `npm.cmd run lint`
- `npm.cmd run typecheck`
- `./gradlew.bat test`

## Correccion frontend recepcion - 19 de julio de 2026

Implementado:

- La portada `/reception` ahora funciona como dashboard rapido de mostrador sin menu lateral duplicado.
- La pantalla muestra marca, logo, sucursal activa, perfil, usuario, alcance y salida de sesion en una tarjeta superior.
- Las acciones de recepcion quedan en una sola cuadricula: caja/POS, socios, ventas, kiosco, reservas y beneficios.
- Se elimino la seccion secundaria "Mas herramientas de recepcion" para evitar duplicidad y saturacion.
- Si el usuario de recepcion tiene una sola sucursal permitida y la sesion guardada no trae `branchId`, el frontend selecciona automaticamente esa sucursal.

Verificado:

- `npm.cmd run lint`
- `npm.cmd run typecheck`
- `npm.cmd run build`

## Correccion catalogo y formularios - 19 de julio de 2026

Implementado:

- `/catalog` separa productos/servicios de planes de membresia.
- El formulario de producto ya no permite crear membresias incompletas como producto comun.
- El formulario de plan de membresia usa `POST /catalog/membership-plans` para crear en una sola transaccion el producto tecnico `MEMBERSHIP_PLAN` y el registro `membership_plan` con duracion y dias de gracia.
- Los beneficios escritos como texto se normalizan en backend a JSON valido para evitar fallos PostgreSQL y productos tecnicos incompletos.
- El listado de productos oculta productos tecnicos de membresia para no mezclarlos con suplementos, ropa o servicios.
- La migracion `V22__cleanup_orphan_membership_products.sql` marca como eliminados los productos `MEMBERSHIP_PLAN` antiguos que quedaron sin plan asociado y sin ventas.
- Los campos de formularios compartidos mantienen altura consistente aunque un campo vecino tenga texto de ayuda.
- Los botones primarios dentro del sistema usan `primaryColor` del tenant y muestran `accentColor` como acento visual consistente.

Verificado:

- `./gradlew.bat test`
- `npm.cmd run lint`
- `npm.cmd run typecheck`
- `npm.cmd run build`

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

Estado histórico: completo como base técnica. El módulo de Coaches y rutinas
permanece `IN_PROGRESS` para producto final según
`25-plan-accion-coaches-rutinas.md`.

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

Brechas de Coaches y rutinas abiertas en `feature/coaches-workouts`:

- catálogo amplio/extensible de ejercicios y seed;
- constructor visual en lugar de captura JSON manual;
- borrador, publicación, duplicado y modelo normalizado de sesiones/bloques;
- asignación idempotente, reemplazo e historial completo;
- PDF imprimible por asignación;
- métricas semanales por coach y detalle de socios;
- permisos granulares, aislamiento y pruebas propias del módulo.

Decisión de alcance: una rutina no es producto ni servicio de POS. Los cobros en
efectivo entre coach y socio no se registran y no generan ventas, pagos, comisiones,
ingresos ni movimientos de caja en GymOS.

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
| 7.4 Cierre funcional minimo | FUNCTIONAL | Beneficios, paquetes de visitas, reembolsos manuales, reservas pagadas por venta vinculada y scheduler interno funcionales parcialmente. |
| 7.5 Preparacion del piloto | PLANNED | Manuales, feedback, soporte y pricing pendientes. |
| 7.6 Administracion multi-sucursal y equipo | FUNCTIONAL | API/UI para crear multiples sucursales, crear usuarios internos, asignar rol y asignar sucursales; navegacion filtrada por permisos. |
| 7.8 Separacion SaaS/tenant por rol | FUNCTIONAL | Admin GymOS, dueño, gerente, recepcion y coach separados por `console`; soporte auditable y migracion V20. |
| 7.9 Correccion CRM plataforma | FUNCTIONAL | Alta de gimnasio separada de sucursales; botones de detalle navegan con soporte activo; creacion de sucursal funcional desde CRM. |

### Matriz de modulos

| Modulo | Estado | Pruebas | Frontend | Documentacion | Produccion |
| ------ | ------ | ------- | -------- | ------------- | ---------- |
| identity/security | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| organizations | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| clients | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| catalog | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| sales/payments | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| memberships | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| selfservice/kiosk | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| access | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| inventory | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| bookings | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| coaching | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| wellness | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| notifications | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| automations | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| reporting | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| onboarding/imports | FUNCTIONAL | PARTIAL | FUNCTIONAL | PARTIAL | IN_PROGRESS |
| benefits | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| visits | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| refunds | FUNCTIONAL | PARTIAL | FUNCTIONAL | FUNCTIONAL | IN_PROGRESS |
| operations | IN_PROGRESS | MANUAL | N/A | FUNCTIONAL | IN_PROGRESS |

Estados permitidos: PLANNED, IN_PROGRESS, FUNCTIONAL, STABILIZED, PILOT_READY, PRODUCTION_READY.

### Hito 7.6 - Administracion multi-sucursal y equipo

Estado: FUNCTIONAL

Implementado:

- Migracion `V17__team_roles_and_branch_creation.sql`.
- Permisos nuevos: `branch.create`, `user.read`, `user.manage`.
- Roles semilla: `Administrador GymOS`, `Dueno del gimnasio`, `Recepcion`, `Coach`, `Operacion`.
- Endpoint `POST /api/v1/organizations/current/branches` para crear multiples sucursales en la organizacion actual.
- Endpoints `GET /api/v1/users/roles`, `GET /api/v1/users/internal`, `POST /api/v1/users/internal`.
- Pantalla frontend `/settings/organization` separada por datos del gimnasio, creacion de sucursales, edicion de sucursales y acceso a equipo.
- Pantalla frontend `/users` para crear usuarios internos, elegir rol y asignar sucursales.
- `AppShell` y `GlobalSessionNav` filtran menus por permisos del usuario activo.
- `/admin` ahora muestra ruta recomendada: revisar gimnasio, crear sucursales, crear equipo/accesos y seleccionar sucursal operativa.

Verificado localmente:

- Backend: `.\gradlew.bat test --no-daemon` con Java 25.
- Frontend: `npm.cmd run lint`.
- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run build`.

Notas:

- Despues de aplicar la migracion o reiniciar el backend local, los usuarios deben volver a iniciar sesion para que el JWT incluya `branch.create`, `user.read` y `user.manage`.
- Sigue pendiente una suite amplia de pruebas automatizadas para CRUD completo de usuarios, edicion/desactivacion de usuarios y validaciones multi-tenant negativas.

### Hito 7.7 - Estabilizacion frontend por consolas y marca tenant

Estado: FUNCTIONAL

Implementado:

- Nueva consola de plataforma `/platform/gymnasiums` para Admin GymOS con layout maestro-detalle, buscador de gimnasios, detalle del tenant seleccionado, tabla compacta de sucursales y alta de gimnasio en panel lateral.
- `/admin` queda como redireccion compatible hacia `/platform/gymnasiums`.
- `AppShell` es el unico menu visible. `GlobalSessionNav` queda como guardia silencioso de rutas para evitar menus duplicados.
- Modelo frontend `resolveUserConsole` separa `PLATFORM_OWNER`, `TENANT_OWNER`, `TENANT_MANAGER`, `RECEPTION` y `COACH` por sesion, permisos y modo soporte.
- Admin GymOS siempre ve "Volver a administracion de gimnasios" cuando entra a pantallas de un gimnasio.
- Migracion `V18__organization_branding.sql` agrega marca por gimnasio: `brand_name`, `logo_url`, `primary_color`, `accent_color`, `surface_color`.
- Migracion `V19__platform_admin_legacy_role.sql` asigna `platform.admin` al rol legacy `Administrador` y al rol `Administrador GymOS`.
- `TenantThemeProvider` aplica marca del gimnasio activo a gerencia, recepcion, kiosco y pantallas internas; la consola de plataforma conserva marca neutral GymOS.
- `settings/organization` permite editar nombre visible, logo y colores del tenant.
- Form components iniciales (`TextField`, `EmailField`, `PhoneField`, `PasswordField`, `SelectField`, `FormError`) con validacion guiada y mensajes por campo.
- Creacion de usuarios valida telefono, email, contrasena minima, rol y sucursal antes de enviar.
- Roles de plataforma se ocultan en creacion de usuarios de gimnasio y backend bloquea asignacion indebida con `ROLE_NOT_AVAILABLE`.
- Login acepta username o email.

Verificado localmente:

- Backend: `./gradlew test` con `JAVA_HOME=C:\Program Files\Java\jdk-25.0.3`.
- Frontend: `npm.cmd run lint`.
- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run build`.
- Docker Compose: `docker compose -f infra/docker-compose.yml config`.
- Docker Compose runtime: backend, frontend, PostgreSQL, Redis y MinIO levantados localmente.
- Smoke auth: `admin@gymos.local` inicia sesion con permiso `platform.admin` y sin sucursal forzada.

Notas:

- Despues de `V18`/`V19`, los usuarios existentes deben volver a iniciar sesion para recibir marca actualizada y `platform.admin` en el JWT si aplica.
- El personalizador flotante local de marca fue eliminado; la marca ahora pertenece al gimnasio y se guarda en backend.
- Documentacion sincronizada en SDS, modelo de datos, contratos API, casos de uso y especificacion frontend.
- Rama estable esperada: `main`. Rama de trabajo equivalente para auditoria: `feature/frontend-architecture-stabilization`.

### Hito 7.8 - Separacion SaaS plataforma/tenant

Estado: FUNCTIONAL

Implementado:

- Migracion `V20__platform_and_tenant_role_separation.sql`.
- `platform.admin` queda reservado para `Administrador GymOS`; el rol tenant `Dueno del gimnasio` no recibe permisos de plataforma.
- La sesion expone `roles`, `platformAdmin`, `supportMode` y `console`.
- Consolas frontend:
  - `PLATFORM_OWNER` -> `/platform/gymnasiums`.
  - `TENANT_OWNER` -> `/dashboard`.
  - `TENANT_MANAGER` -> `/operations`.
  - `RECEPTION` -> `/reception`.
  - `COACH` -> `/coaches`.
- Admin GymOS entra a gimnasios cliente en modo soporte auditable con banda visible `Modo soporte GymOS`.
- Las acciones con `supportMode` se registran como `PLATFORM_SUPPORT` en auditoria.
- `/platform/gymnasiums` queda descrito como CRM SaaS de gimnasios cliente.
- `/operations` queda como consola de gerencia para operacion diaria.
- `/reception` queda centrado en mostrador y elimina configuracion administrativa del cuerpo principal.
- Roles visibles en `/users`: Admin GymOS, Dueño del gimnasio, Gerente, Recepcion y Coach.

Verificado para integracion a `main`:

- Backend: `./gradlew test` con Java 25.
- Frontend: `npm.cmd run lint`.
- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run build`.
- Docker Compose: `docker compose -f infra/docker-compose.yml config`.
- Docker runtime local: backend, frontend, PostgreSQL, Redis y MinIO levantados.
- Flyway local: migracion `V20` aplicada y schema en version 20.
- Smoke auth: Admin GymOS inicia como `PLATFORM_OWNER`, entra a soporte como `TENANT_OWNER` con `supportMode = true` y `/users/roles` no expone `Administrador GymOS` dentro del tenant.

Ramas:

- `main`: version estable integrada.
- `feature/platform-tenant-role-ux-correction`: rama de auditoria de la correccion.

### Hito 7.9 - Correccion CRM plataforma y sucursales

Estado: FUNCTIONAL

Implementado:

- `POST /organizations` ya no obliga a capturar `firstBranchName`, `firstBranchCode` ni `firstBranchPhone`.
- Admin GymOS puede crear un gimnasio/tenant con datos comerciales, contacto y marca base sin crear una sucursal en el mismo formulario.
- La creacion de sucursal queda como accion independiente dentro del detalle del gimnasio seleccionado en `/platform/gymnasiums`.
- `/platform/gymnasiums` inicia modo soporte automaticamente antes de abrir `Datos, marca y sucursales` o `Equipo y accesos` para el gimnasio seleccionado.
- En modo soporte, Admin GymOS puede listar, crear y actualizar sucursales del tenant aunque no tenga `user_branch_access` como trabajador del gimnasio.
- `GET /organizations` lista todos los gimnasios para `platform.admin`, incluyendo tenants sin sucursales.
- `GET /organizations/{organizationId}/branches` lista sucursales del gimnasio seleccionado desde el CRM sin depender del contexto activo de soporte.
- La organizacion tecnica base del Admin GymOS queda oculta del CRM para que una base limpia inicie con cero gimnasios cliente.
- El detalle de un gimnasio sin sucursales muestra estado vacio accionable en lugar de una tabla confusa.
- El menu lateral de `AppShell` queda plegado por secciones para evitar que dueño/soporte vean todas las funciones expandidas al mismo tiempo.
- El menu lateral oculta modulos operativos cuando no hay sucursal seleccionada y conserva solo configuracion de tenant.
- Admin GymOS puede suspender/reactivar gimnasios cliente; usuarios tenant de gimnasios suspendidos o inactivos no pueden iniciar sesion.
- `/users` permite suspender/reactivar usuarios internos sin borrado fisico.
- Login y refresh de usuarios tenant asignan automaticamente la primera sucursal activa permitida, evitando que gerentes/recepcionistas/coaches entren sin sucursal cuando ya tienen acceso asignado.
- El CRM de plataforma recarga sucursales por gimnasio seleccionado y limpia resultados anteriores para evitar estados stale al volver de soporte o despues de reiniciar sesion.
- La sucursal activa en soporte ya no queda como accion bloqueada; el boton permite abrir las herramientas del soporte para esa sucursal.
- El bloque redundante de herramientas de soporte se elimina del CRM; el acceso operativo se realiza desde la sucursal seleccionada.
- Alta y edicion de gimnasio cargan logo con selector de archivo y guardan el resultado en `frontend/public/tenants/<slug>/logo.*`.
- `infra/docker-compose.yml` monta `frontend/public/tenants` dentro del frontend para conservar logos locales.
- El tema tenant aplica `accentColor` y `surfaceColor` en elementos visibles: fondo, chips, estados activos, botones secundarios y detalles de marca.
- Login con credenciales invalidas muestra `Usuario o contrasena incorrectos` en lugar de un error generico.
- Backend y frontend impiden que gerentes creen o asignen roles `Dueno del gimnasio` o `Gerente`; el backend responde `ROLE_NOT_ALLOWED` ante intentos manuales.
- `/reception` carga sucursales de forma independiente a la lectura administrativa de organizacion, por lo que recepcionistas no quedan bloqueados por permisos de tenant.
- El inicio de recepcion queda como dashboard operativo rapido; el estado sin sucursal ya no muestra alta/preparacion de gimnasio ni enlaces administrativos.

## Correccion UX/validaciones administrativas - 19 de julio de 2026

Implementado:

- Validacion frontend y backend de RFC mexicano para gimnasios: 4 letras, 6 numeros y 3 caracteres alfanumericos.
- Validacion frontend y backend de telefonos de gimnasio, sucursal y usuarios internos en formato `+52` mas 10 digitos.
- Normalizacion frontend de telefonos de 10 digitos para anteponer `+52`.
- Confirmacion antes de suspender o reactivar gimnasios cliente.
- Confirmacion antes de suspender o reactivar usuarios internos.
- Endpoint `PATCH /api/v1/users/internal/{userId}/role` para cambiar rol de usuarios existentes dentro del tenant.
- UI de `/users` permite gestionar rol y estado de usuarios existentes; no permite cambiar el propio rol ni suspender la propia cuenta.
- Al crear un usuario `Dueno del gimnasio`, la UI asigna automaticamente todas las sucursales existentes.
- `AppShell` resuelve el nombre real de la sucursal activa cuando la pagina no la carga directamente.
- Menu lateral de dueño/soporte/gerencia elimina la entrada redundante a `/reception`; se mantiene acceso directo a `Caja / POS` y `Socios`.
- Beneficios, accesos y automatizaciones quedan integrados al shell lateral unificado.

Verificado:

- Frontend lint.
- Frontend typecheck.
- Backend tests con Java 25.
- `AppShell` recupera sesiones guardadas sin sucursal intentando re-seleccionar la organizacion actual y centraliza la navegacion lateral sin botones superiores redundantes.
- `/inventory`, `/bookings` y `/coaches` quedan integradas al `AppShell`, por lo que conservan menu lateral, marca tenant, perfil y sucursal igual que el resto de la consola.

Verificado localmente:

- Backend: `.\gradlew.bat test --no-daemon` con Java 25.
- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run lint`.
- Frontend: `npm.cmd run build`.
- Docker Compose: `docker compose -f infra/docker-compose.yml up -d --build`.
- Smoke API: Admin GymOS crea gimnasio con `branchCount = 0`, entra en `supportMode`, crea sucursal y `GET /organizations/current/branches` devuelve la nueva sucursal.
- Smoke API: `GET /organizations/{organizationId}/branches` devuelve las sucursales correctas para cada gimnasio seleccionado.
- Frontend local: `GET http://localhost:3000/platform/gymnasiums` devuelve 200.
- `AppShell` resuelve el nombre real de la sucursal activa cuando la pagina no la carga directamente.

## Correccion socios, marca por rol y formularios - 19 de julio de 2026

Implementado:

- `CurrentUser` devuelve marca del tenant activo: `organizationName`, `brandName`, `logoUrl`, `primaryColor`, `accentColor` y `surfaceColor`.
- `TenantThemeProvider` aplica la marca desde la sesion para roles sin permiso administrativo y usa `/auth/me` para refrescar sesiones locales antiguas.
- Migracion `V21__tenant_theme_and_person_names.sql` agrega `person.maternal_last_name`.
- Clientes y usuarios internos usan `lastName` como apellido paterno y `maternalLastName` como apellido materno.
- `/clients` captura fecha de nacimiento como `dd/mm/aaaa`, genero como desplegable `Hombre/Mujer`, plan inicial y metodo de pago.
- El alta de socio crea una venta de membresia inicial con `POST /sales` cuando se selecciona plan; la activacion sigue dependiendo del pago completado.
- `/clients/[clientId]` usa el `AppShell` lateral del sistema y separa datos personales, membresias y actividad.
- Backend valida telefono mexicano en socios con `+52` seguido de 10 digitos y genero `MALE|FEMALE`.

## Correccion catalogo de membresias y acceso multi-sucursal - 19 de julio de 2026

Implementado:

- `POST /api/v1/catalog/membership-plans` crea producto tecnico y plan de membresia en una sola transaccion.
- Migracion `V22__cleanup_orphan_membership_products.sql` marca como borrados productos `MEMBERSHIP_PLAN` huerfanos que no tienen plan ni ventas.
- Migracion `V23__membership_plan_multi_branch_access.sql` agrega `membership_plan.multi_branch_access`.
- `/catalog` muestra un checkbox `Acceso multi-sucursal` al crear planes y etiqueta el alcance en la lista.
- Las consultas de acceso, kiosco, verificacion de autoservicio y detalle de membresias consideran validas las membresias multi-sucursal desde cualquier sucursal del mismo gimnasio.
- Cambiar de plan cancela la membresia anterior aunque se opere desde otra sucursal cuando el plan anterior era multi-sucursal, y activa el nuevo alcance segun el plan destino.
- La portada de recepcion deja de usar colores fijos por modulo y deriva sus tarjetas/metricas de la marca del tenant.

## Reservas por plan, gestion de planes y kiosco con marca - 19 de julio de 2026

Implementado:

- Migracion `V24__resource_allowed_membership_plans.sql` para asociar recursos con planes de membresia permitidos.
- `POST /api/v1/resources` acepta `allowedPlanIds` y valida que correspondan a planes activos.
- `POST /api/v1/bookings` bloquea reservas cuando el socio no tiene una membresia activa permitida para el recurso.
- `/bookings` permite seleccionar planes permitidos al crear un recurso y no falla por falta de permiso `organization.read` en recepcion.
- `/coaches` ya no falla por falta de permiso `organization.read`; usa la marca de sesion cuando la organizacion no es consultable.
- `PATCH /api/v1/catalog/plans/{planId}` permite gestionar planes existentes.
- `/catalog` permite editar planes creados: precio, duracion, gracia, beneficios, estado y alcance multi-sucursal.
- La sesion publica del kiosco devuelve marca del gimnasio y `/kiosk/[terminalCode]` muestra logo/nombre del tenant.
- El dashboard de recepcion usa una banda con color principal, metricas integradas y acciones asimetricas para acercarse al lenguaje visual del menu lateral.

## Correccion gerente y visibilidad multi-sucursal - 20 de julio de 2026

Implementado:

- Migracion `V25__manager_permissions_and_client_branch_visibility.sql`.
- El rol `Gerente` pierde permisos de edicion de datos del gimnasio y sucursales: `organization.update`, `branch.create` y `branch.update`.
- La ruta `/settings/organization` queda fuera de la consola de gerente; el menu no muestra `Datos y sucursales` para ese perfil.
- `client.home_branch_id` guarda la sucursal donde se dio de alta o importo el socio.
- `GET /clients`, `GET /clients/{id}` y `PATCH /clients/{id}` filtran por sucursal activa: plan normal visible solo en sucursal de alta o membresia local; plan multi-sucursal visible en todas las sucursales.

## Correccion revertida: marca por sucursal y seleccion explicita - 20 de julio de 2026

Implementado:

- Migracion `V26__branch_branding_and_owner_branch_selection.sql`.
- Esta decision quedo reemplazada por `V27`: la marca vuelve a vivir solo en el gimnasio y el dueño recibe una sucursal operativa por defecto.
- `AppShell` conserva selector de sucursal operativa para cambiar explicitamente el contexto de trabajo cuando el gimnasio tiene multiples sedes.
- `/reception` se rediseña con franja de marca, comando principal de caja, carril de acciones y panel de turno para reducir la sensacion de tablero plano lleno de tarjetas.

## Base visual y simplificacion de shell - 21 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `AppShell` resuelve la marca visible desde un solo lugar y muestra el logo del gimnasio en el menu lateral cuando el tenant lo tiene configurado.
- La consola de plataforma conserva marca neutral de GymOS; las consolas tenant usan nombre, logo y sucursal activa del gimnasio.
- Se agrego `AppDrawer` como componente comun para mover formularios densos a paneles laterales reutilizables.
- `/platform/gymnasiums` usa `AppDrawer` para alta de gimnasio y alta de sucursal, evitando drawers propios de la pagina.
- `/users` mueve el alta de usuario interno a `AppDrawer` y separa la UI de roles, contexto y tabla en `UsersSections.tsx`; `page.tsx` queda como orquestador de sesion, carga, alta, suspension y cambio de rol.
- `/catalog` mueve nuevo producto, nuevo plan y edicion de plan a `AppDrawer`; ademas separa toolbar, listas, drawers y helpers de formato/draft en archivos del modulo `catalog`.
- `/clients` mueve el registro completo de socio, membresia inicial y pago de entrada a `AppDrawer`; ademas separa busqueda, lista, drawer y helpers de validacion/formato en archivos del modulo `clients`.
- `/settings/organization` mueve edicion de gimnasio, alta de sucursal y edicion de sucursal a `AppDrawer`; la pagina principal queda como resumen de marca, sedes y acceso a equipo.
- `/bookings` mueve nueva reserva y creacion de recurso a `AppDrawer`; la pagina principal queda enfocada en calendario, resumen diario y recursos.
- `/bookings` elimina enhancers DOM de notas/layout; las notas de reserva se muestran desde React en cada tarjeta, el boton de nota queda visible como estado `Ver nota`/`Sin nota` y el detalle diario ocupa el ancho inferior para recepcion, gerencia, dueno y soporte.
- `/bookings` reemplaza el resumen lateral redundante por estado operativo del dia y tarjetas compactas de recursos en dos columnas; si hay muchos recursos, el resumen usa scroll interno invisible para no empujar el calendario.
- `/benefits` mueve nuevo beneficio a `AppDrawer`; la pagina principal queda enfocada en beneficios configurados y estado vacio claro.
- `/clients/[clientId]` mueve edicion de datos personales, renovacion y cambio de plan a `AppDrawer`; la ficha principal queda como lectura, membresias e historial.
- `/inventory` mueve registro de movimientos a `AppDrawer`; la pagina principal queda enfocada en existencias, alertas y movimientos recientes.
- `/automations` mueve nueva regla y notificacion manual a `AppDrawer`; la pagina principal queda enfocada en reglas, estado y notificaciones.
- `/access` queda alineada al `AppShell` unificado, elimina el encabezado visual duplicado y separa su UI en `AccessSections.tsx` para aislar dispositivos, clientes, decisiones, pases y bitacora.
- `/sales/[saleId]`, `/clients/import` y `/onboarding` dejan de usar contenedores propios y renderizan dentro de `AppShell`, manteniendo una sola base visual para pantallas heredadas.
- `/platform/gymnasiums` extrae formularios y validaciones de alta de gimnasio/sucursal a `PlatformGymnasiumForms.tsx`; la pagina principal conserva el maestro-detalle y queda menos acoplada al formulario.
- `/catalog` reutiliza `TextField`, `NumberField`, `TextAreaField` y `SelectField` para producto y plan, eliminando inputs locales duplicados.
- `CatalogHelpers.ts` centraliza el armado de payloads de productos y planes para que `page.tsx` solo orqueste sesion, carga, guardado y estado de drawers.
- `/settings/organization` separa helpers, validaciones, resumen, tarjetas y drawers en `OrganizationSettingsHelpers.ts` y `OrganizationSettingsSections.tsx`.
- `FormFields.tsx` agrega `ColorField` para reutilizar la edicion de marca sin inputs locales por pantalla.
- Los logos cargados en `frontend/public/tenants/*` se tratan como datos locales/runtime y quedan fuera de Git, conservando solo `.gitkeep`.
- `GlobalSessionNav` se reemplaza por `SessionRouteGuard`: conserva la proteccion/redireccion por rol sin introducir una segunda navegacion.
- `AppShell` deja de aceptar `actions` superiores; las paginas tenant ya no declaran botones de encabezado duplicados y la navegacion queda concentrada en el menu lateral.
- Se elimina CSS muerto de `.global-session-nav`, dejando una sola base visual para plataforma, soporte, dueno, gerencia, recepcion y coach.
- Se retiro `AppNavigation`, que ya no tenia consumidores, para mantener un solo modelo de navegacion visible.
- La siguiente etapa recomendada es continuar con `clients/[clientId]` o `clients/page.tsx`, priorizando helpers de estado y formularios compartidos.

## Saneamiento AppShell y navegacion por consola - 22 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `AppShell.tsx` deja de contener las tablas completas de navegacion por rol y delega el modelo declarativo a `frontend/src/components/app-shell-model.ts`.
- El modelo de shell centraliza:
  - secciones visibles por consola (`PLATFORM_OWNER`, `TENANT_OWNER`, `TENANT_MANAGER`, `RECEPTION`, `COACH`);
  - filtrado por permisos;
  - reglas de navegacion segura cuando no existe sucursal activa;
  - marca neutral de plataforma contra marca tenant/soporte;
  - deteccion de ruta activa.
- Se agregan pruebas de frontend para asegurar que:
  - el dueno del gimnasio conserva acceso a metricas, socios, equipo y datos/sucursales;
  - recepcion queda limitada a mostrador y no ve configuracion/equipo;
  - usuarios tenant sin sucursal solo ven enlaces seguros de configuracion;
  - plataforma conserva marca neutral fuera de soporte.
- `AppShell.tsx` queda como orquestador visual y de sesion: carga sucursales, recupera contexto cuando aplica, muestra sidebar, encabezado, modo soporte y banner de sucursal pendiente.

Verificado:

- Frontend: `npm.cmd run test`.
- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run lint`.
- Frontend: `npm.cmd run build`.
- Backend: pruebas focalizadas de arquitectura, membresias y reservas por fecha de negocio.
- Docker: `docker compose -f infra/docker-compose.yml up -d --build`.
- Smoke local: frontend responde en `http://localhost:3000` y backend queda `UP` en `http://localhost:8080/actuator/health`.

## Saneamiento de validaciones, roles y kiosco - 22 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- Se extrajeron las validaciones puras de `FormFields.tsx` hacia `frontend/src/components/form-validation.ts`.
- `FormFields.tsx` conserva la API publica mediante re-exports, pero deja de mezclar componentes React con reglas puras de validacion.
- `UsersRoleHelpers.ts` usa el modulo puro de validacion, lo que permite probar reglas de roles sin cargar JSX ni componentes visuales.
- Se agregan pruebas de reglas de equipo y accesos para asegurar que:
  - ningun usuario tenant pueda crear `Admin GymOS`;
  - el dueno pueda crear roles operativos, pero no otro dueno;
  - el gerente no pueda ver ni administrar duenos ni otros gerentes;
  - Admin GymOS conserve visibilidad y gestion de soporte.
- El kiosco reemplaza `<img>` por `next/image` con `unoptimized`, eliminando la advertencia de lint sin cambiar la ruta de logos tenant.
- Se corrigio la base remota de backend tras el refactor de sales:
  - `common.time` queda expuesto como interfaz publica de Spring Modulith;
  - la prueba de arquitectura de `JdbcClient` refleja que `SalesService` ya no depende de acceso SQL directo;
  - los tests de sales usan los records movidos a `com.gymos.sales.model`;
  - las verificaciones Mockito de eventos/auditoria quedan alineadas con los nuevos servicios.

Verificado:

- Frontend: `npm.cmd run test` con 25 pruebas.
- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run lint`.
- Frontend: `npm.cmd run build`.
- Backend: `./gradlew.bat test` completo. Resultado exitoso; queda warning no bloqueante de apagado de Spring Modulith en pruebas H2 por `event_publication`, recomendado para el bloque backend profundo.

## Saneamiento de paginas operativas grandes - 22 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `/inventory` separa la presentacion en `InventorySections.tsx`:
  - existencias;
  - alertas;
  - resumen de movimiento;
  - tabla de movimientos recientes;
  - drawer de registro de movimiento.
- `inventory/page.tsx` queda como orquestador de sesion, carga, seleccion de producto y envio de movimiento.
- `/wellness` separa formularios y paneles en `WellnessSections.tsx`:
  - alta de profesional;
  - directorio de profesionales;
  - alta de servicio;
  - disponibilidad;
  - resumen de servicios y horarios.
- `wellness/page.tsx` queda como orquestador de sesion, carga, seleccion de profesional y mutaciones.
- `/access` extrae la logica de estado y acciones a `useAccessController.ts`:
  - busqueda de socios;
  - seleccion de dispositivo;
  - evaluacion de acceso;
  - apertura manual;
  - pases temporales;
  - paquetes de visitas;
  - bitacora.
- `access/page.tsx` queda como composicion de secciones, reduciendo el acoplamiento entre flujo y UI.

Verificado:

- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run lint`.

## Saneamiento CRM de plataforma y POS - 22 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `/platform/gymnasiums` extrae la orquestacion a `usePlatformGymnasiumsController.ts`:
  - carga de gimnasios;
  - carga de sucursales por gimnasio seleccionado;
  - busqueda y seleccion de tenant;
  - entrada en modo soporte;
  - seleccion de sucursal;
  - alta de gimnasio;
  - alta de sucursal;
  - suspension/reactivacion de gimnasio.
- `platform/gymnasiums/page.tsx` queda como composicion de `AppShell`, lista, detalle y drawers.
- Se corrigieron textos heredados con mojibake en mensajes de confirmacion del CRM de plataforma.
- `/pos` separa la presentacion en `PosSections.tsx`:
  - grilla de productos;
  - ventas recientes;
  - busqueda/seleccion de cliente;
  - carrito, pago y registro de venta.
- `pos/page.tsx` queda como orquestador de sesion, carga de catalogo/ventas, carrito y guardado de venta.
- Se corrigieron textos visibles heredados con mojibake en Caja/POS.

Verificado:

- Frontend: `npm.cmd run typecheck`.
- Frontend: `npm.cmd run lint`.

## Saneamiento reservas y socios - 22 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `/bookings` extrae la orquestacion a `useBookingsController.ts`:
  - carga de organizacion, sucursales, recursos, reservas y planes;
  - busqueda de socios para reservar;
  - creacion y cancelacion de reservas;
  - creacion de recursos;
  - calculo de calendario, dia seleccionado, horarios y estado de drawers.
- `bookings/page.tsx` queda como composicion de `AppShell`, panel de resumen, calendario, detalle diario y drawers.
- `/clients` extrae la orquestacion a `useClientsController.ts`:
  - carga y busqueda de socios;
  - carga de planes activos;
  - validacion y normalizacion del alta;
  - registro de socio y venta de membresia inicial.
- `clients/page.tsx` queda como composicion de busqueda, toolbar, listado y drawer de alta.
- `/clients/[clientId]` se divide en:
  - `useClientDetailController.ts` para carga, edicion, renovacion y cambio de plan;
  - `ClientDetailHelpers.ts` para validaciones, normalizacion y formateo;
  - `ClientDetailSections.tsx` para ficha, historial, resumen, timeline y drawers.
- `clients/[clientId]/page.tsx` queda como composicion de alto nivel y deja de mezclar UI, estado remoto y reglas visibles.

Verificado:

- Frontend: `npm.cmd run typecheck`.

## Saneamiento backend de membresias y ventas - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `MembershipRepository` queda como fachada publica del modulo `memberships` y delega responsabilidades a repositorios internos especializados:
  - `MembershipClientRepository`;
  - `MembershipQueryRepository`;
  - `MembershipPlanLookupRepository`;
  - `MembershipEventRepository`;
  - `MembershipCommandRepository`;
  - `MembershipRowMappers`.
- `SalesRepository` queda como fachada publica del modulo `sales` y delega responsabilidades a repositorios internos especializados:
  - `SalesCatalogLookupRepository`;
  - `SalesClientLookupRepository`;
  - `SalesRecordLookupRepository`;
  - `SalesIdempotencyLookupRepository`;
  - `SalesPaymentLookupRepository`;
  - `SalesRefundLookupRepository`;
  - `SalesMembershipLookupRepository`;
  - `SalesRowMappers`.
- `SalesService` deja de construir detalles y reportes directamente:
  - `SaleDetailQuery` concentra detalle de venta y ventas recientes;
  - `SalesReportingQuery` concentra validacion de rango y armado del reporte de ventas.
- Se conservaron los contratos publicos usados por servicios y pruebas para evitar cambios funcionales en ventas, pagos, reembolsos, membresias iniciales o reportes.
- El SQL queda agrupado por responsabilidad sin mover propiedad de datos entre modulos.

Verificado:

- Backend: `./gradlew.bat test` completo. Resultado exitoso; persiste warning no bloqueante de shutdown de Spring Modulith en pruebas H2 por `event_publication`.

## Saneamiento de fachada de ventas - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `SalesService` queda como fachada estable para controladores HTTP y API interna `SalesOperations`.
- Los flujos mutantes de POS se movieron a `SalesPointOfSaleService`: crear venta, agregar pago, validar/rechazar pago, subir comprobante y procesar reembolso.
- `SalesAccessGuard` centraliza las validaciones de autenticacion y sucursal activa para ventas.
- Se agrego una prueba de arquitectura para evitar que la fachada vuelva a absorber dependencias de flujos de POS.

## Saneamiento de pagos en ventas - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `SalesPaymentService` queda como fachada interna del flujo de pagos y delega en servicios especializados.
- `SalesPaymentRecordingService` concentra registro de pagos manuales, idempotencia de pago y evento `PaymentRecordedV1`.
- `SalesPaymentReceiptService` concentra carga de comprobantes, storage y auditoria de recibos.
- `SalesPaymentLifecycleService` concentra validacion/rechazo, recalculo financiero, auditoria y evento `PaymentRejectedV1`.
- Se agrego una prueba de arquitectura para evitar que la fachada de pagos vuelva a depender directamente de repositorios, storage, auditoria o publicador de eventos.

## Saneamiento backend de servicios application - 23 de julio de 2026

Implementado en rama `feature/code-simplification-rebasion-and-refactor`:

- `MembershipService` queda como fachada de API/application y delega en servicios especializados:
  - `MembershipQueryService` para consulta y aislamiento por organizacion/sucursal;
  - `MembershipRenewalService` para renovacion;
  - `MembershipPlanChangeService` para cambio de plan;
  - `MembershipPaymentCompletedListener` para consumo de `PaymentCompletedV1`;
  - `MembershipResultMapper` para conversion de resultados application/API interna;
  - `MembershipAccessGuard` para validacion uniforme de principal y sucursal.
- `MembershipSalesService` reemplaza el metodo con muchos parametros por un `MembershipSaleDraft` interno, manteniendo intacta la creacion de venta/pago/eventos de renovacion y cambio de plan.
- `SalesLookupService` centraliza busqueda de venta, pago y validacion de sucursal para pagos y reembolsos.
- `SalesPaymentService` y `SalesRefundService` reducen duplicacion de lookup/guard sin cambiar reglas de negocio, idempotencia ni auditoria.
- Se actualizaron pruebas unitarias afectadas por los nuevos constructores internos.

Verificado:

- Backend: `./gradlew.bat test` completo. Resultado exitoso; persiste warning no bloqueante de shutdown de Spring Modulith en pruebas H2 por `event_publication`.
