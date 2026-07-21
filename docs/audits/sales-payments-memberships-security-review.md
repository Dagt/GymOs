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

La solicitud de reembolso sí contiene una comprobación explícita entre `principal.branchId()` y `sale.branchId()`. Se añadió una prueba para conservar este comportamiento y garantizar que no se produzcan mutaciones, auditoría ni eventos antes del rechazo.

### Idempotencia de ventas y pagos

La aplicación compara una llave y un hash de solicitud para evitar que una misma llave se reutilice con contenido diferente. Esto es mejor que devolver silenciosamente una operación previa incompatible.

Riesgo residual `P0-SPM-03`:

- Severidad: `HIGH`.
- Clasificación: `DATABASE_VERIFICATION_REQUIRED`.
- La secuencia consulta primero y después inserta. Ante dos solicitudes concurrentes, la protección definitiva depende de índices únicos en PostgreSQL y del tratamiento de `DuplicateKeyException`.
- La migración revisada confirma índice único para reembolsos, pero en este recorrido todavía no se confirmó la restricción equivalente de `sale.idempotency_key` y `payment.idempotency_key` en el esquema final.
- Debe verificarse además que la llave tenga el alcance correcto: organización, operación y, cuando aplique, sucursal.

### Reembolsos acumulados

La implementación calcula correctamente el total ya reembolsado de una venta y evita que el acumulado supere `sale.paidCents()`.

Sin embargo, cuando el reembolso se vincula a un pago concreto sólo comprueba que cada solicitud individual no supere `payment.amountCents()`. No descuenta los reembolsos procesados anteriormente sobre ese mismo pago.

Hallazgo `P0-SPM-05`:

- Severidad: `HIGH`.
- Clasificación: `REQUIRES_TESTS_FIRST`.
- Escenario: una venta tiene dos pagos completados de 500 cada uno. El pago A puede recibir dos reembolsos de 500 mientras el total global de la venta siga permitiéndolo, aunque el segundo reembolso debería corresponder al pago B.
- Riesgo: trazabilidad contable incorrecta por pago y conciliación inconsistente con el medio de cobro original.
- Corrección propuesta: calcular `processedRefundTotal(organizationId, saleId, paymentId)` cuando exista `paymentId` y validar contra `payment.amountCents() - refundedForPayment`.
- Concurrencia: la comprobación debe ejecutarse con bloqueo o restricción/transacción capaz de impedir dos reembolsos simultáneos que excedan el saldo del pago o de la venta.

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
- definir transacción y recuperación cuando se crea venta/pago pero falla la activación posterior.

### Idempotencia del consumidor de membresías

`MembershipService.onPaymentCompleted` intenta registrar primero `organizationId + eventKey + eventType` mediante `MembershipRepository.markEventProcessed`. Si la inserción provoca `DuplicateKeyException`, el repositorio devuelve `false` y el consumidor termina antes de consultar planes, cancelar membresías, insertar una membresía, auditar o emitir eventos derivados.

Estado: `CHARACTERIZED`.

Se añadió una prueba para fijar el comportamiento secuencial de eventos duplicados. Todavía debe verificarse en el esquema final que `processed_domain_event` tenga una restricción única compatible con el alcance esperado. También debe confirmarse una segunda barrera de unicidad para la membresía creada, idealmente por `organization_id + sale_id + plan_id` o por `organization_id + source_payment_event_key`, para proteger ejecuciones concurrentes o futuras rutas que omitan el registro de evento.

Hallazgo `P0-SPM-06`:

- Severidad: `HIGH`.
- Clasificación: `DATABASE_VERIFICATION_REQUIRED`.
- Riesgo: la idempotencia del consumidor depende de una restricción única no confirmada en este recorrido.
- Riesgo adicional: `bySaleAndPlan` usa el patrón consultar-antes-de-insertar; dos ejecuciones concurrentes necesitan una restricción única en base de datos para impedir dos membresías.
- Acción: revisar migraciones completas, añadir prueba de integración PostgreSQL y tratar explícitamente una colisión concurrente devolviendo la membresía ya existente.

### Clave generada cuando el cliente no envía idempotencia

La implementación actual genera una llave distinta mediante UUID cuando no se proporciona una llave. Esto evita colisiones entre operaciones legítimas, pero no protege un reintento de red que llegue sin llave.

Hallazgo `P0-SPM-04`:

- Severidad: `MEDIUM`.
- Clasificación: `CONTRACT_DECISION_REQUIRED`.
- Propuesta: exigir `Idempotency-Key` en mutaciones monetarias desde clientes controlados, o definir una llave estable de operación con ventana/estado claramente documentados.
- No restaurar una llave permanente `operation:membershipId`, porque impediría renovaciones legítimas futuras.

## Pruebas añadidas

### Membresías

`MembershipTenantIsolationTests.membershipFromAnotherOrganizationOrBranchIsNotVisible`

Verifica que el servicio consulta una membresía usando simultáneamente organización, sucursal e identificador, devuelve `MEMBERSHIP_NOT_FOUND` y no llama ventas, auditoría ni publicación de eventos cuando el recurso no pertenece al contexto.

`MembershipEventIdempotencyTests.duplicatePaymentCompletedEventIsIgnoredBeforeMembershipMutation`

Verifica que un `PaymentCompletedV1` ya registrado se ignora antes de consultar planes, insertar o cancelar membresías, registrar auditoría o publicar eventos derivados.

### Reembolsos de ventas

`SalesBranchIsolationTests.refundForSaleFromAnotherBranchIsDeniedBeforeAnyMutation`

Verifica que una solicitud de reembolso sobre una venta perteneciente a otra sucursal de la misma organización devuelve `SALE_NOT_FOUND` antes de acceder a SQL de escritura, almacenamiento, auditoría o eventos.

`SalesRefundIdempotencyTests.reusedRefundKeyWithDifferentAmountIsRejectedBeforeMutation`

Verifica que una llave de reembolso existente no pueda reutilizarse con un importe diferente y que el rechazo ocurra antes de cualquier efecto secundario.

## Próximos pasos del bloque

1. Localizar las restricciones finales de idempotencia de `sale`, `payment` y `processed_domain_event`.
2. Confirmar una restricción única para membresías derivadas de una venta/evento.
3. Definir alcance de lectura y mutación de ventas por rol/sucursal.
4. Centralizar resolución de venta y pago con el alcance aprobado.
5. Añadir pruebas negativas para validar, rechazar y cargar comprobantes entre sucursales.
6. Añadir prueba de reembolsos acumulados por pago y corregir el cálculo.
7. Añadir pruebas concurrentes de reembolsos y activación de membresías.
8. Ejecutar suite backend local antes de cambiar contratos o SQL crítico.

## Validación requerida

```bash
cd backend
./gradlew test --no-daemon
```

Este documento no declara el bloque terminado ni la suite verde.
