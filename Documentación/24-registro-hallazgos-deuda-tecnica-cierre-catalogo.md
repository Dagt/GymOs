# 24. Registro de hallazgos y deuda técnica posterior al cierre de catálogo e inventarios

Estado: VIGENTE / ACTUALIZADO Y AUDITADO AL CIERRE DE INVENTARIOS  
Fecha de revisión: 25 de julio de 2026  
Ramas revisadas: `feature/catalog-inventory-enhancement-plan` / `feature/inventory-operations-hardening`  

## 1. Objetivo

Este documento registra el estado técnico real después de la mejora integral de catálogo e inventarios por sucursal, sesiones, transferencias entre sucursales, conteos físicos, valuación de inventario, corrección de permisos de seguridad y resolución de deuda técnica.

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
- rediseño y estandarización UI/UX según el sistema de diseño de GymOS (`app-card`, `app-input`, `app-button`);
- pruebas de arquitectura, aplicación, persistencia y frontend.

---

## 3. Correcciones cerradas en la iteración de inventarios (`feature/inventory-operations-hardening`)

### 3.1 Consulta de sucursales para Gerentes en transferencias [CERRADO]
- **Diagnóstico**: `SecurityConfig` aplicaba la regla `.requestMatchers("/api/v1/organizations/current/branches/*").hasAuthority("branch.update")` sin restringir el método HTTP `PUT`, interceptando las peticiones `GET /api/v1/organizations/current/branches/all` realizadas por Gerentes (quienes no poseen `branch.update`), respondiendo `403 Forbidden` y provocando que el frontend cayera a la consulta propia sin opciones destino.
- **Solución**: Se acotó la regla a `HttpMethod.PUT` y se ampliaron las autoridades de lectura (`branch.read`, `organization.read`, `inventory.manage`, `inventory.transfer`, `catalog.read`). Los Gerentes ahora consultan y eligen cualquier sucursal de su gimnasio como destino de transferencia.

### 3.2 Publicación automática de catálogo al recibir transferencias [CERRADO]
- **Diagnóstico**: Si la sucursal B recibía una transferencia de un producto creado en la sucursal A (existente en `catalog_product` de la organización pero aún no asociado a `catalog_product_branch` de la sucursal B), la recepción fallaba con `PRODUCT_NOT_FOUND`.
- **Solución**: En `InventoryService.ensureTrackedProduct`, si el producto existe a nivel organización pero no en la sucursal activa, se invoca automáticamente `catalog.attachBranch` para asociar la ficha del producto al catálogo local de la sucursal B e incrementar su existencia.

### 3.3 Validación preventiva de stock en solicitudes de transferencia [CERRADO]
- **Diagnóstico**: Se permitía agregar cantidades ilimitadas en la interfaz de transferencia y la validación de inventario insuficiente fallaba únicamente hasta el momento de enviar.
- **Solución**: Se incorporó validación inmediata tanto en el modal del frontend (`addItem`) como en el backend (`InventoryService.createTransfer`), rechazando adiciones o solicitudes que excedan el stock disponible en la sucursal de origen.

### 3.4 Desglose de partidas en transferencias y conteos físicos [CERRADO]
- **Diagnóstico**: La consulta `list()` en `InventoryTransferRepository` e `InventoryPhysicalCountRepository` devolvía un arreglo vacío (`List.of()`) en el campo de artículos.
- **Solución**: Se actualizaron las consultas JDBC para mapear e incluir los productos, SKUs, cantidades teóricas, contadas y enviadas en las listas generales, desplegándolas en las tarjetas de logística inter-sucursal y en la interfaz del conteo físico activo.

### 3.5 Estandarización visual UI/UX e invisibilización de scroll [CERRADO]
- **Solución**: Se reestructuró la página `/inventory` integrando las clases de GymOS (`app-card`, `app-input`, `app-button`), delimitando el contenedor principal en `max-w-7xl`, dividiendo el módulo en 4 pestañas exclusivas (`Existencias y Alertas`, `Historial de Movimientos`, `Gestión de Transferencias`, `Auditorías`), fijando la altura constante (`shrink-0 h-fit`) en las tarjetas de alerta de stock e invisibilizando las barras de scroll con `[scrollbar-width:none] [&::-webkit-scrollbar]:hidden`.

---

## 4. Deuda técnica priorizada y estado de resolución

## P0 — Riesgos que deben resolverse antes de escalar producción

