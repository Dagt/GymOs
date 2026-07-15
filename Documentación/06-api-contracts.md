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
- `GET /workouts/{id}`
- `POST /workouts/{id}/versions`
- `POST /workouts/{id}/assign`
- `GET /clients/{clientId}/workouts`

## 6.14 Profesionales

- `POST /professionals`
- `GET /professionals`
- `POST /professionals/{id}/services`
- `GET /professionals/{id}/availability`

## 6.15 Automatizaciones

- `POST /automations`
- `GET /automations`
- `PATCH /automations/{id}`
- `POST /automations/{id}/enable`
- `POST /automations/{id}/disable`
- `GET /automations/{id}/executions`

## 6.16 Reportes

- `GET /reports/sales`
- `GET /reports/memberships`
- `GET /reports/access`
- `GET /reports/bookings`
- `GET /reports/inventory`
- `GET /reports/coaches`

## 6.17 Códigos de error

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
- INVENTORY_INSUFFICIENT
- PERMISSION_DENIED

## 6.18 Cambios Hito 3.5

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

Errores agregados o formalizados:
- `IDEMPOTENCY_KEY_REUSED`
- `PAYMENT_ALREADY_COMPLETED`
- `SELF_SERVICE_SESSION_INVALID`
- `SELF_SERVICE_SESSION_BLOCKED`
- `SELF_SERVICE_VERIFICATION_FAILED`
- `RATE_LIMIT_EXCEEDED`
