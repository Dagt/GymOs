# 24. Registro de hallazgos y deuda técnica posterior al cierre de catálogo e inventarios

Estado: VIGENTE / ACTUALIZADO AL CIERRE DE INVENTARIOS  
Fecha de revisión: 24 de julio de 2026  
Ramas revisadas: `feature/catalog-inventory-enhancement-plan` / `feature/inventory-operations-hardening`  

## 1. Objetivo

Este documento registra el estado técnico real después de la mejora integral de catálogo e inventarios por sucursal, sesiones, transferencias entre sucursales, conteos físicos, valuación de inventario y resolución de deuda técnica.

Distingue entre:

- problemas corregidos y validados;
- decisiones de negocio vigentes;
- riesgos pendientes de resolver en otras áreas del sistema;
- seguimiento de la rama de inventarios (`feature/inventory-operations-hardening`).

## 2. Alcance y método de revisión

La revisión incluyó:

- contratos REST y reglas de autorización en `SecurityConfig`;
- emisión, renovación y almacenamiento de sesión;
- aislamiento por organización y sucursal;
- operaciones avanzadas de inventario: salidas por consumo interno, transferencias entre sucursales y conteos físicos con varianzas;
- política configurable de stock negativo por sucursal (`branch.allow_negative_stock`);
- resumen de valuación de activos en stock y cálculo automático de reabastecimiento sugerido;
- exportación de reportes de existencias y movimientos en formato CSV;
- pruebas de arquitectura, aplicación, persistencia y frontend.

## 3. Correcciones cerradas en la iteraciones previas

### 3.1 Borradores de datos del gimnasio (Cerrado)
- El resumen usa exclusivamente el objeto persistido del backend.
- Cierre y cancelación descartan borradores.

### 3.2 Duración visible de membresías (Cerrado)
- Vencimiento calculado como `startsOn + durationDays`.

### 3.3 Sesiones simultáneas (Cerrado)
- Uso de `sessionStorage` independiente por pestaña con tokens rotativos.

### 3.4 Catálogo y jerarquía visual (Cerrado)
- Productos y planes por sucursal activa. Categorías compartidas.

---

## 4. Deuda técnica priorizada y estado de resolución

## P0 — Riesgos que deben resolverse antes de escalar producción

### P0.1 Modelo uniforme de autorización
- **Estado**: *PENDIENTE GLOBAL / COMPLETADO EN INVENTARIO*
- **Situación**:
  - Inventario cuenta con sus permisos específicos (`inventory.read`, `inventory.manage`, `inventory.transfer`, `inventory.count`, `inventory.settings.manage`).
  - Resta extender la suite de pruebas de contrato parametrizada sobre endpoints sensibles de Ventas y Socios.

### P0.2 Almacenamiento seguro de refresh token
- **Estado**: *PENDIENTE*
- **Acción**: Migrar refresh token a cookie `HttpOnly`, `Secure` y `SameSite` con rotación por familia.

### P0.3 Consistencia entre venta e inventario
- **Estado**: **[RESUELTO / CERRADO - V44]**
- **Solución Aplicada**:
  - Se formalizó e implementó la columna `allow_negative_stock` en la tabla `branch`.
  - Verificación y decremento atómico bajo bloqueo `FOR UPDATE` en `InventorySaleDeductionRepository` y `InventoryService`.
  - Si la sucursal tiene `allow_negative_stock = FALSE` y una venta o movimiento manual supera las existencias, se rechaza con excepción `INVENTORY_STOCK_INSUFFICIENT` / `INVENTORY_NEGATIVE_STOCK`.
  - Idempotencia cubierta mediante índices únicos en `inventory_movement` (`ux_inventory_movement_sale_product_effect` y `uq_inventory_movement_refund_product`).

### P0.4 Migraciones con datos reales
- **Estado**: *PENDIENTE DE CI EN PRODUCCIÓN*
- **Acción**: Validar ejecuciones sobre snapshots anonimizados y deshabilitar `out-of-order` en producción.

---

## P1 — Deuda funcional y arquitectónica importante

### P1.1 Semántica de fechas de membresía
- **Estado**: *PENDIENTE*
- **Acción**: Estandarizar si `ends_on` es inclusivo o exclusivo usando la zona horaria propia de la sucursal.

