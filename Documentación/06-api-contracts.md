# 6. Contratos API

Base path: `/api/v1`

## 6.1 Auth

- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/forgot-password`
- `POST /auth/reset-password`
- `GET /auth/me`
- `POST /auth/select-branch`

`CurrentUser` devuelve tambien contexto visual minimo del tenant activo:

- `organizationName`
- `brandName`
- `logoUrl`
- `primaryColor`
- `accentColor`
- `surfaceColor`

Estos campos permiten que recepcion, gerencia, dueno y coaches apliquen marca del gimnasio sin necesitar permisos administrativos de `GET /organizations/current`.

## 6.1.1 Organizaciones y sucursales

- `POST /organizations`
- `GET /organizations`
- `GET /organizations/{id}`
- `PATCH /organizations/{id}`
- `POST /organizations/{id}/branches`
- `GET /organizations/{id}/branches`
- `GET /branches/{id}`
- `PATCH /branches/{id}`

Nota implementada Hito 7.6:

- `GET /organizations/current`
- `PUT /organizations/current`
- `GET /organizations/current/branches`
- `POST /organizations/current/branches`
- `PUT /organizations/current/branches/{branchId}`

`POST /organizations/current/branches` crea una nueva sucursal activa para la organizacion actual y asigna acceso al administrador que la crea. Requiere permiso `branch.create`. Esto habilita gimnasios con multiples sucursales desde la configuracion administrativa.

La marca se configura exclusivamente en la organizacion/gimnasio (`brandName`, `logoUrl`, `primaryColor`, `accentColor`, `surfaceColor`). Las sucursales no aceptan marca propia y heredan siempre el logo y colores del gimnasio.

Nota implementada Hito 7.9:

- `POST /organizations` permite crear un gimnasio/tenant completo sin forzar una sucursal inicial.
- Los campos legacy `firstBranchName`, `firstBranchCode` y `firstBranchPhone` quedan opcionales por compatibilidad.
- El flujo recomendado para Admin GymOS es:
  1. Crear gimnasio cliente con datos comerciales, contacto y marca base.
  2. Entrar al gimnasio en modo soporte.
  3. Crear una o varias sucursales con `POST /organizations/current/branches`.
  4. Crear usuarios del cliente en `/users/internal`.
- `GET /organizations` devuelve todos los gimnasios para usuarios con `platform.admin`; usuarios tenant siguen viendo solo su organizacion accesible.
- `GET /organizations/{organizationId}/branches` devuelve las sucursales del gimnasio seleccionado para `platform.admin`, sin cambiar la organizacion ni sucursal activa de la sesion.
- La organizacion tecnica base del usuario Admin GymOS no se lista como gimnasio cliente en el CRM de plataforma.
- En `supportMode`, `GET /organizations/current/branches` permite a Admin GymOS ver las sucursales del gimnasio seleccionado aunque no sea trabajador del tenant.

Request:

```json
{
  "name": "Sucursal Norte",
  "code": "NORTE",
  "timezone": "America/Mexico_City",
  "phone": "+520000000000"
}
```

Errores:

- `BRANCH_CODE_ALREADY_EXISTS`
- `AUTHENTICATION_REQUIRED`
- `PERMISSION_DENIED`

## 6.1.1.1 Usuarios internos, roles y accesos

- `GET /users/roles`
- `GET /users/internal`
- `POST /users/internal`
- `PATCH /users/internal/{userId}/status`
- `PATCH /users/internal/{userId}/role`

Permisos:

- `user.read` para listar roles y usuarios internos.
- `user.manage` para crear usuarios internos, cambiar roles y suspender/reactivar cuentas.

`POST /users/internal` crea persona, cuenta de usuario, rol y accesos a
sucursales dentro de la organizacion actual. Cuando el rol es `Coach`, acepta
ademas `coachSpecialties` (hasta 30 valores de 80 caracteres) y `coachBio`
(hasta 4,000 caracteres), y crea el perfil operativo en la misma transaccion.
No devuelve contrasenas ni hashes.

`PATCH /users/internal/{userId}/status` cambia el estado de una cuenta interna. Estados validos: `ACTIVE`, `SUSPENDED`, `INACTIVE`. Una cuenta suspendida o inactiva no puede iniciar sesion. No se permite suspender la propia cuenta.

`PATCH /users/internal/{userId}/role` cambia el rol principal de una cuenta interna dentro del gimnasio actual. No se permite cambiar el propio rol ni asignar roles con `platform.admin` desde contexto tenant o soporte.

Validaciones de contacto:

- RFC de organizacion: 4 letras, 6 numeros y 3 caracteres alfanumericos (`AAAA000000XXX`).
- Telefono de organizacion, sucursal y usuario interno: formato mexicano `+52` seguido de 10 digitos.
- Frontend normaliza telefonos de 10 digitos anteponiendo `+52`; backend rechaza formatos invalidos.

Request:

```json
{
  "firstName": "Ana",
  "lastName": "Lopez",
  "maternalLastName": "Perez",
  "email": "ana@example.com",
  "phone": "+520000000000",
  "username": "ana@example.com",
  "temporaryPassword": "Temporal123!",
  "roleId": "uuid",
  "branchIds": ["uuid"]
}
```

`firstName`, `lastName` y `maternalLastName` representan nombre, apellido paterno y apellido materno. `lastName` se conserva como apellido paterno por compatibilidad con contratos anteriores.

Roles semilla Hito 7.6:

- `Administrador GymOS`
- `Dueno del gimnasio`
- `Recepcion`
- `Coach`
- `Operacion`

Errores:

- `ROLE_NOT_AVAILABLE`
- `BRANCH_NOT_AVAILABLE`
- `USER_ALREADY_EXISTS`
- `AUTHENTICATION_REQUIRED`
- `PERMISSION_DENIED`

## 6.1.1.2 Roles tenant y estado comercial

Diferencia de roles:

- `Dueno del gimnasio`: cliente directo del SaaS. Administra su tenant completo, sucursales, usuarios, marca, catalogo y metricas de negocio. No administra otros gimnasios ni la plataforma GymOS.
- `Gerente`: delegado operativo del gimnasio. Gestiona operacion diaria, equipo autorizado, inventario, agenda, recepcion y reportes permitidos. No administra plataforma, datos fiscales/comerciales del gimnasio ni altas/ediciones de sucursales.

Estado comercial de gimnasio cliente:

- `PATCH /organizations/{organizationId}/status`
- Permiso: `platform.admin`.
- Estados validos: `ACTIVE`, `SUSPENDED`, `INACTIVE`.
- Usuarios tenant de una organizacion `SUSPENDED` o `INACTIVE` no pueden iniciar sesion ni refrescar sesion.
- Admin GymOS conserva acceso para soporte y reactivacion.
- El frontend debe solicitar confirmacion explicita antes de suspender o reactivar un gimnasio cliente.

## 6.1.2 Kiosco de autoservicio

- `POST /self-service/kiosk-sessions` — requiere JWT y
  `selfservice.session.launch`; selecciona el perfil lógico de la sucursal.
- `POST /self-service/sessions/{sessionId}/verify`
- `GET /self-service/sessions/{sessionId}/summary`
- `GET /self-service/sessions/{sessionId}/membership-options`
- `POST /self-service/sessions/{sessionId}/membership-enrollments`
- `POST /self-service/sessions/{sessionId}/membership-renewals/{membershipId}`
- `POST /self-service/sessions/{sessionId}/membership-change-requests/{membershipId}`
- `PATCH /self-service/sessions/{sessionId}/contact-data`
- `GET /self-service/sessions/{sessionId}/autopay/{membershipId}/terms`
- `GET /self-service/sessions/{sessionId}/autopay/{membershipId}`
- `POST /self-service/sessions/{sessionId}/autopay/{membershipId}/setup`
- `POST /self-service/sessions/{sessionId}/autopay/{membershipId}/revoke`
- `POST /self-service/sessions/{sessionId}/end`
- `POST /self-service/sessions/{sessionId}/reset` — limpia identidad y datos
  visibles para el siguiente socio, conservando sólo el modo kiosco.
- `GET /self-service/sessions/{sessionId}/wellness/professionals`
- `GET /self-service/sessions/{sessionId}/wellness/professionals/{professionalId}/services`
- `GET /self-service/sessions/{sessionId}/wellness/professionals/{professionalId}/availability`
- `POST /self-service/sessions/{sessionId}/wellness/appointments` — sólo
  acepta servicios activos con costo cero.

Los endpoints de autoservicio sólo exponen acciones permitidas para clientes en una sesión temporal de kiosco. Las reglas de pagos, membresías, beneficios y permisos se validan en backend.

La creación inicial ya no es pública. Recepción abre el modo kiosco desde su
sesión y el frontend elimina inmediatamente sus tokens operativos. El código
`KIOSK-MAIN` permanece únicamente como una etiqueta/configuración lógica
interna, no como identidad de computadora ni credencial. La ruta pública
heredada por `terminalCode` fue eliminada: el único inicio válido requiere la
sesión autenticada de recepción.

`autopay/setup` exige la versión vigente del consentimiento, aceptación
explícita y llave idempotente. Devuelve una URL de Stripe Checkout en modo
`setup`; GymOS no recibe PAN ni CVV. El estado sólo cambia a `ACTIVE` después
del webhook firmado y de consultar autoritativamente Session, SetupIntent y
PaymentMethod. `autopay/revoke` impide nuevas ocurrencias e intenta cancelar
PaymentIntents iniciados que aún sean cancelables.

El modo de navegador dura como máximo doce horas. Después de verificar a un
socio, tres minutos de inactividad limpian su identidad y dejan la pantalla
lista para el siguiente; no se asigna ningún identificador a la computadora.

## 6.1.3 Configuración de cobros

- `GET /payment-settings` — lectura efectiva para roles operativos autorizados.
- `PUT /payment-settings` — dueño, gerente autorizado o soporte configura:
  `receptionManualTerminalEnabled`, `receptionMercadoPagoEnabled`,
  `mercadoPagoTerminalId`, `stripeSelfCheckoutEnabled` y
  `stripeRecurringEnabled`.

Mercado Pago o Stripe no pueden habilitarse antes de vincular su cuenta. La
terminal manual de recepción puede operar sin integración y genera pagos
pendientes de validación. Stripe pertenece al self checkout y a la
domiciliación del socio. Gerencia puede activar o desactivar estas modalidades
si la cuenta ya está vinculada, pero no puede crear, sustituir ni verificar la
cuenta Stripe.

La sesion publica de kiosco usa logo, nombre visible y colores del gimnasio al que pertenece el terminal. La sucursal vinculada define el contexto operativo del kiosco, no una marca visual propia.

El frontend abre `/kiosk/launch` desde recepción, solicita la sesión limitada y
la entrega a una pestaña nueva mediante almacenamiento efímero del navegador.
La credencial no se coloca en la URL y se borra al consumirla. La pestaña
administrativa conserva recepción; la del kiosco queda aislada.

Nota implementada Hito 3: las acciones de renovacion y cambio de plan reciben `{membershipId}` en la ruta concreta:
- `POST /self-service/sessions/{sessionId}/membership-enrollments` recibe
  `{ "planId": "uuid", "idempotencyKey": "..." }` y crea una venta/pago
  pendiente antes de abrir Stripe Checkout;
- `POST /self-service/sessions/{sessionId}/membership-renewals/{membershipId}`
- `POST /self-service/sessions/{sessionId}/membership-change-requests/{membershipId}`

Cambio Hito 3.5 final:
- `POST /self-service/sessions/{sessionId}/verify` ahora recibe `{ "identifier": "email o telefono", "birthDate": "YYYY-MM-DD" }`.
- Contrato anterior: `{ "identifier": "email o telefono" }`.
- Razon: evitar verificacion debil y reducir riesgo de enumeracion de clientes.
- Impacto frontend: `/kiosk/reception` solicita fecha de nacimiento y envía ambos campos.

## 6.2 Personas

- `POST /persons`
- `GET /persons/{id}`
- `PATCH /persons/{id}`
- `POST /persons/{id}/photo`
- `PATCH /persons/{id}/status`

## 6.3 Clientes

- `POST /clients`
- `GET /clients`
- `GET /clients/{id}`
- `PATCH /clients/{id}`
- `PATCH /clients/{id}/status`
- `GET /clients/{id}/timeline`
- `GET /clients/{id}/summary`

Contrato implementado para alta/edicion de socio:

- `firstName`: nombre.
- `lastName`: apellido paterno.
- `maternalLastName`: apellido materno opcional.
- `birthDate`: obligatorio en alta, edición e importación; usa ISO `YYYY-MM-DD`
  en API y el frontend lo captura como `dd/mm/aaaa`. Una fecha futura se rechaza
  como error de negocio y nunca debe terminar en un error 500.
- `gender`: solo `MALE` o `FEMALE`.
- `phone`: formato `+52` seguido de 10 digitos.

El alta de socio en frontend puede contratar un plan después del `POST /clients`
mediante `POST /membership-enrollments`. La activación de membresía sigue
dependiendo de que el pago quede completado.

La respuesta resumida de `GET /clients` incluye `currentPlanName` y
`membershipStatus`. La vigencia se calcula al consultar: una membresía
`ACTIVE` dentro de `grace_ends_on` se expone como `ACTIVE`; si su gracia ya
terminó se expone como `EXPIRED` aunque el job de expiración aún no haya
actualizado la fila. Las membresías canceladas no se presentan como plan actual.

Autorización de alta:

- `Dueno del gimnasio`, `Gerente` y `Recepcion` reciben `client.read`,
  `client.create` y `client.update` desde Flyway;
- `POST /clients` requiere `client.create` y sucursal activa;
- después de aplicar V57 es necesario renovar la sesión para que el JWT refleje
  los permisos reparados.

Visibilidad por sucursal:

- `POST /clients` y `POST /clients/imports` guardan la sucursal activa como `home_branch_id`.
- `GET /clients`, `GET /clients/{id}` y `PATCH /clients/{id}` requieren sucursal activa y solo operan sobre socios visibles desde esa sucursal.
- Un socio con plan no multi-sucursal se ve en su sucursal de alta y en sucursales donde tenga membresia activa local.
- Un socio con membresia activa de plan `multiBranchAccess=true` se ve desde todas las sucursales activas del gimnasio sin duplicar registros.

Autenticacion y sucursal activa:

- `Dueno del gimnasio`, gerente, recepcion y coach reciben la primera sucursal activa permitida cuando existe para evitar pantallas operativas sin contexto.
- `POST /auth/select-branch` establece la sucursal operativa para flujos locales como recursos, socios, inventario, reservas, caja y recepcion.

## 6.4 Productos

- `GET /catalog/products`
- `GET /catalog/products/search`
- `GET /catalog/products/by-barcode/{barcode}`
- `POST /catalog/products`
- `GET /catalog/products/{id}`
- `PUT|PATCH /catalog/products/{id}`
- `PATCH /catalog/products/{id}/status`
- `POST /catalog/products/{id}/activate`
- `POST /catalog/products/{id}/deactivate`
- `DELETE /catalog/products/{id}`
- `POST /catalog/products/{id}/image`
- `DELETE /catalog/products/{id}/image`
- `GET|POST /catalog/categories`
- `PATCH|DELETE /catalog/categories/{id}`

`GET /catalog/products/search` devuelve `items`, `page`, `size`, `totalElements` y `totalPages`. Acepta búsqueda por nombre, SKU, código de barras y etiqueta, además de filtros por tipo, categoría, estado y seguimiento de inventario.

`GET /catalog/products/by-barcode/{barcode}` hace una consulta exacta de un
producto activo de la sucursal y excluye planes. El POS lo usa como fallback
cuando el código escaneado no está en la página local.

`unitOfMeasure` ya no es texto libre en el alta web. Para productos físicos usa
uno de los valores canónicos:

`PIECE`, `BOTTLE`, `CAN`, `PACKAGE`, `BOX`, `BAG`, `SACHET`, `SERVING`,
`TABLET`, `CAPSULE`, `MILLILITER`, `LITER`, `GRAM`, `KILOGRAM`.

El backend rechaza cualquier valor no reconocido con
`PRODUCT_UNIT_INVALID`. Los cargos y servicios no conservan unidad física.

## 6.5 Planes

- `GET /catalog/plans`
- `POST /catalog/plans`
- `POST /catalog/membership-plans`
- `PATCH /catalog/plans/{planId}`
- `GET /plans/{id}`
- `PATCH /plans/{id}`
- `POST /plans/{id}/archive`

`POST /catalog/membership-plans` es el contrato recomendado para altas desde frontend. Crea de forma atomica el producto tecnico `MEMBERSHIP_PLAN` y su `membership_plan`; si falla la validacion o persistencia, no debe quedar un producto de membresia sin duracion. Acepta beneficios como texto libre y backend los normaliza a JSON:

```json
{
  "sku": "MENSUAL",
  "name": "Membresia mensual",
  "description": "Acceso general",
  "priceCents": 70000,
  "currency": "MXN",
  "durationDays": 30,
  "graceDays": 3,
  "benefits": "Acceso general, clases grupales",
  "multiBranchAccess": false,
  "status": "ACTIVE"
}
```

`multiBranchAccess=true` marca el plan como valido para todas las sucursales activas del gimnasio. La venta y la membresia conservan la sucursal donde se originaron, pero las consultas de socios, acceso, kiosco y detalle de membresias aceptan esa membresia desde cualquier sucursal del mismo tenant.

`PATCH /catalog/plans/{planId}` permite gestionar planes ya creados sin cambiar el SKU: nombre, descripcion, precio, moneda, duracion, gracia, beneficios, `multiBranchAccess` y estado.

El SKU es único dentro del gimnasio, no por sucursal. Si `POST /catalog/plans`
recibe en otra sucursal el SKU de un plan existente, vincula ese mismo plan a la
sucursal activa y devuelve el plan; no crea un duplicado. Si el SKU pertenece a
otro tipo de producto, responde `PRODUCT_SKU_ALREADY_EXISTS`.

Errores relevantes:

- `PRODUCT_SKU_ALREADY_EXISTS`
- `INVALID_PLAN_BENEFITS`
- `VALIDATION_ERROR`

## 6.6 Membresías

- `GET /clients/{clientId}/memberships`
- `GET /memberships/{id}`
- `GET /memberships/{id}/periods`
- `POST /membership-enrollments`
- `POST /memberships/{id}/renew`
- `POST /memberships/{id}/cancel`
- `PATCH /memberships/{id}/auto-renew`
- Nota implementada: `GET /clients/{clientId}/memberships` y `GET /memberships/{id}` exponen membresias por cliente y detalle por membresia.
- Nota implementada: `POST /membership-enrollments` crea la venta y el primer
  periodo mediante el flujo específico de contratación.
- Nota implementada: `POST /memberships/{id}/renew` crea una venta de renovación,
  registra pago manual y extiende la membresía operativa cuando el pago queda
  completado.
- Nota implementada: `POST /memberships/{id}/change-plan` crea una venta de
  cambio, exige pago completado y actualiza plan y periodo sin crear una segunda
  membresía activa.
- Nota implementada: si la membresia origen o destino pertenece a un plan con `multiBranchAccess`, el acceso del socio se agrega o remueve para todas las sucursales por regla de consulta, sin duplicar registros por sucursal.

La activación ocurre como consecuencia de pago completado.

## 6.7 Beneficios

- `POST /benefits`
- `GET /benefits`
- `POST /clients/{clientId}/benefits`
- `PATCH /clients/{clientId}/benefits/{id}`
- `POST /clients/{clientId}/benefits/{id}/verify`

## 6.8 Ventas

- `GET /sales`
- `GET /sales/reports/summary`
- `POST /sales`
- `GET /sales/{id}`
- `POST /sales/{id}/items` (pendiente)
- `DELETE /sales/{id}/items/{itemId}` (pendiente)
- `POST /sales/{id}/apply-benefit` (pendiente)
- `POST /sales/{id}/checkout` (pendiente)
- `POST /sales/{id}/cancel` (pendiente)
- `POST /sales/{id}/refund` (pendiente)

`POST /sales` es exclusivo para productos y servicios cotidianos. Si contiene
un `MEMBERSHIP_PLAN`, responde `SALE_MEMBERSHIP_OPERATION_REQUIRED`; las ventas
de planes se crean mediante los endpoints específicos de membresía y siguen
formando parte de reportes, pagos y movimientos de caja.

## 6.9 Pagos

- `POST /sales/{saleId}/payments`
- `POST /sales/payments/{paymentId}/validate`
- `POST /sales/payments/{paymentId}/receipt`
- `POST /sales/payments/{paymentId}/reject`
- `POST /payments/{id}/approve` (pendiente)
- `GET /payments` (pendiente)
- `GET /payments/{id}` (pendiente)

## 6.10 Inventario

- `GET /inventory`
- `GET /inventory/{productId}`
- `POST /inventory/movements`
- `GET /inventory/movements`
- `GET /inventory/low-stock`

Nota implementada Hito 4:
- `GET /inventory` lista existencias de productos fisicos por sucursal activa.
- `GET /inventory/{productId}` muestra existencias de un producto fisico.
- `POST /inventory/movements` registra entradas, salidas, ajustes, mermas, devoluciones y ventas manuales; evita existencias negativas.
- `GET /inventory/movements` lista movimientos recientes, con filtro opcional por producto.
- `GET /inventory/low-stock` lista productos bajo minimo.
- Cuando una venta queda pagada, `inventory` consume `PaymentCompletedV1` y descuenta automaticamente productos fisicos con movimiento `SALE`.
- El descuento automatico es idempotente por organizacion, sucursal, venta y producto; reentregar el evento no duplica movimientos ni reduce stock de nuevo.

## 6.11 Recursos y reservas

- `POST /resources`
- `GET /resources`
- `PUT /resources/{id}`
- `DELETE /resources/{id}`
- `GET /resources/{id}/availability`
- `POST /bookings`
- `GET /bookings`
- `GET /bookings/{id}`
- `POST /bookings/{id}/confirm`
- `POST /bookings/{id}/check-in`
- `POST /bookings/{id}/complete`
- `POST /bookings/{id}/cancel`

Nota implementada Hito 4:
- `POST /resources` crea recursos activos con capacidad, duracion de slot y horario.
- `POST /resources` acepta `allowedPlanIds`; si se envian planes, solo socios con membresia activa de alguno de esos planes pueden reservar el recurso.
- `PUT /resources/{resourceId}` actualiza nombre, capacidad, duracion, horario, notas y planes permitidos del recurso. Requiere `resource.manage`.
- `DELETE /resources/{resourceId}` elimina logicamente el recurso. Requiere `resource.manage` y responde `RESOURCE_HAS_ACTIVE_BOOKINGS` si existen reservas activas.
- `GET /resources/{resourceId}/availability?date=YYYY-MM-DD` devuelve slots con capacidad, reservados y disponibilidad.
- `POST /bookings` crea reserva en estado `RESERVED`.
- `POST /bookings` responde `BOOKING_PLAN_NOT_ALLOWED` cuando el recurso esta restringido y el socio no tiene una membresia permitida.
- Acciones implementadas: `confirm`, `check-in`, `complete` y `cancel`.
- UX implementada en `/bookings`: la reserva se crea buscando socio por texto, seleccionando recurso, dia y hora de inicio; la hora final se calcula con `slotMinutes` del recurso.
- UX implementada en `/bookings`: la creacion de recurso captura duracion manual y horario por dia operativo. Mientras no exista tabla dedicada de horario por recurso/dia, la UI serializa ese horario en `resource.notes` y mantiene `opensAt`/`closesAt` como rango operativo agregado para compatibilidad con la API vigente.
- Las reservas incluidas en el plan no generan cobro. Cuando un recurso exige
  pago, el flujo crea pago pendiente y puede abrir Stripe Checkout; el webhook
  confirmado actualiza la reserva y su movimiento de caja de forma idempotente.

## 6.12 Accesos

Operación lógica:

- `GET /access/devices` lista puntos por sucursal;
- `POST /access/devices` crea un punto y, si es productivo, exige exactamente un
  `requiredBenefitId` de tipo `ACCESS`;
- `GET|PUT /access/devices/{deviceId}/benefits` consulta o reemplaza el único
  derecho requerido de un punto productivo;
- `GET /access/logs` lista hasta 100 eventos recientes de la sucursal;
- `POST /access/requests` evalúa socio/identificador mediante
  `EligibilityPolicy` y registra la decisión;
- `POST /access/commands/open` exige `access.manual.open`, `deviceCode` y motivo;
  registra `SIMULATED_OPEN` mientras no exista adapter físico;
- `POST /access/commands/temporary` crea una autorización limitada por tiempo y
  usos.

Control plane de acceso físico, bajo `/access/physical-integration`:

- `GET|PUT /` consulta/configura proveedor, conexión y mapeos por sucursal;
- `GET /clients/{clientId}` muestra derechos y estado deseado;
- `PUT /clients/{clientId}/link` guarda una referencia opaca de persona;
- `POST /clients/{clientId}/synchronize` y `POST /reconcile` encolan recuperación
  manual; el flujo normal reacciona a eventos de dominio;
- `PUT|DELETE /devices/{deviceId}/link` administra el recurso externo;
- `POST /connector/activation` genera un código de un solo uso y
  `POST /connector/revoke` revoca su credencial;
- `GET /candidates`, `PUT /candidates/{id}/match` y
  `POST /candidates/{id}/reject` permiten conciliación humana segura.

Las mutaciones anteriores y los candidatos requieren
`access.physical.manage`. Recepción no recibe ese permiso.

Contrato máquina-a-máquina independiente, bajo `/api/v1/physical-connectors`:

- `POST /activate` intercambia `connectorKey + activationCode` por un secreto
  mostrado una sola vez;
- `POST /heartbeat` reporta salud/versión;
- `POST /jobs/claim`, `/jobs/{id}/complete`, `/jobs/{id}/fail` y
  `/jobs/{id}/lease` implementan entrega durable con lease; `claim` devuelve un
  `claimToken` nuevo que es obligatorio para completar, fallar o extender ese
  intento y deja inválido al worker anterior después de un reclaim;
- `POST /events` ingresa eventos idempotentes por `externalEventId`;
- `POST /person-candidates` ingresa referencias externas para revisión.

Excepto `/activate`, estas rutas autentican con
`X-GymFort-Connector-Key` y `X-GymFort-Connector-Secret`. Una credencial ausente,
inválida o revocada devuelve `401 CONNECTOR_AUTH_INVALID`. Los códigos y secretos
se almacenan únicamente como hash y nunca forman parte de logs o respuestas de
lectura.

## 6.13 Coaches y rutinas

Contratos base de Hito 5:

- `POST /coaches`
- `GET /coaches`
- `POST /workouts`
- `GET /workouts`
- `GET /workouts/{id}`
- `POST /workouts/{id}/versions`
- `POST /workouts/{id}/assign`
- `GET /clients/{clientId}/workouts`

Nota implementada Hito 5:
- `GET /coaches` y `POST /coaches` operan por organizacion y sucursal activa.
- `POST /workouts` crea la rutina y su primer borrador.
- `POST /workouts/{id}/versions` agrega un borrador; publicar lo vuelve
  inmutable sin modificar asignaciones históricas.
- `POST /workouts/{id}/assign` asigna una version concreta o la ultima version publicada a un cliente activo.
- `GET /reports/coaches` devuelve conteos base: coaches, rutinas, asignaciones activas y asignaciones de ultimos 30 dias.

Contratos implementados en los cierres `84a5fd3` y `8162814`:

- `GET /coaches/profiles`
- `PUT /coaches/profiles/{coachUserId}`
- `GET|POST /exercises`
- `GET|PUT /exercises/{exerciseId}`
- `PATCH /exercises/{exerciseId}/status`
- `GET|POST|DELETE /exercises/{exerciseId}/aliases`
- `GET|POST|DELETE /exercises/{exerciseId}/media`
- `GET|POST /workouts`
- `GET|PUT /workouts/{workoutId}`
- `POST /workouts/{workoutId}/assign`
- `POST /workouts/{workoutId}/duplicate`
- `POST /workouts/{workoutId}/publish`
- `GET /workouts/{workoutId}/versions`
- `GET /workout-versions/{versionId}`
- `GET|POST /workout-assignments`
- `GET /workout-assignments/{assignmentId}`
- `POST /workout-assignments/{assignmentId}/replace`
- `PATCH /workout-assignments/{assignmentId}/status`
- `GET /workout-assignments/{assignmentId}/pdf`
- `GET /reports/coaches/workout-assignments/summary`
- `GET /reports/coaches/workout-assignments/trend`
- `GET /reports/coaches/workout-assignments/export`

El catálogo usa `page` y `size`. El resumen de asignaciones acepta `from`, `to`,
`coachId`, `clientId`, `page` y `size`; devuelve KPIs del conjunto filtrado,
periodo anterior comparable y detalle paginado. El CSV exporta el conjunto
filtrado y rechaza más de 10,000 filas. La tendencia acepta `weeks` entre 4 y
52, `to`, `coachId` y `clientId`; devuelve una serie ordenada con
`weekFrom`, `weekTo`, `assignments`, `uniqueClients` y `firstWorkouts`,
incluyendo ceros en semanas sin actividad.

`GET /coaches/profiles` agrega por `userId` las sucursales visibles del actor.
`PUT /coaches/profiles/{coachUserId}` recibe `specialties[]`, `bio`, estado
`ACTIVE|INACTIVE` y `branchIds[]`. Un perfil activo requiere al menos una
sucursal administrable; el cambio sincroniza las filas operativas `coach` y el
acceso del usuario sólo dentro del alcance del actor. Estos contratos se
consumen desde `/users`, no como una administración duplicada en `/coaches`.

El rol `Dueno del gimnasio` puede usar `GET /coaches`, los contratos de métricas
y los contratos de perfil desde Gestión del equipo. No recibe `exercise.*`,
`workout.read.*`, `workout.manage.*`, `workout.assign`,
`workout.pdf.download` ni `coach.manage`. El Coach conserva sus permisos
operativos de alcance propio.

Crear una asignación acepta
`Idempotency-Key`; reutilizarla con otro payload responde
`IDEMPOTENCY_KEY_REUSED`. Solo se asignan versiones publicadas y el PDF se descarga
por `assignmentId`, nunca desde el borrador actual.

Errores principales: `COACH_NOT_ACTIVE`, `COACH_PROFILE_REQUIRED`,
`COACH_PROFILE_NOT_FOUND`, `COACH_BRANCH_REQUIRED`,
`COACH_BRANCH_OUTSIDE_SCOPE`, `REPORT_WEEKS_INVALID`,
`EXERCISE_DUPLICATE`, `SYSTEM_EXERCISE_IMMUTABLE`,
`EXERCISE_OUTSIDE_OWNER_SCOPE`, `EXERCISE_MEDIA_INVALID`,
`WORKOUT_OUTSIDE_OWNER_SCOPE`, `WORKOUT_VERSION_NOT_PUBLISHED`,
`WORKOUT_ASSIGNMENT_CANCEL_REASON_REQUIRED`, `WORKOUT_ASSIGNMENT_NOT_FOUND`,
`REPORT_RANGE_INVALID`, `REPORT_EXPORT_TOO_LARGE` e
`IDEMPOTENCY_KEY_REUSED`.

Estos contratos no crean ni consultan ventas o pagos. La lectura remota del socio se
reserva para una aplicación futura.

## 6.14 Profesionales

- `GET /professionals`
- `POST /professionals`
- `GET /professionals/{professionalId}/services`
- `POST /professionals/{professionalId}/services`
- `GET /professionals/{professionalId}/availability`
- `POST /professionals/{professionalId}/availability`
- `GET /professionals/{professionalId}/availability-slots`
- `POST /wellness/appointments`
- `GET /wellness/appointments`
- `POST /wellness/appointments/{appointmentId}/confirm`

Nota implementada Hito 5:
- Los profesionales pertenecen siempre a la organizacion y sucursal activa.
- `engagementType` es explícito (`STAFF` o `EXTERNAL`) e independiente de
  `officeLocation`.
- Los servicios profesionales mantienen precio, moneda, duracion, tipo y estado propio.
- La disponibilidad se expresa por dia de semana y rango horario; el backend valida `day_of_week` entre 1 y 7 y que la hora de cierre sea posterior a la apertura.
- Los profesionales externos no administran disponibilidad interna. Sus citas
  reciben una fecha y hora solicitadas, nacen como `REQUESTED` y después pueden
  coordinarse y confirmarse.

## 6.15 Automatizaciones

- `POST /automations`
- `GET /automations`
- `POST /automations/{ruleId}/enable`
- `POST /automations/{ruleId}/disable`
- `POST /automations/{ruleId}/execute`
- `GET /automations/{ruleId}/executions`
- `GET /automations/dead-letters?limit=100`
- `POST /automations/dead-letters/{deadLetterId}/retry`
- `POST /automations/dead-letters/{deadLetterId}/discard`

Nota implementada Hito 5:
- Las reglas tienen `triggerType`, `conditionJson`, `actionType`, `actionConfigJson`, `status` y `retryLimit`.
- Disparadores ejecutables: `MANUAL`, `SCHEDULED`, `PAYMENT_COMPLETED`,
  `PAYMENT_PENDING_VALIDATION`, `BOOKING_CREATED`, `BOOKING_CONFIRMED` y
  `BOOKING_CANCELLED`, además de `MEMBERSHIP_EXPIRING` y
  `MEMBERSHIP_EXPIRED`.
- `conditionJson` se valida por disparador y sólo acepta los campos tipados:
  `intervalMinutes`; `daysBefore`/`planId`; `method`/`minimumAmountCents`;
  `businessContext`; o `resourceId`, según corresponda.
- `actionType = CREATE_NOTIFICATION` crea una notificacion interna en `notification_log`.
- Las ejecuciones registran `PROCESSING`, `SUCCEEDED`, `FAILED`,
  `RETRY_SCHEDULED`, `DEAD_LETTER` o `DISCARDED`, referencia, llave idempotente, versión de regla, intento,
  inicio, fin y error sanitizado cuando aplique.
- `GET /automations/{ruleId}/executions` devuelve hasta 100 ejecuciones
  recientes de la regla dentro del tenant y sucursal activos.
- La lectura requiere `automation.read`; la ejecución manual acepta
  `automation.execute` o `automation.manage`; crear, habilitar y deshabilitar
  requieren `automation.manage`.
- La bandeja de fallos requiere `automation.read`. Reintentar o descartar
  exige `automation.manage`, origen `EVENT`/`EXECUTION` y un motivo auditable.

## 6.16 Notificaciones

- `GET /notifications?limit=50`
- `POST /notifications`
- `GET /notifications/email-configuration`
- `PUT /notifications/email-configuration`
- `POST /notifications/email-configuration/test`
- `PUT /notifications/email-preferences`
- `POST /notifications/{notificationId}/retry`

Contrato vigente:
- `IN_APP` registra la notificación para operación interna;
- `EMAIL` resuelve destinatario directo, socio o usuario, valida preferencias y
  crea una entrega durable;
- la configuración tenant nunca recibe host, usuario ni contraseña: sólo
  habilitación, clave del proveedor, remitente y `Reply-To`;
- el adaptador SMTP toma conexión y secretos exclusivamente del entorno;
- los estados externos son `QUEUED`, `PROCESSING`, `RETRY_SCHEDULED`, `SENT`,
  `SUPPRESSED` y `DEAD_LETTER`;
- `WHATSAPP` y `PUSH` permanecen rechazados hasta que exista su adaptador.

## 6.17 Reportes

- `GET /reports/sales`
- `GET /reports/memberships`
- `GET /reports/access`
- `GET /reports/bookings`
- `GET /reports/inventory`
- `GET /reports/coaches`
- `GET /reports/owner-dashboard?fromDate=YYYY-MM-DD&toDate=YYYY-MM-DD`

Nota implementada Hito 5:
- `owner-dashboard` devuelve ingresos, ventas, clientes nuevos, membresias activas, accesos, reservas, inventario, coaches, rutinas, asignaciones y profesionales para la organizacion/sucursal activa.

## 6.18 Onboarding e importacion inicial

- `GET /onboarding/checklist`
- `POST /onboarding/pilot-setup`
- `POST /clients/imports`

Nota implementada Hito 6:
- `GET /onboarding/checklist` devuelve banderas de preparacion comercial: organizacion, sucursal, planes, productos, kiosco y clientes.
- `POST /onboarding/pilot-setup` actualiza organizacion/sucursal activa y crea de forma idempotente planes, productos y kiosco base para piloto.
- `POST /clients/imports` recibe `{ fileName, csvText, dryRun }` y devuelve conteos y resultado por fila.
- La plantilla CSV soportada usa columnas `firstName,lastName,birthDate,gender,phone,email,notes`.
- `dryRun=true` valida filas sin crear clientes; `dryRun=false` crea clientes validos y omite duplicados.

## 6.19 Códigos de error

- PERSON_ALREADY_EXISTS
- CLIENT_ALREADY_EXISTS
- PRODUCT_INACTIVE
- PLAN_INACTIVE
- MEMBERSHIP_NOT_ACTIVE
- MEMBERSHIP_ALREADY_CANCELLED
- PAYMENT_AMOUNT_MISMATCH
- PAYMENT_ALREADY_PROCESSED
- SELF_SERVICE_SESSION_EXPIRED
- SELF_SERVICE_ACTION_NOT_ALLOWED
- BENEFIT_NOT_ELIGIBLE
- BENEFIT_EVIDENCE_EXPIRED
- BOOKING_CONFLICT
- BOOKING_NOT_ALLOWED
- ACCESS_DENIED
- INVENTORY_STOCK_INSUFFICIENT
- PROFESSIONAL_NOT_FOUND
- AUTOMATION_RULE_NOT_FOUND
- CLIENT_IMPORT_EMPTY
- CLIENT_IMPORT_TOO_LARGE
- BRANCH_REQUIRED
- PERMISSION_DENIED

## 6.20 Cambios Hito 3.5

Contratos HTTP vigentes:
- Se preservan las rutas de ventas, pagos y membresías. La ruta pública
  heredada de alta por `terminalCode` se retiró por seguridad y fue sustituida
  por `POST /self-service/kiosk-sessions` autenticado.
- `ManualPaymentRequest`, renovacion, cambio de plan y creacion de venta aceptan `idempotencyKey` opcional.
- `POST /self-service/kiosk-sessions` devuelve `sessionToken` para la pestaña
  restringida.
- Las rutas de sesion de kiosco requieren header `X-Self-Service-Session-Token`.

API interna publica:
- `SalesOperations.createMembershipRenewal(CreateMembershipRenewalSaleCommand)`
- `SalesOperations.createMembershipPlanChange(CreateMembershipPlanChangeSaleCommand)`
- `MembershipOperations.renew(RenewMembershipOperationCommand)`
- `MembershipOperations.changePlan(ChangeMembershipPlanOperationCommand)`
- `ClientLookupOperations.verifyForSelfService(VerifyClientIdentityCommand)`
- `ClientLookupOperations.selfServiceProfile(organizationId, clientId)`

Eventos internos publicos:
- `com.gymos.sales.api.events.SaleCreatedV1`
- `com.gymos.sales.api.events.PaymentRecordedV1`
- `com.gymos.sales.api.events.PaymentCompletedV1`
- `com.gymos.sales.api.events.PaymentRejectedV1`
- `com.gymos.memberships.api.events.MembershipActivatedV1`
- `com.gymos.memberships.api.events.MembershipRenewedV1`
- `com.gymos.memberships.api.events.MembershipPlanChangedV1`

Permisos por endpoint interno:
- `organization.read`, `organization.update`
- `branch.read`, `branch.create`, `branch.update`
- `client.read`, `client.create`, `client.update`
- `catalog.read`, `catalog.manage`
- `sale.read`, `sale.create`
- `payment.manual.create`, `payment.manual.validate`, `payment.manual.reject`, `payment.receipt.upload`
- `payment.settings.read`, `payment.settings.manage`, `payment.provider.stripe.manage`
- `membership.read`, `membership.renew`, `membership.change-plan`
- `report.sales.read`
- `access.read`, `access.manage`, `access.manual.open`
- `inventory.read`, `inventory.manage`
- `resource.read`, `resource.manage`
- `booking.read`, `booking.manage`
- `coach.read`, `coach.manage`, `coach.profile.manage`
- `workout.read.own`, `workout.read.all`
- `workout.manage.own`, `workout.manage.all`
- `workout.assign`, `workout.pdf.download`, `coach.metrics.read`
- `report.coaches.read`
- `professional.read`, `professional.manage`
- `notification.read`, `notification.manage`
- `automation.read`, `automation.execute`, `automation.manage`
- `report.owner.read`
- `onboarding.manage`
- `client.import`

Nota: `Recepcion` conserva `resource.read`, `booking.read` y `booking.manage` para hacer reservaciones, pero no recibe `resource.manage`; la creacion/modificacion de recursos reservables queda para perfiles administrativos.

Nota implementada durante saneamiento:
- `Gerente` puede recibir `user.read` y `user.manage` para administrar staff operativo, pero la API publica de usuarios filtra roles y usuarios por contexto.
- Desde contexto de gerente solo se ofrecen roles operativos permitidos (`Recepcion` y `Coach`) y no se permite crear, modificar, suspender ni cambiar rol a `Dueno del gimnasio`, `Gerente` o `Administrador GymOS`.
- Si el gerente tiene acceso a una sola sucursal, el frontend envia esa sucursal de forma obligatoria para nuevos usuarios operativos.

Errores agregados o formalizados:
- `IDEMPOTENCY_KEY_REUSED`
- `PAYMENT_ALREADY_COMPLETED`
- `SELF_SERVICE_SESSION_INVALID`
- `SELF_SERVICE_SESSION_BLOCKED`
- `SELF_SERVICE_VERIFICATION_FAILED`
- `RATE_LIMIT_EXCEEDED`
 
## Hito 7 - Benefits API

### `GET /api/v1/benefits`

Permiso: `benefit.read`.

Devuelve beneficios de la organizacion activa.

### `POST /api/v1/benefits`

Permiso: `benefit.manage`.

Request:

```json
{
  "branchId": null,
  "code": "STUDENT",
  "name": "Estudiante",
  "benefitType": "STUDENT",
  "discountType": "PERCENT",
  "discountValueCents": null,
  "discountPercent": 15,
  "requiresEvidence": true,
  "combinable": false,
  "priority": 10,
  "validFrom": "2026-01-01",
  "validUntil": null,
  "status": "ACTIVE"
}
```

Errores:

- `BENEFIT_ALREADY_EXISTS`
- `AUTHENTICATION_REQUIRED`

### `POST /api/v1/clients/{clientId}/benefits`

Permiso: `client.benefit.assign`.

Asigna beneficio a un cliente del tenant activo.

### `POST /api/v1/client-benefits/{clientBenefitId}/verify`

Permiso: `client.benefit.verify`.

Verifica un beneficio asignado.

## Hito 7 - Visit Pass API

Las rutas viven dentro del modulo de acceso porque su operacion diaria sucede en recepcion/entrada, pero usan permisos propios de visitas.

### `GET /api/v1/access/visit-passes`

Permiso: `visit.read`.

Query opcional:

- `clientId`: filtra paquetes de un cliente.

Devuelve hasta 100 paquetes recientes de la sucursal activa.

### `POST /api/v1/access/visit-passes`

Permiso: `visit.create`.

Request:

```json
{
  "clientId": "uuid",
  "saleId": null,
  "passType": "VISIT_PACK",
  "totalVisits": 5,
  "validFrom": "2026-07-16",
  "validUntil": "2026-08-15"
}
```

Tipos permitidos:

- `DROP_IN`
- `VISIT_PACK`
- `COURTESY`
- `PROMOTION`

Errores:

- `CLIENT_NOT_FOUND`
- `CLIENT_NOT_ACTIVE`
- `VISIT_PASS_INVALID_RANGE`
- `BRANCH_REQUIRED`

### `POST /api/v1/access/visit-passes/{visitPassId}/consume`

Permiso: `visit.consume`.

Request:

```json
{
  "reason": "Consumo registrado en recepcion"
}
```

Descuenta una visita de forma atomica. Si era la ultima, cambia el estado a `CONSUMED`.

Errores:

- `VISIT_PASS_NOT_FOUND`
- `VISIT_PASS_NOT_ACTIVE`
- `VISIT_PASS_OUT_OF_RANGE`
- `VISIT_PASS_CONSUMED`
- `VISIT_PASS_CONFLICT`

## Hito 7 - Refund API

### `POST /api/v1/sales/{saleId}/refunds`

Permiso: `sale.refund`.

Registra un reembolso manual procesado sobre una venta de la sucursal activa. El reembolso no elimina pagos ni cambia la venta historica; queda registrado como movimiento financiero separado en `sale_refund`.

Request:

```json
{
  "paymentId": null,
  "amountCents": 50000,
  "reason": "Cancelacion autorizada por administracion",
  "idempotencyKey": "refund-20260716-0001"
}
```

Notas:

- `paymentId` es opcional. Si se informa, debe pertenecer a la venta y estar `COMPLETED`.
- `amountCents` no puede exceder el saldo pagado disponible menos reembolsos `PROCESSED` previos.
- `idempotencyKey` evita registrar dos veces la misma operacion.
- `GET /api/v1/sales/{saleId}` incluye `refunds` en el detalle.

Errores:

- `SALE_NOT_FOUND`
- `PAYMENT_NOT_FOUND`
- `PAYMENT_SALE_MISMATCH`
- `PAYMENT_NOT_COMPLETED`
- `REFUND_AMOUNT_INVALID`
- `REFUND_EXCEEDS_PAYMENT`
- `REFUND_EXCEEDS_REFUNDABLE_BALANCE`
- `IDEMPOTENCY_KEY_REUSED`

## Hito 7 - Booking Payment API

### `POST /api/v1/bookings/{bookingId}/payments`

Permiso: `booking.payment.manage`.

Vincula una reserva con una venta existente del POS. La venta sigue perteneciendo al modulo `sales`; `bookings` solo registra la relacion operativa en `booking_payment`.

Request:

```json
{
  "saleId": "uuid",
  "status": "PAID",
  "expiresAt": null
}
```

Estados permitidos:

- `PENDING`
- `PAID`
- `EXPIRED`
- `CANCELLED`

Comportamiento:

- Si la reserva ya tenia pago vinculado, la relacion se actualiza de forma idempotente por `organization_id + booking_id`.
- Si `status = PAID` y la reserva estaba `RESERVED`, la reserva pasa a `CONFIRMED`.
- `GET /api/v1/bookings` y `GET /api/v1/bookings/{bookingId}` incluyen `bookingPaymentId`, `saleId`, `paymentStatus` y `saleNumber`.

Errores:

- `BOOKING_NOT_FOUND`
- `BOOKING_ALREADY_CLOSED`
- `SALE_NOT_FOUND`

## Hito 7 - Scheduled Automations

Las automatizaciones aceptan `triggerType = SCHEDULED` usando el contrato existente de `POST /api/v1/automations`.

Request ejemplo:

```json
{
  "name": "Recordatorio operativo diario",
  "triggerType": "SCHEDULED",
  "conditionJson": "{\"intervalMinutes\":1440}",
  "actionType": "CREATE_NOTIFICATION",
  "actionConfigJson": "{\"channel\":\"IN_APP\",\"subject\":\"Revision diaria\",\"body\":\"Revisa pagos pendientes y reservas.\"}",
  "retryLimit": 3
}
```

Comportamiento:

- Al crear una regla `SCHEDULED`, backend calcula `nextRunAt` usando `conditionJson.intervalMinutes`.
- Un job interno `@Scheduled` revisa reglas vencidas cada `gymos.automation.scheduler-delay-ms` milisegundos, por defecto 60000.
- Cada regla vencida se reclama con `locked_until` para evitar ejecuciones simultaneas.
- Al terminar, la regla se reprograma sumando nuevamente `intervalMinutes`.
- Las ejecuciones se registran en `automation_execution` y, para `CREATE_NOTIFICATION`, se encola una notificacion interna.

## Hito 7 - Eventos durables de Automatizaciones

Los eventos públicos de ventas, reservas y ciclo de membresía se materializan de forma
transaccional en `automation_trigger_event`. Un worker reclama cada ocurrencia
con lease y ejecuta las reglas habilitadas del mismo gimnasio y alcance de
sucursal.

Eventos consumidos:

- `PaymentCompletedV1` -> `PAYMENT_COMPLETED`;
- `PaymentRecordedV1` con estado `PENDING_VALIDATION` ->
  `PAYMENT_PENDING_VALIDATION`;
- `BookingCreatedV1` -> `BOOKING_CREATED`;
- `BookingStatusChangedV1` a `CONFIRMED` o `CANCELLED` -> disparador homónimo.
- `MembershipExpiringV1` -> `MEMBERSHIP_EXPIRING`, una vez por día dentro del
  horizonte configurado y con ocurrencia estable por fecha final;
- `MembershipExpiredV1` -> `MEMBERSHIP_EXPIRED`, una vez aplicada la fecha de
  gracia y reclamado el marcador de publicación;
- `RecurringChargeDueV1`, `RecurringChargeSucceededV1` y
  `RecurringChargeFailedV1` -> disparadores `RECURRING_CHARGE_*` homónimos.

La referencia del evento y la versión funcional de la regla forman una llave
estable. Un reinicio después de crear la notificación, registrar la ejecución o
vencer el lease retoma la ocurrencia sin duplicar el efecto. Los cargos
domiciliados basan la referencia en la ocurrencia de vigencia, por lo que un
webhook o worker repetido no duplica la notificación.

Los fallos agotados aparecen en la bandeja `dead-letters`. El reintento vuelve
a poner la ocurrencia durable en cola sin cambiar su identidad; el descarte
cierra la ejecución u ocurrencia y permite continuar con las demás reglas. Las
dos decisiones exigen motivo y producen auditoría.

Historial de ejecuciones:

- `GET /api/v1/automations/{ruleId}/executions?page=0&size=25`;
- responde `{ items, page, size, totalElements, totalPages }`;
- `page` se normaliza a cero y `size` queda acotado entre 1 y 100;
- la consulta exige `automation.read` y filtra organización, sucursal y regla.

Historial operativo de pagos, disponible con `payment.settings.read`:

- `GET /api/v1/payment-settings/history/mandates?page=0&size=25`;
- `GET /api/v1/payment-settings/history/recurring-charges?page=0&size=25`;
- `GET /api/v1/payment-settings/history/webhooks?page=0&size=25`.

Los tres contratos usan la misma envoltura paginada, se filtran siempre por la
organización del JWT y no exponen PAN, CVV, payload del proveedor, token de
método de pago ni hash del webhook. El historial de mandatos limita la tarjeta
a marca, últimos cuatro y vencimiento.

## Hito 7.7 - Consolas, marca tenant y administracion de plataforma

### Auth

`POST /api/v1/auth/login` acepta `username` con nombre de usuario interno o email de la persona asociada. La respuesta mantiene el contrato existente de usuario, permisos, organizacion y sucursal.

Para usuarios tenant sin `platform.admin`, `branchId` se resuelve automaticamente con la primera sucursal activa a la que el usuario tenga acceso por `user_branch_access`. Si el usuario no tiene sucursales asignadas, `branchId` queda `null` y el frontend muestra estado de configuracion pendiente.

`POST /api/v1/auth/refresh` aplica la misma resolucion de sucursal por defecto para evitar que una sesion renovada pierda contexto operativo.

El frontend decide la consola inicial por permisos:

- `platform.admin` -> `/platform/gymnasiums`
- permisos gerenciales/reportes -> `/dashboard`
- permisos de recepcion/caja -> `/reception`
- permisos de coaching -> `/coaches`

### Organizaciones

Los contratos de organizacion incluyen marca de tenant:

```json
{
  "id": "uuid",
  "legalName": "Appex S.A. de C.V.",
  "displayName": "Appex Gym",
  "brandName": "Appex Gym",
  "logoUrl": "https://example.com/logo.png",
  "primaryColor": "#0f766e",
  "accentColor": "#f59e0b",
  "surfaceColor": "#f8fafc"
}
```

Endpoints afectados:

- `GET /api/v1/organizations/current`
- `PUT /api/v1/organizations/current`
- `GET /api/v1/organizations`
- `POST /api/v1/organizations`

Reglas:

- `primaryColor`, `accentColor` y `surfaceColor` deben usar formato hexadecimal `#RRGGBB`.
- La marca se guarda por organizacion/gimnasio. Gerencia, recepcion, coaches, kiosco y pantallas internas usan siempre la marca del tenant activo.
- La consola de plataforma conserva tema neutral y atribución GymFort; las
  consolas tenant combinan esa atribución con la marca del gimnasio.

