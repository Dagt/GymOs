# 18. Implementación de catálogo e inventario

Estado: `IN_PROGRESS`  
Fecha: 23 de julio de 2026  
Rama: `feature/catalog-inventory-enhancement-plan`

## 18.1 Decisiones aplicadas

- El SKU permanece editable por compatibilidad, con unicidad por organización y auditoría.
- El código de barras es opcional, se normaliza a mayúsculas y es único por organización para productos no archivados.
- El ciclo de vida conserva `ACTIVE` e `INACTIVE`; el archivado se representa con `archived_at` y `archived_by`.
- Un producto físico controla inventario sólo cuando `inventory_tracked=true`. Los productos físicos existentes se respaldan con `true`; servicios y planes permanecen en `false`.
- La clasificación V1 usa una categoría principal jerárquica y hasta veinte etiquetas.
- Variantes se resuelven en esta etapa con `brand`, `presentation` y `unit_of_measure`. SKU, código e inventario siguen perteneciendo a la unidad vendible; una tabla de variantes se incorporará sólo cuando el piloto lo requiera.
- Las imágenes se almacenan en MinIO mediante `CatalogImageStoragePort`; PostgreSQL conserva únicamente `image_object_key`.
- Los productos técnicos `MEMBERSHIP_PLAN` continúan separados de productos y servicios comunes en administración y POS.

## 18.2 Contratos implementados

Productos:

- `GET /api/v1/catalog/products`
- `GET /api/v1/catalog/products/search`
- `GET /api/v1/catalog/products/{productId}`
- `POST /api/v1/catalog/products`
- `PUT|PATCH /api/v1/catalog/products/{productId}`
- `PATCH /api/v1/catalog/products/{productId}/status`
- `POST /api/v1/catalog/products/{productId}/activate`
- `POST /api/v1/catalog/products/{productId}/deactivate`
- `DELETE /api/v1/catalog/products/{productId}`
- `POST /api/v1/catalog/products/{productId}/image`
- `DELETE /api/v1/catalog/products/{productId}/image`

Categorías:

- `GET /api/v1/catalog/categories`
- `POST /api/v1/catalog/categories`
- `PATCH /api/v1/catalog/categories/{categoryId}`
- `DELETE /api/v1/catalog/categories/{categoryId}`

La búsqueda paginada acepta `q`, `type`, `categoryId`, `status`, `inventoryTracked`, `includeArchived`, `page`, `size` y `sort`. `q` consulta nombre, SKU, código de barras y etiquetas.

## 18.3 Arquitectura

- API depende de comandos y vistas de aplicación, nunca de records JDBC.
- Aplicación depende de puertos especializados para productos, planes, categorías, dependencias e imágenes.
- Infraestructura separa consultas de productos/planes, comandos de productos/planes, categorías, dependencias y almacenamiento.
- `ArchitectureTests` impide dependencias desde `catalog.api` o `catalog.application` hacia `catalog.infrastructure`.
- Los repositorios de comandos de producto y plan se mantienen separados para evitar una fachada de escritura monolítica.

## 18.4 Integraciones

- Inventario lista y descuenta únicamente productos físicos con seguimiento habilitado.
- El descuento por venta ignora productos sin seguimiento y conserva idempotencia.
- POS excluye archivados y permite localizar por nombre, SKU o código de barras compatible con lectores tipo teclado.
- Onboarding activa seguimiento de inventario para los productos físicos que crea después de la migración.
- Membresías conservan el flujo atómico de producto técnico y plan.

## 18.5 Frontend

`/catalog` incluye:

- métricas operativas;
- búsqueda con debounce;
- filtros persistidos en URL;
- paginación;
- tabla administrativa responsive;
- edición en drawer;
- activación, desactivación y archivado;
- categoría, etiquetas, código, marca, presentación, unidad e inventario;
- carga de imagen;
- administración de categorías;
- estados de carga, vacío, error y confirmación;
- acciones visibles según permisos.

Los componentes nuevos viven en `features/catalog/components` y los estados especializados en `features/catalog/hooks`. `app/catalog/page.tsx` conserva únicamente composición de alto nivel.

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

`catalog.manage` se conserva como compatibilidad transitoria para clientes y tokens anteriores.

## 18.7 Validación

Validaciones ejecutadas durante la implementación:

- suite backend Gradle;
- migraciones Flyway;
- pruebas de arquitectura;
- pruebas frontend;
- TypeScript;
- ESLint;
- build de producción Next.js.
- `docker compose config`;
- build de imágenes backend/frontend;
- smoke HTTP con backend `200/UP` y `/catalog` `200`.

La suite final quedó verde y las imágenes locales se recrearon sin reiniciar PostgreSQL, Redis o MinIO.

## 18.8 Pendientes de cierre

- prueba E2E con MinIO real que cubra carga, reemplazo y eliminación;
- medición del plan de ejecución con un volumen representativo del piloto;
- página dedicada de historial y dependencias del producto.
