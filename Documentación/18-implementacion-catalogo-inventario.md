# 18. Implementación de catálogo e inventario

Estado: `IMPLEMENTATION_COMPLETE`  
Fecha de cierre funcional: 23 de julio de 2026  
Rama: `feature/catalog-inventory-enhancement-plan`

## 18.1 Alcance completado

El plan de catálogo e inventario queda implementado en la rama de trabajo con:

- administración integral de productos y servicios;
- edición con control optimista mediante `version`;
- estados `ACTIVE` e `INACTIVE`, archivado lógico y restauración;
- categoría jerárquica principal y etiquetas;
- código de barras, marca, presentación, unidad de medida e información comercial;
- costo y proveedor protegidos por `catalog.cost.read`;
- seguimiento de inventario configurable por producto físico;
- imágenes mediante puerto de almacenamiento y MinIO;
- cola durable para limpieza y reintento de objetos sustituidos o eliminados;
- búsqueda, filtros, ordenamiento y paginación;
- métricas, detalle, dependencias e historial de producto;
- POS desacoplado de la carga completa del catálogo;
- inventario paginado y separado en repositorios por intención;
- descuento automático e idempotente de existencias por venta pagada;
- reembolsos monetarios completos o parciales acumulados;
- devoluciones por línea, cantidad y decisión explícita de reposición.

## 18.2 Decisiones funcionales vigentes

- El SKU permanece editable por compatibilidad, con unicidad por organización y auditoría.
- El código de barras es opcional, se normaliza a mayúsculas y es único por organización para productos no archivados.
- Los productos archivados conservan referencias históricas y se restauran como `INACTIVE`.
- Sólo un producto `PHYSICAL` con `inventoryTracked=true` puede modificar existencias.
- Los productos técnicos `MEMBERSHIP_PLAN` sólo se administran mediante el flujo atómico de membresías.
- Las variantes V1 se representan mediante `brand`, `presentation` y `unitOfMeasure`.
- Los costos y proveedor se omiten de API, auditoría y frontend cuando el actor no tiene `catalog.cost.read`.
- Las salidas `SALE` sólo se generan desde la integración de ventas.
- Una venta no puede mezclar reembolsos monetarios generales con devoluciones itemizadas.
- Las cantidades devueltas se acumulan por `sale_item` y nunca pueden superar la cantidad vendida.
- `restock=true` sólo se acepta para líneas físicas; inventario además confirma que el producto sigue teniendo seguimiento habilitado.
- Los productos dañados o no reposicionables se registran como devueltos sin incrementar existencias.
- Cada reembolso es idempotente por llave, incluyendo sus líneas, cantidades y decisión de reposición.
- La venta se bloquea con `FOR UPDATE` durante la validación del reembolso para evitar carreras de saldo o cantidad.

## 18.3 Contratos implementados

### Productos

- `GET /api/v1/catalog/products/search`
- `GET /api/v1/catalog/products/summary`
- `GET /api/v1/catalog/products/{productId}`
- `GET /api/v1/catalog/products/{productId}/detail`
- `GET /api/v1/catalog/products/by-barcode/{barcode}`
- `POST /api/v1/catalog/products`
- `PUT /api/v1/catalog/products/{productId}`
- `PATCH /api/v1/catalog/products/{productId}/status`
- `POST /api/v1/catalog/products/{productId}/restore`
- `DELETE /api/v1/catalog/products/{productId}`
- `POST /api/v1/catalog/products/{productId}/image`
- `DELETE /api/v1/catalog/products/{productId}/image`

Los listados completos de compatibilidad se mantienen temporalmente con encabezados de deprecación; las pantallas operativas utilizan contratos paginados.

### Categorías

- `GET /api/v1/catalog/categories`
- `POST /api/v1/catalog/categories`
- `PATCH /api/v1/catalog/categories/{categoryId}`
- `DELETE /api/v1/catalog/categories/{categoryId}`

### Inventario

- `GET /api/v1/inventory/search`
- `GET /api/v1/inventory/{productId}`
- `GET /api/v1/inventory/low-stock`
- `GET /api/v1/inventory/movements/search`
- `POST /api/v1/inventory/movements`
- `PUT /api/v1/inventory/products/{productId}/settings`

### Reembolsos y devoluciones

- `POST /api/v1/sales/{saleId}/refunds`