### Usuarios internos

`POST /api/v1/users/internal` no permite asignar roles con permiso `platform.admin` si el usuario autenticado no tiene `platform.admin`.

El frontend debe filtrar roles por contexto:

- Plataforma puede crear accesos de administracion de plataforma cuando corresponda.
- Un gimnasio no debe ofrecer `Administrador GymOS` como rol operativo del tenant.
- Recepcionistas, coaches, gerentes y administradores de gimnasio entran a consolas distintas segun permisos.

Errores esperados:

- `VALIDATION_ERROR`: campos invalidos, telefono/correo/contrasena no aceptados o sucursal requerida.
- `FORBIDDEN`: intento de asignar un rol de plataforma sin permiso.

## Hito 7.8 - Modo soporte y consolas por rol

### Sesion actual

`AuthResponse.user` y `GET /api/v1/auth/me` agregan campos de contexto frontend:

```json
{
  "permissions": ["platform.admin"],
  "roles": ["Administrador GymOS"],
  "platformAdmin": true,
  "supportMode": false,
  "console": "PLATFORM_OWNER"
}
```

Valores posibles de `console`:

- `PLATFORM_OWNER`
- `TENANT_OWNER`
- `TENANT_MANAGER`
- `RECEPTION`
- `COACH`

### Soporte de plataforma

