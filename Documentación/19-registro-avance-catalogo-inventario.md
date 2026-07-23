# 19. Registro de avance de catálogo e inventario

Rama: `feature/catalog-inventory-enhancement-plan`  
Fecha de inicio del registro: 23 de julio de 2026  
Estado actual: `IN_PROGRESS_HARDENING`

Este documento conserva el historial verificable de implementación posterior al plan `16-plan-accion-catalogo-inventario.md` y al prompt `17-prompt-maestro-implementacion-catalogo-inventario.md`. El estado consolidado y los contratos vigentes se mantienen en `18-implementacion-catalogo-inventario.md`.

---

## Iteración 1 — Revisión integral contra el plan

### Objetivo

Contrastar la rama completa con los requisitos funcionales, arquitectónicos, de seguridad, rendimiento, pruebas y documentación.

### Hallazgos principales

- `catalog.cost.read` existía como permiso, pero costo y proveedor se exponían a cualquier usuario con `catalog.read`.
- POS descargaba el catálogo completo, filtraba localmente y podía recibir productos técnicos `MEMBERSHIP_PLAN`.
- El flujo genérico permitía crear productos técnicos sin su `membership_plan` asociado.
- Los reembolsos parciales acumulados no restauraban inventario al completar el monto total pagado.
- `version` se incrementaba, pero no impedía actualizaciones perdidas.
- El slug de una categoría archivada no podía reutilizarse por una restricción global de base de datos.
- Las relaciones de categoría y etiqueta no tenían protección compuesta por organización.
- La eliminación de imágenes dependía de una llamada no durable posterior al commit.
- `InventoryRepository` concentraba consultas, comandos, bloqueo, movimientos y descuento por venta.
- Inventario y POS no estaban preparados para catálogos crecientes.
- La administración frontend de categorías no exponía todas las capacidades del backend.
- El documento 18 marcaba como pendientes funciones que ya existían y omitía deuda nueva.

### Resultado

Se clasificó el bloque como avanzado funcionalmente, pero no terminado. Se priorizó seguridad, integridad y escalabilidad antes de nuevas funciones visuales.

---

## Iteración 2 — Seguridad de costos y separación de planes técnicos

### Objetivo

Aplicar realmente los permisos especializados y evitar productos técnicos huérfanos.

### Cambios realizados

- `ProductSummaryView.visibleTo(...)` redacciona `supplierName` y `referenceCostCents`.
- Listados, detalle, código de barras, imágenes y mutaciones aplican la redacción en backend.
- El frontend oculta campos de costo y proveedor sin `catalog.cost.read`.
- Un usuario sin permiso de costo tampoco puede modificar esos campos mediante payload manual.
- `ProductRequest` genérico acepta sólo `PHYSICAL` y `SERVICE`.
- Creación, edición, estado, archivado y restauración genéricos bloquean `MEMBERSHIP_PLAN`.
- Los planes técnicos se administran exclusivamente desde el flujo atómico de membresías.
- Los listados comunes y el código de barras excluyen `MEMBERSHIP_PLAN`.

### Archivos principales

- `catalog/application/query/ProductSummaryView.java`
- `catalog/application/CatalogProductService.java`
- `catalog/api/ProductRequest.java`
- `catalog/infrastructure/CatalogProductQueryRepository.java`
- `frontend/src/features/catalog/components/CatalogProductDrawer.tsx`
- `frontend/src/app/catalog/[productId]/page.tsx`

### Pruebas añadidas

- redacción de costos;
- preservación de costos en una edición no autorizada;
- rechazo del tipo técnico en el flujo genérico.

### Riesgos residuales

- se requiere una matriz HTTP completa por rol para confirmar todos los matchers de Spring Security.

---

## Iteración 3 — POS escalable e integración de ventas

### Objetivo

Eliminar la descarga completa del catálogo y asegurar que caja use únicamente productos vendibles comunes.

### Cambios realizados

- POS dejó de llamar `GET /catalog/products`.
- Usa `GET /catalog/products/search` con debounce, estado activo, ordenamiento y tamaño acotado.
- Búsqueda por nombre, SKU y código se ejecuta en backend.
- Los planes técnicos quedan excluidos por el contrato de búsqueda común.
- Ventas usa el contrato público `CatalogProductOperations` en lugar de consultar `catalog_product` directamente.

### Archivos principales

- `frontend/src/app/pos/page.tsx`
- `catalog/api/CatalogProductOperations.java`
- `catalog/infrastructure/CatalogProductOperationsAdapter.java`
- `sales/infrastructure/SalesCatalogLookupRepository.java`

### Pruebas añadidas

