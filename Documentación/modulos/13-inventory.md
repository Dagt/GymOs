# Inventarios

Estado: `ESTABILIZADO`
Última revisión: 27 de agosto de 2026

## Implementado

- existencias por sucursal;
- movimientos manuales y automáticos;
- transferencias con validación de stock y recepción;
- conteos físicos y ajustes;
- costos, valuación y alertas de reabastecimiento;
- integración con ventas, devoluciones y catálogo;
- stock, costos, mínimos y movimientos separados por variante vendible, aunque
  varias variantes pertenezcan a la misma familia comercial;
- búsqueda por cualquiera de los códigos alias de la variante;
- el stock mínimo común del alta sólo inicializa el mismo valor en cada variante;
  después puede editarse por separado sin afectar los demás sabores;
- frontend separado en existencias, movimientos, transferencias y auditorías.
- existencias, movimientos, alertas, transferencias y conteos paginados en
  servidor, con totales filtrados y navegación explícita;
- listados de altura acotada, scroll interno con barra oculta y controles de
  página siempre fuera del scroll; no se confunde una consulta fallida con
  cero existencias o un historial vacío, y se ofrece reintento;
- verificación de capacidad del backend para los nuevos endpoints/filtros,
  evitando que un servidor anterior ignore `lowStockOnly` y muestre todos los
  productos como alertas;
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
paginación. V95 agrega familias/códigos al catálogo sin modificar las claves de
producto usadas por stock, conteos, transferencias o movimientos.
Contrato y límites: [paginación de listados](../../docs/operations/list-pagination.md).

## SAL-001

Point reserva unidades por venta y sede bajo el bloqueo del stock, sin alterar el saldo físico. La venta pagada consume la reserva y escribe el movimiento. Los demás egresos respetan esas unidades.