`POST /api/v1/auth/select-organization`:

- Para usuarios con `platform.admin`, permite entrar a cualquier gimnasio como soporte aunque no exista `user_branch_access`.
- Emite token con `supportMode = true`.
- Selecciona la primera sucursal activa del gimnasio cuando exista.
- Audita `PLATFORM_SUPPORT_STARTED` con origen `PLATFORM_SUPPORT`.

`POST /api/v1/auth/select-branch`:

- Para usuarios normales, mantiene validacion por `user_branch_access`.
- Para Admin GymOS en `supportMode`, permite elegir sucursales del tenant activo.
- Audita `BRANCH_SELECTED` con origen `PLATFORM_SUPPORT` cuando aplique.

### Usuarios internos

`GET /api/v1/users/roles`:

- En contexto tenant o soporte devuelve roles del gimnasio: `Dueno del gimnasio`, `Gerente`, `Recepcion`, `Coach`.
- No devuelve `Administrador GymOS` cuando se administran usuarios de un gimnasio.
- Para gerentes, la lista queda limitada a roles operativos permitidos; no devuelve `Dueno del gimnasio` ni `Gerente`.

`POST /api/v1/users/internal`:

- Rechaza cualquier rol con `platform.admin` con `ROLE_NOT_AVAILABLE`.
- Rechaza roles no asignables por el perfil actual con `ROLE_NOT_ALLOWED`; por ejemplo, un gerente no puede crear dueños ni otros gerentes.
- Para `Coach`, persiste cuenta, sucursales, especialidades y biografia como una
  sola operacion; un fallo del perfil revierte el alta completa.