- Playwright verifica que POS use el endpoint paginado.
- Playwright falla si POS vuelve a solicitar el catálogo completo.

### Riesgos residuales

- falta un endpoint específicamente nombrado como catálogo de ventas; actualmente se reutiliza la búsqueda administrativa común con sus reglas de exclusión.

---

## Iteración 4 — Reembolsos acumulativos e inventario

### Objetivo

Restaurar inventario cuando varios reembolsos parciales completan el total pagado.

### Cambios realizados

- La validación devuelve el monto ya reembolsado.
- El procesador publica `RefundProcessedV1` cuando `alreadyRefunded + amount == paidCents`.
- La reposición conserva idempotencia por reembolso y producto.

### Archivos principales

- `sales/application/SalesRefundValidationService.java`
- `sales/application/SalesRefundService.java`
- `sales/application/SalesRefundProcessor.java`
- `inventory/infrastructure/InventoryRefundAdapter.java`

### Pruebas añadidas

- reembolso parcial sin reposición prematura;
- reembolso total directo;
- reembolsos parciales acumulados que completan el total.

### Pendiente explícito

El modelo actual restaura la venta completa al agotarse el saldo pagado. No modela todavía:

- productos y cantidades devueltas;
- devolución parcial de líneas;
- condición `restock`;
- artículos dañados o no retornables;
- reversión de una devolución.

La evolución recomendada es un evento versionado con líneas: `SaleItemsRefundedV1`.

---

## Iteración 5 — Concurrencia e integridad multi-tenant

### Objetivo

Evitar sobrescrituras silenciosas y reforzar el aislamiento en la base de datos.

### Cambios realizados

- `ProductSummaryView`, contratos frontend y payloads incluyen `version`.
- `PUT /catalog/products/{id}` exige la versión leída.
- El repositorio actualiza con `WHERE version=:expectedVersion`.
- Se devuelven `PRODUCT_VERSION_REQUIRED` y `PRODUCT_CONCURRENT_MODIFICATION`.
- `V36` convierte la unicidad de slug de categoría en índice parcial para categorías no archivadas.
- `V37` agrega restricciones compuestas por `organization_id` para jerarquías, categorías y etiquetas.
- Una categoría inactiva ya asignada puede conservarse al editar el producto, pero no puede asignarse a un producto distinto.

### Archivos principales

- `catalog/application/command/ProductCommand.java`
- `catalog/application/port/CatalogProductCommandPort.java`
- `catalog/infrastructure/CatalogProductCommandRepository.java`
- `V36__catalog_category_active_slug.sql`
- `V37__catalog_tenant_relation_integrity.sql`

### Pruebas añadidas

- versión obsoleta rechazada antes de mutar;
- actualización SQL obsoleta afecta cero filas;
- relación producto-categoría cruzada entre organizaciones rechazada por PostgreSQL;
- reutilización de slug después del archivado.

---

## Iteración 6 — Categorías completas en frontend

### Objetivo

Exponer las capacidades jerárquicas ya disponibles en backend.

### Cambios realizados

El administrador permite:

- crear;
- editar nombre y descripción;
- elegir categoría padre;
- cambiar orden;
- activar y desactivar;
- archivar;
- visualizar raíz o categoría padre.

Los filtros y formularios de producto muestran sólo categorías activas, salvo la categoría inactiva ya asignada al producto que se está editando.

### Archivos principales

- `frontend/src/features/catalog/components/CatalogCategoryManager.tsx`
- `frontend/src/features/catalog/components/CatalogFilters.tsx`
- `frontend/src/features/catalog/components/CatalogProductDrawer.tsx`

---

## Iteración 7 — Imágenes y limpieza durable

### Objetivo

Evitar objetos huérfanos cuando MinIO falla después de una transacción confirmada.

### Cambios realizados

- `V38` crea `catalog_storage_cleanup`.
- Sustitución y eliminación encolan la clave anterior en la misma transacción de catálogo.
- Una carga nueva se elimina al revertirse la transacción.
- Si la eliminación compensatoria falla, también se encola.
- `CatalogStorageCleanupWorker` procesa lotes y reintenta con backoff.
- El frontend indica si existe imagen y permite sustituirla o eliminarla según `catalog.image.manage`.
- El guardado de producto y la carga de imagen reportan éxito parcial de forma explícita.

### Archivos principales

- `catalog/application/port/CatalogStorageCleanupPort.java`
- `catalog/infrastructure/CatalogStorageCleanupRepository.java`
- `catalog/infrastructure/CatalogStorageCleanupWorker.java`
- `catalog/application/CatalogProductImageService.java`
- `V38__catalog_storage_cleanup_queue.sql`

### Pruebas añadidas

