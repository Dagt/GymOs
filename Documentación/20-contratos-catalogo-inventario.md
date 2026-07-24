# 20. Contratos de catálogo e inventario

Estado: `IMPLEMENTED_PENDING_FINAL_VALIDATION`  
Fecha: 23 de julio de 2026  
Rama: `feature/catalog-inventory-enhancement-plan`

Este documento complementa `06-api-contracts.md` con el detalle versionado del bloque de catálogo e inventario. En caso de diferencia durante el hardening, este documento y `18-implementacion-catalogo-inventario.md` describen el comportamiento actual de la rama.

## 20.1 Convenciones

Base path:

```text
/api/v1
```

Todas las operaciones requieren autenticación. El contexto de organización y sucursal se obtiene de `GymOsPrincipal`; no se aceptan identificadores de tenant enviados libremente por el cliente.

Los errores de negocio conservan códigos estables. Los ejemplos principales de este bloque son:

```text
PRODUCT_NOT_FOUND
PRODUCT_SKU_ALREADY_EXISTS
PRODUCT_BARCODE_ALREADY_EXISTS
PRODUCT_TYPE_INVALID
PRODUCT_UPDATE_REQUIRES_PLAN_WORKFLOW
PRODUCT_STATUS_REQUIRES_PLAN_WORKFLOW
PRODUCT_ARCHIVE_REQUIRES_PLAN_WORKFLOW
PRODUCT_RESTORE_REQUIRES_PLAN_WORKFLOW
PRODUCT_VERSION_REQUIRED
PRODUCT_CONCURRENT_MODIFICATION
PRODUCT_INVENTORY_TRACKING_CHANGE_NOT_ALLOWED
CATEGORY_NOT_FOUND
CATEGORY_NOT_AVAILABLE
CATEGORY_ALREADY_EXISTS
CATEGORY_CYCLE_NOT_ALLOWED
PRODUCT_IMAGE_INVALID
INVENTORY_PRODUCT_NOT_FOUND
INVENTORY_NEGATIVE_STOCK
INVENTORY_STOCK_INSUFFICIENT
INVENTORY_SALE_MOVEMENT_RESERVED
```

## 20.2 Permisos

```text
catalog.read
catalog.create
catalog.update
catalog.status.manage
catalog.archive
catalog.category.manage
catalog.image.manage
catalog.cost.read
inventory.read
inventory.manage
```

`catalog.manage` se conserva como compatibilidad transitoria. `platform.admin` puede operar en soporte según el contexto de sesión.

Los campos `supplierName` y `referenceCostCents` sólo son visibles y modificables con:

```text
catalog.cost.read
catalog.manage
platform.admin
```

Sin esos permisos, API devuelve ambos campos como `null`, incluso si existen en PostgreSQL.

## 20.3 Modelo de producto

Respuesta resumida:

```json
{
  "id": "uuid",
  "sku": "AGUA-001",
  "name": "Agua mineral",
  "description": "Botella de 600 ml",
  "productType": "PHYSICAL",
  "commercialSubtype": "GENERAL_PRODUCT",
  "priceCents": 2500,
  "currency": "MXN",
  "taxable": true,
  "status": "ACTIVE",
  "barcode": "750000000001",
  "brand": "Marca",
  "presentation": "600 ml",
  "unitOfMeasure": "PIECE",
  "inventoryTracked": true,
  "displayOrder": 10,
  "imageObjectKey": "catalog/organization/product/object.webp",
  "supplierName": "Proveedor",
  "referenceCostCents": 1200,
  "promotionalPriceCents": 2200,
  "promotionStartsAt": "2026-07-23T00:00:00Z",
  "promotionEndsAt": "2026-07-30T00:00:00Z",
  "categoryId": "uuid",
  "categoryName": "Bebidas",
  "tags": ["fría", "bebida"],
  "archived": false,
  "archivedAt": null,
  "createdAt": "2026-07-23T00:00:00Z",
  "updatedAt": "2026-07-23T00:00:00Z",
  "version": 4
}
```

