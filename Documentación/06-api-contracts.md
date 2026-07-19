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

Permisos:

- `user.read` para listar roles y usuarios internos.
- `user.manage` para crear usuarios internos y suspender/reactivar cuentas.

`POST /users/internal` crea persona, cuenta de usuario, rol y accesos a sucursales dentro de la organizacion actual. No devuelve contrasenas ni hashes.

`PATCH /users/internal/{userId}/status` cambia el estado de una cuenta interna. Estados validos: `ACTIVE`, `SUSPENDED`, `INACTIVE`. Una cuenta suspendida o inactiva no puede iniciar sesion. No se permite suspender la propia cuenta.

Request:

```json
{
  "firstName": "Ana",
  "lastName": "Lopez",
  "email": "ana@example.com",
  "phone": "+520000000000",
  "username": "ana@example.com",
  "temporaryPassword": "Temporal123!",
  "roleId": "uuid",
  "branchIds": ["uuid"]
}
```

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
- `Gerente`: delegado operativo del gimnasio. Gestiona operacion diaria, equipo autorizado, inventario, agenda, recepcion y reportes permitidos, sin acceso a administracion de plataforma.

Estado comercial de gimnasio cliente:

- `PATCH /organizations/{organizationId}/status`
- Permiso: `platform.admin`.
- Estados validos: `ACTIVE`, `SUSPENDED`, `INACTIVE`.
- Usuarios tenant de una organizacion `SUSPENDED` o `INACTIVE` no pueden iniciar sesion ni refrescar sesion.
- Admin GymOS conserva acceso para soporte y reactivacion.

## 6.1.2 Kiosco de autoservicio

- `POST /self-service/terminals/{terminalCode}/sessions`
- `POST /self-service/sessions/{sessionId}/verify`
- `GET /self-service/sessions/{sessionId}/summary`
- `GET /self-service/sessions/{sessionId}/membership-options`
- `POST /self-service/sessions/{sessionId}/membership-renewals`
- `POST /self-service/sessions/{sessionId}/membership-change-requests`
- `GET /self-service/sessions/{sessionId}/pending-payments`
- `POST /self-service/sessions/{sessionId}/payments`
- `POST /self-service/sessions/{sessionId}/payment-proofs`
- `PATCH /self-service/sessions/{sessionId}/contact-data`
- `POST /self-service/sessions/{sessionId}/end`

Los endpoints de autoservicio sólo exponen acciones permitidas para clientes en una sesión temporal de kiosco. Las reglas de pagos, membresías, beneficios y permisos se validan en backend.

Nota implementada Hito 3: las acciones de renovacion y cambio de plan reciben `{membershipId}` en la ruta concreta:
- `POST /self-service/sessions/{sessionId}/membership-renewals/{membershipId}`
- `POST /self-service/sessions/{sessionId}/membership-change-requests/{membershipId}`

Cambio Hito 3.5 final:
- `POST /self-service/sessions/{sessionId}/verify` ahora recibe `{ "identifier": "email o telefono", "birthDate": "YYYY-MM-DD" }`.
- Contrato anterior: `{ "identifier": "email o telefono" }`.
- Razon: evitar verificacion debil y reducir riesgo de enumeracion de clientes.
- Impacto frontend: `/kiosk/[terminalCode]` solicita fecha de nacimiento y envia ambos campos.

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

## 6.4 Productos

- `GET /catalog/products`
- `POST /catalog/products`
- `GET /products/{id}` (pendiente)
- `PATCH /products/{id}` (pendiente)
- `PATCH /products/{id}/status` (pendiente)

## 6.5 Planes

- `GET /catalog/plans`
- `POST /catalog/plans`
- `GET /plans/{id}`
- `PATCH /plans/{id}`
- `POST /plans/{id}/archive`

## 6.6 Membresías

- `GET /clients/{clientId}/memberships`
- `GET /memberships/{id}`
- `POST /memberships/{id}/renew`
- `POST /memberships/{id}/cancel`
- `PATCH /memberships/{id}/auto-renew`
- Nota implementada: `GET /clients/{clientId}/memberships` y `GET /memberships/{id}` exponen membresias por cliente y detalle por membresia.
- Nota implementada: `POST /memberships/{id}/renew` crea una venta de renovacion, registra pago manual y activa una nueva membresia acumulativa cuando el pago queda completado.
- Nota implementada: `POST /memberships/{id}/change-plan` crea una venta de cambio de plan, exige pago completado, cancela la membresia anterior y activa la nueva.
- Nota implementada: una venta pagada que incluya un producto vinculado a `membership_plan` activa una membresia.

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
- `PATCH /resources/{id}` (pendiente)
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
- `GET /resources/{resourceId}/availability?date=YYYY-MM-DD` devuelve slots con capacidad, reservados y disponibilidad.
- `POST /bookings` crea reserva en estado `RESERVED`.
- Acciones implementadas: `confirm`, `check-in`, `complete` y `cancel`.