- Los usuarios creados entran a su consola segun permisos/rol.
## Extensiones vigentes de membresías y acceso

### Cotización de plan

`POST /api/v1/membership-quotes`

```json
{ "clientId": "uuid", "planId": "uuid" }
```

Devuelve `listPriceCents`, `discountCents`, `totalCents`, `currency` y
`appliedBenefits`. El servidor es la fuente de verdad.

Cada elemento de `appliedBenefits` incluye `benefitId`, `code`, `name` y
`discountCents`. El mismo conjunto se congela en la venta al ejecutar alta,
renovación o cambio.

### Alta inicial de membresía

`POST /api/v1/membership-enrollments`

Recibe socio, plan, método y validación de pago. Rechaza otra membresía activa,
aplica la cotización de backend y registra venta, pago, periodo y activación.

### Periodos de membresía

`GET /api/v1/memberships/{membershipId}/periods`

Devuelve alta/importación, renovaciones y cambios con plan, vigencia, venta y
fecha de registro.

Las fechas son inclusivas. Renovar antes del fin inicia el periodo nuevo al día
siguiente; renovar una membresía vencida inicia en la fecha operativa actual.

### Contexto financiero de membresías

`GET /api/v1/sales` y el encabezado de
`GET /api/v1/sales/{saleId}` incluyen `businessContext`.