- eliminación encola la imagen;
- reemplazo encola la imagen previa;
- rollback elimina la carga nueva;
- respuestas de imagen no filtran costos.

### Riesgos residuales

- falta prueba con MinIO real;
- falta política de purga de tareas completadas;
- el worker mantiene una transacción mientras llama a MinIO y debe medirse bajo carga.

---

## Iteración 8 — Refactor y escalabilidad de inventario

### Objetivo

Eliminar el repositorio monolítico y preparar existencias y movimientos para volumen creciente.

### Cambios realizados

`InventoryRepository` quedó como fachada sin SQL. Se extrajeron:

- `InventoryStockQueryRepository`;
- `InventoryMovementQueryRepository`;
- `InventoryStockCommandRepository`;
- `InventoryMovementCommandRepository`;
- `InventorySaleDeductionRepository`.

Ventas e inventario consultan el producto mediante `CatalogProductOperations`.

Se añadieron contratos paginados:

- `GET /inventory/search`;
- `GET /inventory/movements/search`.

El frontend usa:

- búsqueda remota con debounce;
- ordenamiento por nombre, cantidad y actualización;
- páginas de existencias;
- páginas de movimientos;
- skeletons y estados vacíos;
- un hook especializado `useInventoryController`.

### Pruebas añadidas

- prueba arquitectónica que impide SQL en `InventoryRepository`;
- búsqueda, ordenamiento y páginas de stock en PostgreSQL;
- aislamiento de cantidades por sucursal;
- paginación de movimientos por producto y sucursal.

### Compatibilidad

Los endpoints legacy `GET /inventory` y `GET /inventory/movements` se conservan temporalmente. Deben marcarse como deprecated cuando todos los consumidores se migren.

---

## Iteración 9 — Cobertura frontend

### Objetivo

Proteger los flujos de mayor riesgo de regresión.

### Pruebas Playwright

`frontend/e2e/catalog-inventory.spec.ts` verifica:

- filtros persistidos en URL;
- versión enviada en edición;
- ocultamiento de costos sin permiso;
- ocultamiento de administración de imagen sin permiso;
- búsqueda paginada del POS;
- ausencia de descarga completa del catálogo.

### Pendiente

Ampliar Playwright al flujo con backend y servicios reales:

1. crear producto;
2. asignar categoría;
3. cargar imagen;
4. registrar inventario inicial;
5. vender;
6. comprobar descuento;
7. completar reembolso;
8. comprobar reposición;
9. archivar y conservar historial.

---

## Iteración 10 — Documentación

### Documentos actualizados

- `05-data-model.md`;
- `18-implementacion-catalogo-inventario.md`;
- este registro `19-registro-avance-catalogo-inventario.md`.

### Documentos por mantener sincronizados en el cierre

- `06-api-contracts.md`;
- `13-estado-desarrollo.md`;
- OpenAPI generado o anotaciones equivalentes;
- instrucciones operativas para catálogo e inventario.

---

## Validación pendiente

No se ejecutaron suites en esta sesión porque el acceso disponible permite modificar el repositorio mediante GitHub, pero no ofrece un checkout ejecutable. GitHub tampoco devolvió estados de CI para el commit consultado.

La siguiente validación es obligatoria:

```text
./gradlew clean test
npm run test
npm run lint
npm run typecheck
npm run build
npm run test:e2e
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

También se deben probar Flyway, PostgreSQL y MinIO reales.

---

## Deuda residual priorizada

### P0 — Bloquea cierre

- corregir cualquier fallo de compilación o pruebas del commit final;
- validar V32–V38 sobre PostgreSQL limpio y datos previos;
- ejecutar MinIO real;
- prueba E2E integrada catálogo–POS–inventario–reembolso;
- definir devolución por líneas y cantidades.

### P1 — Recomendado antes de producción amplia

- autorización HTTP completa por permiso y rol;
- `EXPLAIN ANALYZE` con volumen representativo;
- índices trigram si las mediciones los justifican;
- URL firmada o endpoint autorizado para mostrar imágenes;
- deprecación formal de listados legacy no paginados;
- política de purga para tareas de almacenamiento completadas.

### P2 — Evolución posterior

- variantes como agregado independiente cuando exista necesidad real;
- transferencia de inventario entre sucursales;
- compras y proveedores como módulos propios;
- read model separado para métricas de catálogo de gran volumen.

---

## Regla para futuras actualizaciones

Cada nueva iteración debe agregar:

```text
Fecha:
Objetivo:
Cambios:
Archivos:
Pruebas ejecutadas:
Resultado:
Deuda introducida o resuelta:
Siguiente paso:
```

No se marcará el bloque como `DONE` hasta registrar evidencia de todas las validaciones obligatorias.