Reembolso monetario compatible:

```json
{
  "paymentId": null,
  "amountCents": 2000,
  "reason": "Reembolso general",
  "idempotencyKey": "refund-uuid"
}
```

Devolución itemizada:

```json
{
  "paymentId": null,
  "amountCents": 1000,
  "reason": "Producto devuelto",
  "idempotencyKey": "refund-item-uuid",
  "items": [
    {
      "saleItemId": "uuid",
      "quantity": 1,
      "restock": true
    }
  ]
}
```

La respuesta de detalle de venta incluye `refundedQuantity` por línea para calcular la cantidad todavía devolvible.

## 18.4 Arquitectura final

- API utiliza comandos, resultados y vistas de aplicación; no expone records JDBC.
- Aplicación de catálogo depende de puertos especializados.
- Ventas e inventario consumen el contrato público `CatalogProductOperations`.
- `InventoryRepository` es una fachada compatible sin SQL directo.
- Consultas de stock, movimientos, comandos, descuentos y devoluciones viven en repositorios especializados.
- `InventoryRefundOperations` constituye el contrato público para reposición completa o selectiva.
- Los eventos `RefundProcessedV1` y `SaleItemsRefundedV1` separan reembolso total compatible y devolución itemizada.
- Las pruebas de arquitectura impiden dependencias desde API/aplicación hacia infraestructura y uso directo de `JdbcClient` en aplicación.

## 18.5 Frontend final

`/catalog` incluye métricas, filtros en URL, debounce, tabla responsive, paginación, detalle, edición, categorías, imágenes, ciclo de vida y permisos.

`/inventory` incluye búsqueda remota, ordenamiento, paginación de existencias y movimientos, ajustes manuales y punto de reorden.

`/pos` consulta productos vendibles de forma remota y no carga planes técnicos como productos comunes.

`/sales/{saleId}` permite:

- consultar cantidad vendida y cantidad ya devuelta;
- registrar reembolso monetario compatible;
- activar modo de devolución por productos;
- seleccionar líneas y cantidades;
- marcar si cada producto vuelve al inventario;
- impedir desde la interfaz cantidades superiores a las disponibles;
- mostrar el acumulado devuelto después de procesar la operación.

## 18.6 Migraciones relevantes

- `V32__catalog_commercial_management.sql`
- `V33__catalog_permissions.sql`
- `V34__inventory_refund_idempotency.sql`
- `V35__catalog_extended_commercial_fields.sql`
- `V36__catalog_category_active_slug.sql`
- `V37__catalog_tenant_relation_integrity.sql`
- `V38__catalog_storage_cleanup_queue.sql`
- `V40__sale_refund_items.sql`

`V40` agrega `sale_refund_item`, unicidad por reembolso y línea, índices de acumulación y la decisión `restock`.

## 18.7 Cobertura incorporada

Backend:

- permisos de costos;
- separación de productos técnicos;
- control optimista de edición;
- aislamiento multi-tenant;
- jerarquías de categorías;
- paginación de catálogo e inventario;
- concurrencia de descuento de existencias;
- reembolso total y parcial acumulado;
- validación de cantidades itemizadas;
- rechazo de modalidades mezcladas;
- evento de devolución itemizada;
- reposición selectiva y agregada por producto;
- exclusión de productos sin seguimiento de inventario.

Frontend y E2E:

- filtros y edición de catálogo;
- protección de costos;
- búsqueda paginada del POS;
- selección de línea, cantidad y reposición en devoluciones;
- payload itemizado y actualización de `refundedQuantity`.

Smoke reproducible:

- `scripts/smoke_catalog_inventory.py` valida producto, imagen, inventario inicial, venta, descuento y dos devoluciones parciales: una reposicionable y otra no reposicionable.

## 18.8 Estado de cierre

No quedan pendientes funcionales dentro del alcance definido por `16-plan-accion-catalogo-inventario.md` y el hardening documentado en esta rama.

La rama remota contiene la implementación final. El workflow `.github/workflows/ci.yml` se activa en cada push de `feature/**` y ejecuta:

- pruebas backend Gradle;
- pruebas frontend;
- ESLint;
- TypeScript;
- build de Next.js;
- validación y build de Docker Compose.

La evidencia histórica y decisiones por iteración se conservan en los documentos 19, 20, 21 y en el acta de cierre del bloque de devoluciones.