`GET /api/v1/sales/{saleId}` agrega `appliedBenefits`.

`GET /api/v1/sales/reports/summary` agrega `salesByBusinessContext`, con conteo,
subtotal, descuento, total, cobrado y saldo para:

- `RETAIL_SALE`
- `MEMBERSHIP_ENROLLMENT`
- `MEMBERSHIP_RENEWAL`
- `MEMBERSHIP_PLAN_CHANGE`

### Accesos efectivos

`GET /api/v1/access/clients/{clientId}/effective-benefits`

Deriva los beneficios desde la membresía vigente y el plan actual.

`PUT /api/v1/access/devices/{deviceId}/benefits`

```json
{ "benefitIds": ["uuid"] }
```

Reemplaza de forma auditable el derecho requerido. Para un punto productivo la
lista debe contener exactamente un beneficio `ACCESS`; cero o más de uno se
rechazan.

## Organización y marca persistente

`GET /api/v1/organizations/current`

- requiere `organization.read` o `organization.update`;
- devuelve los datos y la marca de la organización activa;
- no depende de una sucursal seleccionada.

`POST /api/v1/organizations/current/logo`

- requiere `organization.update`;
- recibe `multipart/form-data` con la parte `file`;
- acepta PNG, JPG o WebP de hasta 2 MB;
- persiste el archivo en almacenamiento de objetos, audita el reemplazo y
  devuelve la organización actualizada con una URL versionada.

