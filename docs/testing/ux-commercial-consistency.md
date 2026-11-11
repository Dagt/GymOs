> Seguimiento actual: [alturas, lista de cien productos y restauración del acento inferior](commercial-layout-followup.md). Las verificaciones originales de este informe corresponden al corte aef03176.

# Correcciones UX comerciales — PR #74

## Trazabilidad

Rama única: `codex/reception-memberships-visual-polish`.
SHA inicial: `e534bdc809487fce23ca1424168be6c1c67e03f0`.
Se ejecutó `git fetch origin`, se comprobó el árbol limpio y se examinó el diff completo previo de 19 archivos contra main. PR #74 abierto y Draft, sin merge. El SHA final se registra en la descripción del PR (evita una referencia circular al commit que contiene este informe).

## Auditoría y alcance

Se leyeron AGENTS, guía Next instalada y documentación técnica/UX de catálogo, inventario y membresías. Se contrastaron `app-button`, `app-button-secondary`, `app-input`, `form-field`, AppDrawer y AppNotice con POS/PosSections, ClientSections, Bookings/forms y las reglas finales de globals.css. Predominan fondo claro, encabezados oscuros, tokens por tenant y controles de borde simple. La cascada global contiene overrides históricos que explican diferencias de radio, sombras, tonos secundarios y subrayados. No se modifica globals.css ni se rediseñan POS, socios o reservas.

## Correcciones

- UX-01: el panel de producto conserva nombre, métricas y Registrar Movimiento. Conteo y transferencia permanecen en sus pestañas. Sólo se retiran los dos callbacks innecesarios de ese panel.
- UX-02: se reutiliza InventoryProductPicker, también consumidor de movimientos e historial. Producto ocupa todo el ancho y cantidad/agregar van debajo. Resultados dentro del flujo del drawer, con límite de altura y scroll interno, nombres ajustables y sin portal ni nuevos z-index. Búsqueda usa el endpoint existente (nombre/SKU/aliases de código). Flechas/Enter seleccionan y Escape retorna al disparador. Se conserva disponibilidad y lógica de agregación/validación. El formulario original permite reemplazar la cantidad de un producto agregándolo de nuevo; no tenía eliminación de partidas y no se introduce una regla nueva.
- AppDrawer agrega `containFocus` optativo sólo en transferencias: foco inicial, ciclo Tab/Shift+Tab, Escape y retorno al disparador. Otros consumidores mantienen el valor por defecto.
- UX-03: filtros principales con búsqueda proporcional, tipo, categoría y Limpiar; avanzados con Estado, Inventario, Orden y Archivados. Labels visibles, misma altura y cuadrícula responsiva. Mantiene details expandible, URL, paginación, búsqueda y limpieza.
- UX-04: se elimina RecentReceptionSales, su importación, estado de revisión y render. No se modifica getRecentSales ni endpoints/historiales compartidos. Se mantienen ambas rutas de contratación, confirmaciones y recuperación del pago.
- UX-05: CommercialControls.module.css aplica el patrón de botones existente sólo al resumen/transferencia de inventario, filtros de catálogo y membresías. No altera botones destructivos de otros módulos. Selección de membresías con borde simple, acento del tenant, marca de verificación y aria-pressed. Se conservan controles deshabilitados y foco visible.
- El retiro de la tabla expuso una colisión entre el aviso flotante y la confirmación al reintentar. AppNotice agrega una presentación inline optativa, usada sólo en recepción (incluido VisitPackageCard); los demás consumidores mantienen el portal. Las pruebas de reintento completan la operación sin cerrar artificialmente el aviso ni forzar clics.

## Pruebas y límites

Windows, Node 24.13.1, npm 11.8.0, Next 16.4.5, Playwright 1.61.1/Chromium. Dependencias existentes del worktree, sin paquetes añadidos.

