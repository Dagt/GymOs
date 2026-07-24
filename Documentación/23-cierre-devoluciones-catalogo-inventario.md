# 23. Cierre de devoluciones por línea y catálogo–inventario

Estado: `IMPLEMENTED`  
Fecha: 23 de julio de 2026  
Rama: `feature/catalog-inventory-enhancement-plan`

## 23.1 Objetivo

Cerrar el último alcance funcional pendiente del plan de catálogo e inventario: permitir devoluciones parciales por producto y cantidad, distinguiendo entre mercancía que regresa al inventario y mercancía no reposicionable.

## 23.2 Implementación

### Persistencia

La migración `V40__sale_refund_items.sql` agrega `sale_refund_item` con:

- organización y sucursal;
- venta y reembolso;
- línea original de venta;
- producto asociado;
- cantidad devuelta;
- indicador `restock`;
- actor y fecha de creación;
- unicidad por reembolso y línea;
- índices para acumulación y consulta.

### Aplicación de ventas

`RequestRefundCommand` conserva el constructor monetario anterior y acepta opcionalmente `items`.

Cada elemento contiene:

- `saleItemId`;
- `quantity`;
- `restock`.

Las reglas aplicadas son:

1. El monto debe estar dentro del saldo pagado todavía reembolsable.
2. La línea debe pertenecer a la venta.
3. Una línea no puede repetirse dentro de la misma solicitud.
4. La cantidad debe ser positiva.
5. La suma histórica de cantidades devueltas no puede superar la cantidad vendida.
6. Sólo líneas físicas pueden solicitar reposición.
7. Una venta no puede mezclar reembolsos generales y devoluciones itemizadas.
8. La idempotencia compara monto, pago, líneas, cantidades y reposición.
9. La venta se bloquea con `FOR UPDATE` antes de consultar saldos y cantidades acumuladas.

### Eventos

- `RefundProcessedV1` permanece para compatibilidad con reembolsos monetarios que completan el total pagado.
- `SaleItemsRefundedV1` publica las líneas devueltas con producto, tipo, cantidad y decisión de reposición.

### Inventario

`InventoryRefundOperations` permite:

- restauración completa de una venta;
- restauración selectiva de productos.

El listener de inventario:

- conserva sólo líneas con `restock=true`;
- conserva sólo productos físicos;
- consulta `CatalogProductOperations` y exige seguimiento de inventario activo;
- agrupa cantidades por producto;
- genera un único movimiento `RETURN` por reembolso y producto;
- conserva idempotencia mediante el índice de movimientos de reembolso;
- audita la reposición selectiva.

## 23.3 Contrato HTTP

Ruta:

```text
POST /api/v1/sales/{saleId}/refunds
```

Ejemplo:

```json
{
  "paymentId": null,
  "amountCents": 15000,
  "reason": "Devolución parcial",
  "idempotencyKey": "refund-123",
  "items": [
    {
      "saleItemId": "uuid-linea-1",
      "quantity": 1,
      "restock": true
    },
    {
      "saleItemId": "uuid-linea-2",
      "quantity": 1,
      "restock": false
    }
  ]
}
```

Errores de negocio relevantes:

- `REFUND_AMOUNT_INVALID`
- `REFUND_EXCEEDS_REFUNDABLE_BALANCE`
- `REFUND_MODE_MIXED`
- `REFUND_ITEM_REQUIRED`
- `REFUND_ITEM_DUPLICATED`
- `REFUND_ITEM_QUANTITY_INVALID`
- `REFUND_ITEM_NOT_FOUND`
- `REFUND_ITEM_EXCEEDS_SOLD_QUANTITY`
- `REFUND_ITEM_NOT_RESTOCKABLE`
- `IDEMPOTENCY_KEY_REUSED`

## 23.4 Frontend

La ficha de venta incorpora:

- cantidad vendida;
- cantidad acumulada devuelta;
- modo de devolución por productos;
- selección de líneas;
- cantidad máxima disponible;
- decisión de reposición;
- estimación proporcional del monto;
- actualización de la ficha después del reembolso.

## 23.5 Pruebas añadidas

### Ventas

- persistencia de líneas;
- publicación del evento itemizado;
- rechazo al superar cantidades disponibles;
- rechazo de mezcla de modalidades;
- compatibilidad del reembolso monetario anterior;
- idempotencia con contenido de líneas.

### Inventario

- agregación por producto;
- exclusión de líneas sin reposición;
- exclusión de servicios;
- exclusión de productos físicos sin seguimiento;
- llamada de reposición selectiva;
- auditoría.

### Frontend

`frontend/e2e/sales-refunds.spec.ts` valida:

- activación del modo itemizado;
- selección de producto;
- cantidad;
- reposición;
- payload enviado;
- visualización del acumulado devuelto.

### Smoke

`scripts/smoke_catalog_inventory.py` ejecuta:

1. producto temporal con inventario;
2. carga, sustitución y eliminación de imagen;
3. existencia inicial de cinco unidades;
4. venta pagada de dos unidades;
5. verificación de stock en tres;
6. devolución de una unidad reposicionable;
7. verificación de stock en cuatro;
8. devolución de una unidad no reposicionable;
9. verificación de stock final en cuatro;
10. verificación de `refundedQuantity=2`.

## 23.6 Operación y despliegue

Antes de desplegar:

```text
cd backend
./gradlew test --no-daemon

cd ../frontend
npm ci
npm test
npm run lint
npm run typecheck
npm run build
npm run test:e2e

cd ..
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

Flyway aplicará `V40` antes de que la API acepte solicitudes con `items`.

No debe desplegarse una versión de frontend que envíe devoluciones itemizadas contra un backend anterior a `V40`.

## 23.7 Resultado

El último pendiente funcional identificado en la revisión de catálogo e inventario queda implementado y publicado en la rama remota.