`POST /api/v1/organizations/{organizationId}/logo`

- requiere `platform.admin`;
- permite que el alta de plataforma cree primero el tenant y después persista
  su logo sin entrar en modo soporte;
- comparte validación, almacenamiento, auditoría y compensación transaccional
  con la carga del gimnasio actual.

`GET /api/v1/organizations/{organizationId}/logo`

- lectura pública de la identidad visual no sensible;
- sólo resuelve la referencia controlada de la organización, nunca una clave
  de objeto proporcionada por el cliente;
- el frontend la expone mediante
  `/api/tenant-logo/{organizationId}?v={version}`.

## Configuración e integración de proveedores de pago

Los endpoints operativos de configuración requieren `payment.settings.manage`,
salvo la consulta, que también acepta `payment.settings.read`. Conectar o
refrescar Stripe exige `payment.provider.stripe.manage` o `platform.admin`.

- `POST /api/v1/payment-settings/stripe/connect`: crea/reutiliza la cuenta
  Express del gimnasio y devuelve un Account Link temporal.
- `POST /api/v1/payment-settings/stripe/refresh`: consulta a Stripe y sólo
  marca conexión cuando `details_submitted` y `charges_enabled` son verdaderos.
- Estos dos endpoints no aceptan `payment.settings.manage` por sí solo: un
  gerente puede encender/apagar una conexión existente, no administrarla.
