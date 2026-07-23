# 18. Implementación de catálogo e inventario

Estado: `IN_PROGRESS`  
Fecha: 23 de julio de 2026  
Rama: `feature/catalog-inventory-enhancement-plan`

## 18.1 Decisiones aplicadas

- El SKU permanece editable por compatibilidad, con unicidad por organización y auditoría.
- El código de barras es opcional, se normaliza a mayúsculas y es único por organización para productos no archivados.
- El ciclo de vida conserva `ACTIVE` e `INACTIVE`; el archivado se representa con `archived_at` y `archived_by`.
- Los productos archivados pueden consultarse explícitamente y restaurarse como `INACTIVE` después de revalidar SKU y código de barras.
- Un producto físico controla inventario sólo cuando `inventory_tracked=true`. Los productos físicos existentes se respaldan con `true`; servicios y planes permanecen en `false`.
- La clasificación V1 usa una categoría principal jerárquica y hasta veinte etiquetas.
- Las jerarquías de categorías impiden ciclos y el archivado se bloquea mientras existan hijos o productos activos.
- Variantes se resuelven en esta etapa con `brand`, `presentation` y `unit_of_measure`.
- Las imágenes se almacenan en MinIO mediante `CatalogImageStoragePort`; PostgreSQL conserva únicamente `image_object_key`.
- La eliminación y sustitución de objetos de imagen ocurre después del commit; una carga nueva se elimina si la transacción revierte.
- Los productos técnicos `MEMBERSHIP_PLAN` continúan separados de productos y servicios comunes en administración y POS.
- Las salidas de inventario por venta sólo pueden originarse desde la integración de ventas.

## 18.2 Contratos implementados

Productos:

- `GET /api/v1/catalog/products`
- `GET /api/v1/catalog/products/search`
- `GET /api/v1/catalog/products/{productId}`
- `POST /api/v1/catalog/products`
- `PUT /api/v1/catalog/products/{productId}`
- `PATCH /api/v1/catalog/products/{productId}/status`
- `POST /api/v1/catalog/products/{productId}/activate`
- `POST /api/v1/catalog/products/{productId}/deactivate`
- `POST /api/v1/catalog/products/{productId}/restore`
- `DELETE /api/v1/catalog/products/{productId}`
- `POST /api/v1/catalog/products/{productId}/image`
- `DELETE /api/v1/catalog/products/{productId}/image`

Categorías:

- `GET /api/v1/catalog/categories`
- `POST /api/v1/catalog/categories`
- `PATCH /api/v1/catalog/categories/{categoryId}`
- `DELETE /api/v1/catalog/categories/{categoryId}`

La búsqueda paginada acepta `q`, `type`, `categoryId`, `status`, `inventoryTracked`, `includeArchived`, `page`, `size` y `sort`. `q` consulta nombre, SKU, código de barras y etiquetas. El tamaño máximo de página es 100.

## 18.3 Arquitectura

- API depende de comandos y vistas de aplicación, nunca de records JDBC.
- Aplicación depende de puertos especializados para productos, planes, categorías, dependencias e imágenes.
- Los endpoints se separaron en controladores de productos, planes, categorías e imágenes.
- Infraestructura separa consultas de productos/planes, comandos de productos/planes, categorías, dependencias y almacenamiento.
- `ArchitectureTests` impide dependencias desde `catalog.api` o `catalog.application` hacia `catalog.infrastructure`.
- Los repositorios de comandos de producto y plan se mantienen separados.
- El bloqueo y actualización de existencias manuales se aisló en `InventoryStockCommandRepository`.

## 18.4 Integraciones

- Inventario lista y descuenta únicamente productos físicos con seguimiento habilitado.
- Los movimientos manuales bloquean la fila de stock mediante `FOR UPDATE` antes de calcular existencias.
- La creación de stock es idempotente con `ON CONFLICT DO NOTHING`.
- El API manual rechaza movimientos `SALE`.
- El descuento por venta ignora productos sin seguimiento y conserva idempotencia.
- POS excluye archivados y permite localizar por nombre, SKU o código de barras.
- Onboarding activa seguimiento de inventario para los productos físicos que crea después de la migración.
- Membresías conservan el flujo atómico de producto técnico y plan.

## 18.5 Frontend

`/catalog` incluye:

- métricas operativas;
- búsqueda con debounce;
- filtros persistidos en URL;
- filtro para incluir archivados;
- ordenamiento y tamaño de página;
- paginación;
- tabla administrativa responsive;
- edición en drawer;
- activación, desactivación, archivado y restauración;
- categoría, etiquetas, código, marca, presentación, unidad e inventario;
- carga de imagen;
- administración de categorías;
- estados de carga, vacío, error y confirmación;
- acciones visibles según permisos.

Los componentes nuevos viven en `features/catalog/components` y los estados especializados en `features/catalog/hooks`.

## 18.6 Migraciones y permisos

- `V32__catalog_commercial_management.sql`: metadatos, archivado, categorías, etiquetas, backfill e índices.
- `V33__catalog_permissions.sql`: permisos especializados y herencia para roles que ya poseían `catalog.manage`.

Permisos:

- `catalog.read`
- `catalog.create`
- `catalog.update`
- `catalog.status.manage`
- `catalog.archive`
- `catalog.category.manage`
- `catalog.image.manage`
- `catalog.cost.read`

`catalog.manage` se conserva como compatibilidad transitoria.

## 18.7 Validación

Las validaciones documentadas antes del bloque de hardening habían quedado verdes. Después de los cambios de hardening de ciclo de vida, imágenes, controladores, categorías y concurrencia de inventario, se requiere ejecutar nuevamente:

- suite backend Gradle;
- migraciones Flyway;
- pruebas de arquitectura;
- pruebas específicas de catálogo e inventario;
- TypeScript;
- ESLint;
- build de producción Next.js;
- Docker Compose y smoke HTTP.

No debe declararse este bloque terminado hasta repetir estas validaciones sobre el commit final.

## 18.8 Pendientes de cierre

- pruebas específicas de producto, categorías, permisos, multi-tenant y concurrencia de inventario;
- prueba E2E con MinIO real para carga, reemplazo, rollback y eliminación;
- separación completa de DTO de inventario respecto de infraestructura;
- política y automatización de reversión de inventario por devoluciones y reembolsos;
- configuración independiente del punto de reorden;
- consulta agregada de métricas del catálogo;
- página dedicada de detalle, dependencias e historial del producto;
- medición del plan de ejecución con volumen representativo;
- evaluación de índice trigram para búsqueda por coincidencia parcial.