El flujo genérico sólo permite:

```text
PHYSICAL
SERVICE
```

`MEMBERSHIP_PLAN` se crea mediante `/catalog/membership-plans` y se administra mediante el contrato de planes.

## 20.4 Productos

### Buscar productos

```text
GET /catalog/products/search
```

Parámetros:

```text
q
productType/type
categoryId
status
inventoryTracked
includeArchived
page
size
sort
```

La implementación HTTP actual usa `type` como nombre del parámetro. El tamaño máximo es 100.

Valores de orden soportados:

```text
displayOrder,asc
displayOrder,desc
name,asc
name,desc
createdAt,asc
createdAt,desc
price,asc
price,desc
```

Respuesta:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0
}
```

La búsqueda textual consulta nombre, SKU, código de barras y etiquetas. Si no se envía tipo, se excluyen productos `MEMBERSHIP_PLAN`.

### Listado legacy

```text
GET /catalog/products
```

Devuelve productos y servicios comunes no archivados. Excluye `MEMBERSHIP_PLAN`. Se conserva por compatibilidad y no debe utilizarse en pantallas con crecimiento no acotado.

### Obtener producto

```text
GET /catalog/products/{productId}
```

### Detalle operativo

```text
GET /catalog/products/{productId}/detail?historyLimit=50
```

Respuesta:

```json
{
  "product": {},
  "dependencies": {
    "saleItems": 0,
    "inventoryMovements": 0,
    "branchesWithStock": 0,
    "activeMembershipPlans": 0,
    "activeProductsInCategory": 0
  },
  "history": []
}
```

### Métricas

```text
GET /catalog/products/summary
```

Incluye productos, servicios y planes activos, productos inactivos o archivados, seguimiento de inventario, productos sin categoría, código o imagen y bajo inventario.

### Búsqueda exacta por código

```text
GET /catalog/products/by-barcode/{barcode}
```

Devuelve únicamente un producto común activo y no archivado.

### Crear producto

```text
POST /catalog/products
```

Request:

```json
{
  "sku": "AGUA-001",
  "name": "Agua mineral",
  "description": "Botella de 600 ml",
  "productType": "PHYSICAL",
  "commercialSubtype": "GENERAL_PRODUCT",
  "priceCents": 2500,
  "currency": "MXN",
  "taxable": true,
  "status": "ACTIVE",
  "barcode": "750000000001",
  "brand": "Marca",
  "presentation": "600 ml",
  "unitOfMeasure": "PIECE",
  "inventoryTracked": true,
  "displayOrder": 10,
  "supplierName": "Proveedor",
  "referenceCostCents": 1200,
  "promotionalPriceCents": null,
  "promotionStartsAt": null,
  "promotionEndsAt": null,
  "categoryId": null,
  "tags": [],
  "version": null
}
```

`version` se omite o envía `null` al crear.

### Editar producto

```text
PUT /catalog/products/{productId}
```

Usa el mismo request de creación, pero `version` es obligatorio y debe coincidir con la versión leída.

Conflictos:

```text
PRODUCT_VERSION_REQUIRED
PRODUCT_CONCURRENT_MODIFICATION
```

El contrato implementado es reemplazo completo mediante `PUT`. No existe todavía un `PATCH` parcial general; el `PATCH` disponible se reserva al estado.

### Cambiar estado

```text
PATCH /catalog/products/{productId}/status
POST /catalog/products/{productId}/activate
POST /catalog/products/{productId}/deactivate
```

Request del `PATCH`:

```json
{
  "status": "INACTIVE"
}
```

### Archivar y restaurar

```text
DELETE /catalog/products/{productId}
POST /catalog/products/{productId}/restore
```

La restauración deja el producto en `INACTIVE`.

## 20.5 Imágenes

```text
POST /catalog/products/{productId}/image
DELETE /catalog/products/{productId}/image
```

Carga multipart:

```text
file: JPG, PNG o WebP
max: 5 MB
```

La clave anterior se agrega a `catalog_storage_cleanup`. Un worker intenta eliminarla de MinIO y reprograma fallos temporales.

## 20.6 Categorías

```text
GET /catalog/categories?includeInactive=false
POST /catalog/categories
PATCH /catalog/categories/{categoryId}
DELETE /catalog/categories/{categoryId}
```

Request:

```json
{
  "parentId": null,
  "name": "Bebidas",
  "description": "Productos líquidos",
  "status": "ACTIVE",
  "displayOrder": 10
}
```

Reglas:

- la categoría padre debe pertenecer a la misma organización;
- no se permiten ciclos;
- no puede archivarse con hijos activos;
- no puede archivarse con productos activos;
- un slug archivado puede reutilizarse.

## 20.7 Planes

```text
GET /catalog/plans
POST /catalog/plans
POST /catalog/membership-plans
PATCH /catalog/plans/{planId}
```

`POST /catalog/membership-plans` crea atómicamente `catalog_product` de tipo `MEMBERSHIP_PLAN` y `membership_plan`.

## 20.8 Inventario

### Búsqueda paginada de existencias

```text
GET /inventory/search
```

Parámetros:

```text
q
page
size
sort
```

Ordenamientos:

```text
name,asc
name,desc
quantity,asc
quantity,desc
updatedAt,desc
```

Respuesta:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0
}
```