- `POST /api/v1/payment-settings/mercado-pago/connect`: inicia OAuth con estado
  de un solo uso y PKCE.
- `GET /api/v1/payment-settings/mercado-pago/oauth/callback`: consume el estado,
  intercambia el código y redirige a configuración.
- `GET /api/v1/payment-settings/mercado-pago/terminals`: lista las Point de la
  cuenta conectada.
- `PUT /api/v1/payment-settings/mercado-pago/terminal`: selecciona la Point y la
  configura en modo PDV. No identifica la computadora donde corre el kiosco.
- `POST /api/v1/sales/payments/{paymentId}/mercado-pago-order`: envía el monto
  pendiente a la Point seleccionada; reintentar conserva la misma Order.

Webhooks públicos con verificación criptográfica:

- `POST /api/v1/payment-webhooks/stripe`;
- `POST /api/v1/payment-webhooks/mercado-pago`.

El retorno del navegador y el cuerpo del webhook no son fuente suficiente para
completar el pago. El backend consulta la sesión/Order al proveedor y concilia
monto, moneda y contexto antes de actualizar ventas o membresías.

## Cobranza comercial de plataforma

Estos endpoints pertenecen a la consola neutral de GymFort y requieren
exclusivamente `platform.admin`; ningún dueño, administrador o gerente de un
gimnasio puede consultar o mutar esta información.