## 6.12 Accesos

- `POST /access/requests`
- `POST /access/commands/open`
- `POST /access/commands/temporary`
- `POST /access/commands/block`
- `GET /access/logs`
- `GET /access/devices`

Nota implementada Hito 4:
- `GET /access/devices` lista dispositivos de acceso por sucursal activa.
- `GET /access/logs` lista la bitacora reciente de accesos por sucursal activa.
- `POST /access/requests` evalua acceso por cliente o identificador contra membresia activa o pase temporal vigente.
- `POST /access/commands/open` registra apertura manual con motivo y comando simulado.
- `POST /access/commands/temporary` crea pase temporal limitado por expiracion y numero de usos.
- `POST /access/commands/block` queda pendiente para integracion futura con hardware real.

## 6.13 Coaches y rutinas

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
- `POST /workouts` crea la rutina y su primera version publicada.
- `POST /workouts/{id}/versions` agrega una nueva version publicada sin modificar asignaciones historicas.
- `POST /workouts/{id}/assign` asigna una version concreta o la ultima version publicada a un cliente activo.
- `GET /reports/coaches` devuelve conteos base: coaches, rutinas, asignaciones activas y asignaciones de ultimos 30 dias.

## 6.14 Profesionales

- `GET /professionals`
- `POST /professionals`
- `GET /professionals/{professionalId}/services`
- `POST /professionals/{professionalId}/services`
- `GET /professionals/{professionalId}/availability`
- `POST /professionals/{professionalId}/availability`

Nota implementada Hito 5:
- Los profesionales pertenecen siempre a la organizacion y sucursal activa.
- Los servicios profesionales mantienen precio, moneda, duracion, tipo y estado propio.
- La disponibilidad se expresa por dia de semana y rango horario; el backend valida `day_of_week` entre 1 y 7 y que la hora de cierre sea posterior a la apertura.

## 6.15 Automatizaciones

- `POST /automations`
- `GET /automations`
- `POST /automations/{ruleId}/enable`
- `POST /automations/{ruleId}/disable`
- `POST /automations/{ruleId}/execute`
- `GET /automations/{ruleId}/executions`

Nota implementada Hito 5:
- Las reglas tienen `triggerType`, `conditionJson`, `actionType`, `actionConfigJson`, `status` y `retryLimit`.
- `actionType = CREATE_NOTIFICATION` crea una notificacion interna en `notification_log`.
- Las ejecuciones registran estado `SUCCEEDED`, `SKIPPED` o `FAILED`, referencia del disparador y error cuando aplique.

## 6.16 Notificaciones

- `GET /notifications?limit=50`
- `POST /notifications`

Nota implementada Hito 5:
- La V1 registra notificaciones internas y auditorias de envio en `notification_log`.
- Los canales externos reales quedan preparados por contrato y no se conectan aun a proveedores de email, SMS o WhatsApp.

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

Contratos HTTP preservados:
- No se eliminaron rutas existentes de ventas, pagos, membresias ni kiosco.
- `ManualPaymentRequest`, renovacion, cambio de plan y creacion de venta aceptan `idempotencyKey` opcional.
- `POST /self-service/terminals/{terminalCode}/sessions` ahora devuelve tambien `sessionToken`.
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
- `branch.read`, `branch.update`
- `client.read`, `client.create`, `client.update`
- `catalog.read`, `catalog.manage`
- `sale.read`, `sale.create`
- `payment.manual.create`, `payment.manual.validate`, `payment.manual.reject`, `payment.receipt.upload`
- `membership.read`, `membership.renew`, `membership.change-plan`
- `report.sales.read`
- `access.read`, `access.manage`, `access.manual.open`
- `inventory.read`, `inventory.manage`
- `resource.read`, `resource.manage`
- `booking.read`, `booking.manage`
- `coach.read`, `coach.manage`
- `workout.read`, `workout.manage`
- `report.coaches.read`
- `professional.read`, `professional.manage`
- `notification.read`, `notification.manage`
- `automation.read`, `automation.manage`
- `report.owner.read`
- `onboarding.manage`
- `client.import`

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
- La marca se guarda por organizacion y se aplica a gerencia, recepcion, coaches, kiosco y pantallas internas del tenant.
- La consola de Administrador GymOS conserva marca neutral de plataforma.

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

`POST /api/v1/users/internal`:

- Rechaza cualquier rol con `platform.admin` con `ROLE_NOT_AVAILABLE`.
- Los usuarios creados entran a su consola segun permisos/rol.