### P1.2 Puertos de aplicación en módulos restantes
- **Estado**: **[COMPLETADO EN INVENTARIOS / PENDIENTE EN OTROS MÓDULOS]**
- **Avance**: El módulo `com.gymos.inventory` implementa 100% el patrón `application.port` (`InventoryQueryPort` y `InventoryCommandPort`) aislando la capa de aplicación de la infraestructura JDBC.
- **Pendiente**: Extender el patrón a `memberships` y `sales`.

### P1.3 Producto maestro versus oferta por sucursal
- **Estado**: *PENDIENTE DE ENTIDAD FORMAL `catalog_branch_offer`*

### P1.4 Permisos de membresía separados de caja
- **Estado**: *PENDIENTE*

### P1.5 Reembolsos financieros completos
- **Estado**: *PENDIENTE DE CONCILIACIÓN PASARELA EXTERNA*

### P1.6 Ciclo operativo de archivos en MinIO
- **Estado**: *PENDIENTE DE ALERTAS Y RETENCIÓN*

### P1.7 Estado de sesión centralizado en frontend
- **Estado**: *PENDIENTE DE `AuthProvider` CENTRALIZADO*

---

## P2 — Mantenibilidad, experiencia y operación

### P2.1 Servicios y componentes grandes
- **Estado**: *EN REVISIÓN*

### P2.2 Campos comerciales ocultos pero persistentes
- **Estado**: *PENDIENTE DE DEPRECACIÓN O USO*

### P2.3 Búsqueda y escalabilidad
- **Estado**: *PROBADO CON ÍNDICES DE INVENTARIO*

### P2.4 Accesibilidad del scroll oculto
- **Estado**: *EN SEGUIMIENTO*

---

## 5. Seguimiento del Plan de Inventarios (`feature/inventory-operations-hardening`)

### **Fase I — Integridad y concurrencia** [COMPLETADO 100%]
- [x] Definir política de stock negativo (`branch.allow_negative_stock`).
- [x] Lock atómico al descontar (`FOR UPDATE`).
- [x] Idempotencia de movimientos manuales y por venta/devolución.
- [x] Validación de pertenencia producto-sucursal.
- [x] Pruebas unitarias e integración de concurrencia.

### **Fase II — Operación diaria y transferencias** [COMPLETADO 100%]
- [x] Entradas por compra, recepción, devolución e inicial.
- [x] Salidas por merma, consumo interno (`INTERNAL_CONSUMPTION`) y ajustes.
- [x] Transferencias entre sucursales (`TRANSFER_OUT` y `TRANSFER_IN`).
- [x] Folios, referencias y notas explicativas.
- [x] Autorización mediante permisos `inventory.transfer` e `inventory.manage`.

### **Fase III — Conteos físicos (Auditorías de Stock)** [COMPLETADO 100%]
- [x] Sesiones de conteo físico (`inventory_physical_count` e `inventory_physical_count_item`).
- [x] Captura parcial de conteos contados vs. sistema.
- [x] Cálculo automático de varianzas.
- [x] Aprobación y generación automática de movimientos de ajuste (`ADJUSTMENT_IN` / `ADJUSTMENT_OUT`).
- [x] Auditoría por usuario y fecha.

### **Fase IV — Costos, valuación y reabastecimiento** [COMPLETADO 100%]
- [x] Costo promedio ponderado (`averageCostCents`).
- [x] Endpoint `/api/v1/inventory/summary` con valuación total en MXN.
- [x] Tarjetas métricas en la interfaz de `/inventory`.
- [x] Puntos de reorden y alertas de stock bajo.
- [x] Cálculo automático de **Reabastecimiento Sugerido** (`suggestedReorderQuantity`).

### **Fase V — Reportes y exportación** [COMPLETADO 100%]
- [x] Botonera de exportación a CSV para existencias y valuación (`inventario_existencias_YYYY-MM-DD.csv`).
- [x] Botonera de exportación a CSV para historial de movimientos (`inventario_movimientos_YYYY-MM-DD.csv`).

---

## 6. Criterios de Calidad y Cierre de Inventario [CUMPLIDOS]

- Ninguna operación de inventario afecta otra sucursal.
- Cada cambio produce un registro inmutable en `inventory_movement`.
- No hay doble descuento por reintento de evento.
- Permisos granulares validados en backend (`SecurityConfig`) y frontend.
- Pruebas unitarias de frontend (`npm test`), verificación de tipos TypeScript (`npx tsc --noEmit`) y pruebas de backend (`gradlew.bat test`) pasan en verde sin errores.