`GET /api/v1/platform/customers`

- devuelve el resumen ejecutivo y una fila por `Organization` cliente con etapa,
  salud derivada, responsable, contacto de cobranza, contrato, seguimiento, MRR,
  vencidos y configuración pendiente;
- excluye la organización técnica GymFort y no expone datos personales de socios.

`PUT /api/v1/platform/customers/{organizationId}`

- actualiza el expediente comercial interno: etapa, responsable, contacto de
  cobranza, fechas, días de crédito, seguimiento y notas;
- exige `expectedVersion`; un expediente modificado en otra sesión responde
  `CUSTOMER_ACCOUNT_VERSION_CONFLICT` en lugar de sobrescribirlo.

`GET /api/v1/platform/billing/overview`

- devuelve el resumen de MRR configurado, clientes, sucursales, socios activos,
  usuarios internos activos, vencimientos y sucursales sin configurar;
- devuelve una fila por sucursal con cliente, socios activos, planes de
  membresía activos, plan sugerido/contratado, importe, corte y estado de cobro.

`PUT /api/v1/platform/billing/branches/{branchId}`

- configura estado de suscripción, día de corte, próximo cobro, plan comercial,
  cotización Enterprise, implementación y notas;
- los planes estándar no aceptan importe manual; `ENTERPRISE` sí lo requiere.
- exige `expectedVersion`; `lastPaymentDate` y `lastPaymentAmount` son de sólo
  lectura y se actualizan únicamente desde el ledger de pagos.

`GET /api/v1/platform/billing/branches/{branchId}/payments`

- devuelve hasta los 50 cobros comerciales más recientes de la sucursal,
  ordenados por fecha efectiva y registro.

`POST /api/v1/platform/billing/branches/{branchId}/payments`

```json
{
  "paymentType": "SUBSCRIPTION",
  "paymentMethod": "TRANSFER",
  "amount": 1590.00,
  "paidOn": "2026-08-23",
  "reference": "SPEI-001",
  "notes": "Mensualidad de agosto",
  "idempotencyKey": "payment-2026-08-gym-centro"
}
```

- acepta `SUBSCRIPTION`, `IMPLEMENTATION` u `OTHER` y medios previamente
  definidos por el contrato;
- rechaza importe no positivo, fecha futura, referencia mayor a 160 caracteres
  y notas mayores a 1000;
- una mensualidad exige suscripción `ACTIVE` o `PENDING`, registra la evidencia
  y mueve el siguiente corte al siguiente día ancla; un pago de implementación
  completo la marca liquidada;
- responde el pago creado y la configuración comercial actualizada. Los pagos
  no se editan ni eliminan por esta API;
- repetir la misma `idempotencyKey` y los mismos datos devuelve el cobro original
  sin avanzar nuevamente el corte; reutilizarla con datos distintos se rechaza.
