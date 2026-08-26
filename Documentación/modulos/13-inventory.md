# Inventarios

Estado: `ESTABILIZADO`
Última revisión: 25 de agosto de 2026

## Implementado

- existencias por sucursal;
- movimientos manuales y automáticos;
- transferencias con validación de stock y recepción;
- conteos físicos y ajustes;
- costos, valuación y alertas de reabastecimiento;
- integración con ventas, devoluciones y catálogo;
- frontend separado en existencias, movimientos, transferencias y auditorías.
- existencias, movimientos, alertas, transferencias y conteos paginados en
  servidor, con totales filtrados y navegación explícita;
- búsqueda global de movimientos por nombre, SKU y motivo, combinada con
  producto/tipo antes de paginar;
- selectores de producto independientes de la página de existencias y señales
  de conteo/transferencia activos independientes de la página del historial;
- exportaciones identificadas como **Exportar página CSV**: sólo incluyen las
  filas de la página seleccionada, no el inventario completo.

## Pendiente

- indicador de guardado por renglón (`INV-002`);
- pruebas de concurrencia y alto volumen.

Base histórica: PR #6. Última revisión dentro del árbol integrado actual:
25-ago-2026. `INV-001` queda resuelta con los contratos `/search`,
`InventoryPaginationIntegrationTests` sobre 127 registros y la suite E2E de
paginación. No se modifica el esquema ni el ciclo de aprobación de conteos.
Contrato y límites: [paginación de listados](../../docs/operations/list-pagination.md).
