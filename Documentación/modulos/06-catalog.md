# Catálogo comercial

Estado: `ESTABILIZADO`
Última revisión: 27 de agosto de 2026

## Implementado

- productos, planes de membresía, servicios y conceptos vendibles;
- planes con asociación por sucursal y `multiBranchAccess` para elegibilidad
  transversal dentro del mismo tenant;
- categorías, etiquetas, búsqueda, filtros y paginación;
- filtros de URL que reinician la página al cambiar, navegación visible antes
  de la tabla y recuperación de la última página válida después de archivar;
- POS paginado con búsquedas globales, sin perder el carrito al cambiar de página;
- activación, archivado, imágenes y múltiples códigos de barras por variante;
- familias comerciales de organización y variantes vendibles independientes;
- alta masiva de hasta 50 sabores/presentaciones, con configuración común
  capturada una sola vez, filas normales de sabor/código/SKU, overrides y
  aliases bajo demanda, y transacción completa ante colisiones;
- unidad de producto físico guiada por un selector cerrado de valores comunes,
  normalización de etiquetas heredadas y validación canónica en backend;
- disponibilidad y configuración por sucursal;
- integración con POS, ventas, inventario y reembolsos.

## Decisiones vigentes

- el catálogo define qué se vende; inventario controla existencias;
- una membresía no es un producto físico;
- una familia no es vendible ni posee inventario; `catalog_product` sigue siendo
  el SKU vendible para conservar ventas, existencias y auditoría;
- cada código activo es único por organización y cualquiera de sus alias puede
  resolver la variante exacta;
- los defaults comunes se materializan al crear; no existe herencia posterior
  y cada variante continúa editable, vendible e inventariable por separado;
- `catalog_product_family` es una estructura interna: la UX habla de producto
  base, lo crea automáticamente y permite ampliarlo desde el catálogo. Productos
  individuales no exponen sabor ni aliases múltiples salvo bajo opción avanzada;
- los registros históricos conservan snapshots;
- los listados operativos deben ser paginados.

## Pendiente

- pruebas de alto volumen y compatibilidad de listados legacy;
- completar ciclo de archivos e imágenes;
- mantener fronteras con ventas e inventario (`GEN-002`).

Última validación: `CatalogProductServiceTests`,
`CatalogPersistenceIntegrationTests`, `DatabaseMigrationTests`, pruebas
frontend de POS y E2E de alta masiva, 27-ago-2026.

`e2e/pagination.spec.ts` cubre 127 productos, filtros, archivado del último
registro de una página y carrito entre páginas. Esto no equivale a una prueba
de carga productiva ni cierra el ciclo de imágenes de `CAT-001`.
Contrato y límites: [paginación de listados](../../docs/operations/list-pagination.md).
