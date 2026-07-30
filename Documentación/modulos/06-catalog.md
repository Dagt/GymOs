# Catálogo comercial

Estado: `ESTABILIZADO`

## Implementado

- productos, planes de membresía, servicios y conceptos vendibles;
- categorías, etiquetas, búsqueda, filtros y paginación;
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
