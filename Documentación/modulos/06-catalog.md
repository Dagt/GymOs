# Catálogo comercial

Estado: `ESTABILIZADO`
Última revisión: 25 de agosto de 2026

## Implementado

- productos, planes de membresía, servicios y conceptos vendibles;
- planes con asociación por sucursal y `multiBranchAccess` para elegibilidad
  transversal dentro del mismo tenant;
- categorías, etiquetas, búsqueda, filtros y paginación;
- filtros de URL que reinician la página al cambiar, navegación visible antes
  de la tabla y recuperación de la última página válida después de archivar;
- POS paginado con búsquedas globales, sin perder el carrito al cambiar de página;
- activación, archivado, imágenes y código de barras;
- unidad de producto físico guiada por un selector cerrado de valores comunes,
  normalización de etiquetas heredadas y validación canónica en backend;
- disponibilidad y configuración por sucursal;
- integración con POS, ventas, inventario y reembolsos.

## Decisiones vigentes

- el catálogo define qué se vende; inventario controla existencias;
- una membresía no es un producto físico;
- los registros históricos conservan snapshots;
- los listados operativos deben ser paginados.

## Pendiente

- pruebas de alto volumen y compatibilidad de listados legacy;
- completar ciclo de archivos e imágenes;
- mantener fronteras con ventas e inventario (`GEN-002`).

Última validación: `CatalogProductServiceTests`, pruebas frontend de unidades y
E2E del selector cerrado, 30-jul-2026.

`e2e/pagination.spec.ts` cubre 127 productos, filtros, archivado del último
registro de una página y carrito entre páginas. Esto no equivale a una prueba
de carga productiva ni cierra el ciclo de imágenes de `CAT-001`.
Contrato y límites: [paginación de listados](../../docs/operations/list-pagination.md).