- `npm.cmd test`: 118/118.
- `npm.cmd run typecheck`: PASS.
- `npm.cmd run lint -- --ignore-pattern test-results/**`: PASS. Se ignora sólo la salida volátil de Playwright.
- `npm.cmd run test:e2e -- --workers=3 --timeout=60000`: 130/130. Incluye consumidores de componentes compartidos, conteos, paginación, POS, socios, reservas y recuperación Point de los cuatro contratos. Se ejecutó antes del último ajuste de normalización CSS; los recorridos finales de las secciones afectadas se repiten en producción.
- `npm.cmd run cf:build`: PASS; incluye build Next webpack, validación TypeScript y empaquetado OpenNext.
- Recorridos finales sobre build de producción (`next start`, configuración temporal Playwright, eliminada al terminar): 3/3 PASS (inventario/transferencia, catálogo y contratación completa). Incluyen categoría, disponibilidad, error de cantidad, teclado, Tab/Shift+Tab/Escape, solicitud de transferencia, filtros y ambos órdenes de contratación.
- `git diff --check`: PASS.

La primera ejecución parcial tuvo 58 PASS/3 FAIL: fixture de sucursales incompleta, espera inmediata del checkbox controlado por navegación URL y aviso que tapaba la confirmación. Se corrigieron fixture/esperas y la presentación del aviso; el recorrido completo posterior concluyó 130/130. No se ocultan esos fallos iniciales.

API y proveedores simulados sólo dentro de Playwright con datos sintéticos. Backend/PostgreSQL/proveedor conectado/terminal física no ejecutados: no se modifica servidor ni integración de cobro. Cloudflare no desplegado. OpenNext mantiene su advertencia de compatibilidad Windows y deprecación punycode. CI remoto y aceptación visual final del usuario son gates pendientes.

## Evidencia del código final

Las capturas actuales se generan sobre el build de producción local, no sobre datos del gimnasio. Se incorporan en `evidence/commercial-consistency/` y reemplazan las capturas de membresías de `evidence/reception-polish/`. Las imágenes de la entrega anterior con actividad reciente quedan superadas por esta versión.

## Recomendaciones separadas

Consolidar los overrides globales y extender el patrón de foco a todos los drawers requiere un trabajo independiente con inventario completo de consumidores. No se intenta resolverlo globalmente aquí. La eliminación de partidas de una transferencia tampoco se agrega en esta corrección visual.

### Capturas actuales

- [catalogo-avanzado-1440](evidence/commercial-consistency/catalogo-avanzado-1440.png)
- [catalogo-avanzado-390](evidence/commercial-consistency/catalogo-avanzado-390.png)
- [catalogo-avanzado-768](evidence/commercial-consistency/catalogo-avanzado-768.png)
- [catalogo-principal](evidence/commercial-consistency/catalogo-principal.png)
- [cotizacion-paquete-movil](evidence/commercial-consistency/cotizacion-paquete-movil.png)
- [formulario-paquete](evidence/commercial-consistency/formulario-paquete.png)
- [formulario-renovacion](evidence/commercial-consistency/formulario-renovacion.png)
- [inventario-movil](evidence/commercial-consistency/inventario-movil.png)
- [inventario](evidence/commercial-consistency/inventario.png)
- [membresias-sin-socio](evidence/commercial-consistency/membresias-sin-socio.png)
- [membresias-y-paquetes](evidence/commercial-consistency/membresias-y-paquetes.png)
- [renovacion-con-paquete-1440](evidence/commercial-consistency/renovacion-con-paquete-1440.png)
- [renovacion-con-paquete-390](evidence/commercial-consistency/renovacion-con-paquete-390.png)
- [renovacion-con-paquete-768](evidence/commercial-consistency/renovacion-con-paquete-768.png)
- [transferencia-390](evidence/commercial-consistency/transferencia-390.png)
- [transferencia-768](evidence/commercial-consistency/transferencia-768.png)
- [transferencia-partidas](evidence/commercial-consistency/transferencia-partidas.png)
- [transferencia-seleccion](evidence/commercial-consistency/transferencia-seleccion.png)
- [transferencia-selector](evidence/commercial-consistency/transferencia-selector.png)
