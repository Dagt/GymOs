# Revisión de mutaciones de pagos por sucursal

## Estado

`IN_PROGRESS`

Este documento caracteriza el alcance de sucursal de las operaciones de escritura sobre ventas y pagos.

## Resultado por operación

### `addPayment`

Estado: `BRANCH_GUARDED`.

La operación llama `ensureSale(principal, saleId)` antes de insertar el pago. `ensureSale` obtiene la venta por organización y rechaza cuando `sale.branchId()` no coincide con `principal.branchId()`.

Prueba añadida:

`SalesPaymentBranchIsolationTests.addingPaymentToSaleFromAnotherBranchIsDeniedBeforeMutation`

La prueba verifica `SALE_BRANCH_MISMATCH` y ausencia de SQL, auditoría, almacenamiento y eventos.

### `uploadReceipt`

Estado: `BRANCH_GUARDED`.

La operación resuelve el `saleId` del pago y después llama `ensureSale` antes de almacenar el archivo o actualizar la base de datos.

Prueba añadida:

`SalesPaymentBranchIsolationTests.uploadingReceiptToPaymentFromAnotherBranchIsDeniedBeforeStorageOrMutation`

La prueba verifica que una venta de otra sucursal sea rechazada antes de escribir el comprobante o ejecutar SQL.

### `validatePayment`

Estado: `BRANCH_GAP`.

La operación localiza el pago por `organizationId + paymentId`, pero no llama `ensureSale` antes de marcarlo como `COMPLETED`. Por tanto, el código revisado no impide que un usuario con el permiso correspondiente valide un pago perteneciente a otra sucursal de la misma organización si conoce su UUID.

Hallazgo: `P0-PBM-01`.

- Severidad: `HIGH`.
- Corrección mínima propuesta: resolver la venta y exigir el mismo alcance de sucursal antes de consultar el estado o ejecutar el `UPDATE`.
- Corrección estructural propuesta: una única función `resolvePaymentForWrite(principal, paymentId)` que devuelva conjuntamente venta y pago ya autorizados.

### `rejectPayment`

Estado: `BRANCH_GAP`.

Presenta el mismo patrón que `validatePayment`: localiza por organización y pago, pero no verifica la sucursal de la venta antes del cambio de estado y la publicación del evento `PaymentRejectedV1`.

Hallazgo: `P0-PBM-02`.

- Severidad: `HIGH`.
- Riesgo adicional: el evento rechazado se publica con la sucursal activa del actor, no necesariamente con la sucursal real de la venta, lo que puede contaminar consumidores y auditoría.

### `requestRefund`

Estado: `BRANCH_GUARDED`.

Además de usar `ensureSale`, contiene una comprobación explícita de igualdad de sucursal. Existe prueba negativa separada.

## Decisión de implementación

No se modificaron todavía `validatePayment` ni `rejectPayment` porque ambas son rutas monetarias y el cambio debe acompañarse de pruebas ejecutables. El siguiente cambio seguro debe:

1. introducir un resolvedor común de pago y venta;
2. usar la sucursal real de la venta en eventos;
3. añadir pruebas negativas para validación y rechazo cruzados;
4. conservar idempotencia de estados ya `COMPLETED` o `REJECTED`;
5. ejecutarse contra la suite backend completa antes de continuar con SQL crítico.

## Validación requerida

```bash
cd backend
./gradlew test --no-daemon
```

Este documento no declara la suite verde.
