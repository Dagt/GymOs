# Auditoría de ventas, pagos y membresías

## Estado

`IN_PROGRESS`

Este bloque revisa integridad monetaria, idempotencia, aislamiento multi-tenant y límites entre los módulos `sales`, `payments` y `memberships`.

## Evidencia revisada

### Autoridad de precios y totales

La creación ordinaria de ventas no confía en el precio unitario enviado por frontend. `SalesService.create` recibe identificadores, cantidades y descuentos, vuelve a consultar cada producto mediante `SalesRepository.productForSale(organizationId, productId)` y calcula en backend:

- subtotal por línea;
- descuentos por línea;
- descuento global;
- total final;
- moneda común de la venta.

También rechaza descuentos superiores al subtotal y ventas de membresía sin cliente.

Estado: `PARTIAL_VERIFIED`.

Pendiente:

- límites explícitos para cantidades y protección de overflow aritmético;
- política de autorización de descuentos;
- pruebas de precio manipulado, moneda mixta y descuentos inválidos;
- evitar consultar dos veces el mismo producto durante una creación.

### Aislamiento de listados y reportes

Los listados recientes y reportes filtran por:

- `organization_id` del principal;
- `branch_id` del principal;
- rango temporal;
- `deleted_at IS NULL`.

Estado: `VERIFIED_IN_CODE`, pendiente prueba ejecutable.

### Detalle de venta

`SalesService.detail` y `SalesRepository.saleHeader` filtran por organización, pero no por sucursal. Los items, pagos, reembolsos y membresías asociados se recuperan también usando organización y venta.

Hallazgo `P0-SPM-01`:

- Severidad: `HIGH`.
- Clasificación: `REQUIRES_POLICY_DECISION`.
- Riesgo: un usuario que conoce el UUID de una venta de otra sucursal de la misma organización podría consultar el detalle si posee `sale.read`.
- Motivo para no corregir inmediatamente: debe definirse si propietarios y administradores tienen visibilidad multi-sucursal mientras recepción y gerencia permanecen limitados a su sucursal.
- Corrección propuesta: introducir un alcance explícito de ventas (`CURRENT_BRANCH` o `ORGANIZATION`) resuelto en backend; no inferirlo sólo por el rol visual.
- Pruebas necesarias: recepción A no ve venta B; gerente A no muta pago B; propietario con alcance organizacional sí puede consultar según política aprobada.

### Mutaciones de pagos

Las operaciones de comprobante, validación y rechazo localizan pagos por `organization_id`. Algunas verifican después la venta mediante `ensureSale`, pero la revisión inicial no demuestra una política uniforme de sucursal para todas las mutaciones.

Hallazgo `P0-SPM-02`:

- Severidad: `HIGH`.
- Clasificación: `REQUIRES_TESTS_FIRST`.
- Riesgo: validación, rechazo o carga de comprobante cruzada entre sucursales de una misma organización.
- Acción: inventariar `addPayment`, `uploadReceipt`, `validatePayment`, `rejectPayment` y `requestRefund`, y exigir una única función de resolución de venta/pago con alcance explícito.

### Idempotencia de ventas y pagos

La aplicación compara una llave y un hash de solicitud para evitar que una misma llave se reutilice con contenido diferente. Esto es mejor que devolver silenciosamente una operación previa incompatible.

Riesgo residual `P0-SPM-03`:

- Severidad: `HIGH`.
- Clasificación: `DATABASE_VERIFICATION_REQUIRED`.
- La secuencia consulta primero y después inserta. Ante dos solicitudes concurrentes, la protección definitiva depende de índices únicos en PostgreSQL y del tratamiento de `DuplicateKeyException`.
- La migración revisada confirma índice único para reembolsos, pero en este recorrido todavía no se confirmó la restricción equivalente de `sale.idempotency_key` y `payment.idempotency_key` en el esquema final.
- Debe verificarse además que la llave tenga el alcance correcto: organización, operación y, cuando aplique, sucursal.

### Ventas de renovación y cambio de plan

`MembershipService` obtiene la membresía mediante `organizationId + branchId + membershipId`, consulta el plan y precio desde backend y llama al contrato público `SalesOperations`.

Estado: `PARTIAL_VERIFIED`.

Aspectos positivos:

- La membresía ajena se presenta como no encontrada.
- El precio del plan proviene de persistencia backend.
- Los eventos de pago incluyen organización, sucursal, venta y contexto de membresía.
- El consumidor de eventos usa claves de evento procesadas para evitar reprocesamiento simple.

Pendiente:

- prueba concurrente de dos renovaciones con la misma llave;
- confirmar restricción única en venta y pago;
- revisar el comportamiento cuando el cliente no proporciona llave de idempotencia;
- comprobar que eventos duplicados no generen dos membresías;
- definir transacción y recuperación cuando se crea venta/pago pero falla la activación posterior.

### Clave generada cuando el cliente no envía idempotencia

La implementación actual genera una llave distinta mediante UUID cuando no se proporciona una llave. Esto evita colisiones entre operaciones legítimas, pero no protege un reintento de red que llegue sin llave.

Hallazgo `P0-SPM-04`:

- Severidad: `MEDIUM`.
- Clasificación: `CONTRACT_DECISION_REQUIRED`.
- Propuesta: exigir `Idempotency-Key` en mutaciones monetarias desde clientes controlados, o definir una llave estable de operación con ventana/estado claramente documentados.
- No restaurar una llave permanente `operation:membershipId`, porque impediría renovaciones legítimas futuras.

## Prueba añadida

`MembershipTenantIsolationTests.membershipFromAnotherOrganizationOrBranchIsNotVisible`

La prueba verifica que el servicio consulta una membresía usando simultáneamente organización, sucursal e identificador, devuelve `MEMBERSHIP_NOT_FOUND` y no llama ventas, auditoría ni publicación de eventos cuando el recurso no pertenece al contexto.

## Próximos pasos del bloque

1. Localizar las restricciones finales de idempotencia de `sale` y `payment`.
2. Añadir pruebas de reutilización de llave con payload diferente.
3. Definir alcance de lectura y mutación de ventas por rol/sucursal.
4. Centralizar resolución de venta y pago con el alcance aprobado.
5. Añadir pruebas negativas entre sucursales.
6. Revisar reembolsos parciales, límites acumulados y concurrencia.
7. Revisar activación de membresías ante eventos duplicados y fallos parciales.
8. Ejecutar suite backend local antes de cambiar contratos o SQL crítico.

## Validación requerida

```bash
cd backend
./gradlew test --no-daemon
```

Este documento no declara el bloque terminado ni la suite verde.