### P0.1 Modelo uniforme de autorización
- **Estado**: *COMPLETADO EN INVENTARIO / PENDIENTE GLOBAL*
- **Situación**: Inventario cuenta con sus permisos específicos (`inventory.read`, `inventory.manage`, `inventory.transfer`, `inventory.count`, `inventory.settings.manage`). Resta extender la suite de pruebas de contrato parametrizada sobre endpoints sensibles de Ventas y Socios.

### P0.2 Almacenamiento seguro de refresh token
- **Estado**: *PENDIENTE*
- **Acción**: Migrar refresh token a cookie `HttpOnly`, `Secure` y `SameSite` con rotación por familia.

### P0.3 Consistencia entre venta e inventario
- **Estado**: **[RESUELTO / CERRADO - V44]**
- **Solución Aplicada**: Política `allow_negative_stock` en `branch`, bloqueo `FOR UPDATE` e idempotencia con índices únicos en `inventory_movement`.

---

## P1 — Deuda funcional y arquitectónica importante

### P1.1 Semántica de fechas de membresía
- **Estado**: *PENDIENTE*
- **Acción**: Estandarizar si `ends_on` es inclusivo o exclusivo usando la zona horaria propia de la sucursal.

### P1.2 Puertos de aplicación en módulos restantes
- **Estado**: **[COMPLETADO EN INVENTARIOS / PENDIENTE EN OTROS MÓDULOS]**
- **Avance**: El módulo `com.gymos.inventory` implementa 100% el patrón `application.port` (`InventoryQueryPort` y `InventoryCommandPort`) aislando la capa de aplicación de la infraestructura JDBC.

### P1.3 Paginación parametrizada en Transferencias y Conteos Físicos
- **Estado**: **[NUEVO HALLAZGO - P1]**
- **Situación**: Los métodos `list()` de `InventoryTransferRepository` e `InventoryPhysicalCountRepository` tienen un límite de `LIMIT 50`. Se debe migrar a paginación parametrizada (`page` / `size`).

---

## P2 — Mantenibilidad, experiencia y operación

### P2.1 Indicador de guardado por renglón en conteos físicos
- **Estado**: **[NUEVO HALLAZGO - P2]**
- **Acción**: Agregar un ícono discreto de confirmación visual en la casilla de captura del conteo físico al disparar el evento `onBlur`.

### P2.2 Accesibilidad del scroll oculto
- **Estado**: *CUMPLIDO CON CLASES TAILWIND*

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
- [x] Consulta de sucursales del gimnasio habilitada para Gerentes.
- [x] Publicación automática de fichas en catálogo de sucursal destino.
- [x] Autorización mediante permisos `inventory.transfer` e `inventory.manage`.

### **Fase III — Conteos físicos (Auditorías de Stock)** [COMPLETADO 100%]
- [x] Sesiones de conteo físico (`inventory_physical_count` e `inventory_physical_count_item`).
- [x] Despliegue de partidas completas y captura de conteos contados vs. sistema.
- [x] Cálculo automático de varianzas en tiempo real.
- [x] Aprobación y generación automática de movimientos de ajuste (`ADJUSTMENT_IN` / `ADJUSTMENT_OUT`).
- [x] Auditoría por usuario y fecha.

### **Fase IV — Costos, valuación y reabastecimiento** [COMPLETADO 100%]
- [x] Costo promedio ponderado (`averageCostCents`).
- [x] Endpoint `/api/v1/inventory/summary` con valuación total en MXN.
- [x] Tarjetas métricas en la interfaz de `/inventory`.
- [x] Puntos de reorden y alertas de stock bajo con tamaño constante.
- [x] Cálculo automático de **Reabastecimiento Sugerido** (`suggestedReorderQuantity`).

### **Fase V — Reportes y exportación** [COMPLETADO 100%]
- [x] Exportación a CSV para existencias y valuación.
- [x] Exportación a CSV para historial de movimientos con filtros.

---

## 6. Criterios de Calidad y Cierre de Inventario [CUMPLIDOS 100%]

- Ninguna operación de inventario afecta otra sucursal de forma no deseada.
- Cada cambio produce un registro inmutable en `inventory_movement`.
- Permisos de Gerente corregidos en Spring Security sin alterar permisos de actualización (`PUT`).
- Pruebas unitarias de frontend (`npm test`), verificación de tipos TypeScript (`npx tsc --noEmit`) y pruebas de backend (`gradlew.bat test`) pasan en verde sin errores.