La consulta opera sobre la sucursal activa y sólo incluye productos físicos con `inventoryTracked=true`.

### Listado legacy

```text
GET /inventory
```

Se conserva temporalmente para compatibilidad. Los consumidores nuevos deben usar `/inventory/search`.

### Detalle y bajo inventario

```text
GET /inventory/{productId}
GET /inventory/low-stock
```

`low-stock` devuelve como máximo cien alertas ordenadas por cantidad.

### Movimientos paginados

```text
GET /inventory/movements/search
```

Parámetros:

```text
productId
page
size
```

### Movimientos legacy

```text
GET /inventory/movements?productId=&limit=50
```

### Crear movimiento manual

```text
POST /inventory/movements
```

Request:

```json
{
  "productId": "uuid",
  "movementType": "PURCHASE",
  "quantity": 10,
  "unitCostCents": 1200,
  "referenceType": "PURCHASE_ORDER",
  "referenceId": "uuid",
  "reason": "Compra de proveedor"
}
```

Tipos permitidos:

```text
INITIAL
PURCHASE
ADJUSTMENT_IN
ADJUSTMENT_OUT
WASTE
RETURN
```

`SALE` está reservado a la integración automática.

### Punto de reorden

```text
PUT /inventory/products/{productId}/settings
```

Request:

```json
{
  "reorderPoint": 10
}
```

## 20.9 Integración venta e inventario

Al completar un pago:

1. ventas publica `PaymentCompletedV1`;
2. inventario agrupa cantidades por producto;
3. consulta el producto mediante `CatalogProductOperations`;
4. ignora productos sin seguimiento;
5. bloquea stock;
6. valida saldo no negativo;
7. registra movimiento `SALE` idempotente.

Cuando la suma de reembolsos procesados alcanza el total pagado, ventas publica `RefundProcessedV1` e inventario repone la venta completa de forma idempotente.

La devolución por línea y cantidad no forma parte todavía de este contrato.

## 20.10 Deprecaciones previstas

Después de migrar todos los consumidores y ejecutar pruebas de regresión se deberán marcar como deprecated:

```text
GET /catalog/products
GET /inventory
GET /inventory/movements
```

No deben eliminarse antes de confirmar que onboarding, POS, reportes y cualquier cliente externo ya usan contratos paginados o especializados.

## 20.11 Validación pendiente

Los contratos requieren validación final mediante:

- pruebas backend completas;
- Testcontainers PostgreSQL;
- Playwright;
- build de producción;
- Docker Compose;
- smoke HTTP;
- MinIO real.

No se considera contrato estable para release hasta que esos resultados queden registrados en `19-registro-avance-catalogo-inventario.md`.
