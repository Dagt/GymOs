# 44. Especificación de UX por actor, pantalla y estado

**Estado: propuesta de especificación para UX #47–50, no implementación aprobada ni promesa de captura existente.** Inspirado en los flujos de actor Web/Mobile de TrailerSaas. La jerarquía visual concreta debe validarse con usuarios y capturas reales. El backend y las políticas por tenant/sede siempre mandan.

## 1. Mapa de navegación e información
| Actor | Entrada/ruta existente | Objetivo de los primeros 10 s | Acciones de alta frecuencia |
|---|---|---|---|
| Admin GymFort | `/platform`, `/platform/gymnasiums`, `/platform/billing` | cartera, fallos/estado y siguiente acción | alta tenant, invitación, cobros, soporte |
| Dueño | `/dashboard`, `/settings/organization` | indicadores por sede y periodo | revisar negocio, configurar, equipo |
| Gerente | `/dashboard`, `/users`, `/bookings` | operación activa de sucursal | gestionar reservas, stock, staff permitido |
| Recepción | `/reception`, `/pos`, `/clients` | localizar socio o cobrar | alta, venta, renovación, pago, kiosco |
| Coach | `/coaches` | rutinas y asignaciones vigentes | crear ejercicio, versión, asignar y PDF |
| Socio en terminal | `/kiosk/[terminalCode]` | «Ya soy socio» o «Quiero registrarme» | identificarse, adquirir/renovar, reservar, salir |
| Socio resultado | `/kiosk/checkout/result` | saber si está pendiente/confirmado | reintentar consulta, comprobante solo si conciliado |

Rutas se verificaron en árbol de código, **no** navegando producción ni comprobando permisos de cada ruta. El menú se construye por permiso y alcance real; no crear rutas redundantes para funciones que ya están en una vista.

## 2. UX-PLAT — Consola de plataforma
**Jerarquía:** encabezado con salud de cartera/alertas, buscador de gimnasios y estados verificados; cards de MRR con definición y periodo; listado denso con plan comercial, sucursales, vencimiento y acción.

**Flujo PL-01 crear gimnasio:** `Nuevo gimnasio → datos mínimos + primera sede + correo dueño → revisión → POST transaccional → éxito persistido → envío invitación/estado SMTP → reemitir si falla`. No convertir fallo posterior de correo en «no se creó». Cualquier retry conserva idempotencia; nunca mostrar token como texto persistente.

**PL-02 soporte:** seleccionar gimnasio → motivo explícito → caducidad visible → cinta inequívoca de soporte → salida auditable. Prohibir plataforma no soportada en rutas tenant.

**PL-03 cobranza comercial:** editar contrato/datos por sede, registrar pago manual único, mostrar historial inmutable, no confundir con POS del gimnasio. Cobro recurrente/CFDI solo luego de ADR/issue #52.

**Estados:** cartera vacía, invitación pendiente/enviada/expirada/fallida, tenant suspendido, MRR sin datos, permiso insuficiente y red caída. Reglas: #29 #31 #45 #52.

## 3. UX-OWN — Dueño / Gerente
- Landing muestra filtros **sucursal, periodo y KPI** con fecha de datos; cards de ventas, renovaciones, socios, visitas, reservas y stock sólo cuando hay fuente real.
- Al cambiar sede, actualizar filtros de operaciones y permisos, no cambiar organización. Nombre y logo por sucursal solo después de #51; antes fallback corporativo explícito.
- Navegación por tareas: **Operación** (socios, caja, reservas), **Productos** (catálogo, inventario), **Accesos**, **Equipo y rutinas**, **Configuración**, **Indicadores**. No añadir un menú a cada botón si duplica acciones.
- Gerente no puede gestionar identidad platform ni cambiar Stripe Connect si su permiso sólo habilita toggles operativos.
- Configuración de inscripción/medios por sede: autosave serializado, en cada campo feedback `guardando/guardado/error` y reversión clara.

**OWN-01:** elegir sede → abrir indicadores → ver definiciones, periodo/comparación → abrir detalle relacionado; estados de métricas cero vs error distintos. **OWN-02:** configuración pago por sede → activar canal listo → pruebas sin live ni habilitar capacidad dependiente de Stripe en estado inconsistente. #51 #53 #47.

## 4. UX-REC — Recepción, POS y socios
**Recepción:** preferir búsqueda inmediata con teclado/lector de barcode, CTA «Registrar socio», «Nueva venta», «Renovar» y «Abrir kiosco» cuando rol autoriza; nunca duplicar acciones en tarjetas, menú y toolbars.

**REC-01 alta:** búsqueda previa (no duplicar) → formulario progresivo datos/contacto → guardado confirmado → ficha → cotización con inscripción si aplica → cobro/recibo. Si el alta fue exitosa y falla cargar una vista secundaria, preservar resultado y mensaje seguro.

**REC-02 POS:** buscar o escanear → variante precisa con SKU/precio/unidad → cantidad → total y método → crear pago pendiente/manual → validación autoritativa → venta/stock/recibo; evitar cambios de stock dobles. Indicadores de estado distinguen «pago en proceso», «confirmado» y «necesita acción».

**REC-03 renovación/cambio:** localizar socio → ver membresía y sucursales autorizadas → elegir operación y comparar precio, inscripción, vigencia → confirmar → cobro → mostrar nuevo periodo sólo cuando se confirme. Nunca permitir plan de otra sede sin contrato.

**REC-04 conteo inventario:** tabla de partidas editables, estados individuales y errores que no desaparecen por scroll. #33 #30 #54.

## 5. UX-KIOSK — Sesión autónoma supervisada
**Precondición:** Recepción abre sesión temporal para terminal configurada; no es login de staff ni portal remoto.
1. Pantalla de entrada con título y dos acciones diferenciadas cuando registro habilitado.
2. Identificar al socio con datos permitidos y límites de intentos **o** registrarse si la sede habilita alta.
3. Mostrar resumen de plan/paquete, elegibilidad y saldo con origen de cada derecho. No mezclar paquete con membresía ilimitada.
4. Contratar, renovar o cambiar plan según disponibilidad; catálogo puede seguir visible con Stripe pendiente, CTA monetaria deshabilitada y explicación.
5. Resumen financiero (plan, inscripción, total, método) → Checkout alojado → retorno **pendiente de webhook** → estado definitivo consultado → comprobante.
6. Salida explícita/timeout → reset de estado y datos sensibles; siguiente socio inicia limpio.

**Matriz de estado:** socio no existe, socio inactivo automático vs manual, socio sin plan, plan oculto pero renovable, paquete pendiente/activo/consumido, Stripe no listo, red caída, webhook atrasado/duplicado, regreso del navegador sin confirmación, sesión caducada, 429 y acceso fuera de sede. No presentar IDs/URLs/códigos HTTP públicamente. #34 #35 #50.

## 6. UX-COA — Coach y rutinas
**COA-01 crear:** catálogo buscable / crear ejercicio faltante → rutina en pasos (estructura, prescripción, revisión) → publicar versión inmutable → seleccionar socio y sede autorizada → asignar periodo sin solapamiento → descargar PDF verificado. Mostrar faltantes del catálogo sin romper prescripción. «Crear ejercicio» no debe cerrar automáticamente por nombre parcialmente escrito. Cambios a rutina publicada crean versión, no mutación silenciosa.

**COA-02 métricas:** seleccionar rango, contexto coach/rol, mostrar periodo y denominadores; evitar gráficos que insinúen eficacia clínica no medida. #47 #48 #55.

## 7. UX-ACCESS — Integración física
**Solo config cloud actual en main; Connector rama experimental.** Pantalla de configuración muestra conexión/sucursal/dispositivo, proveedor, permisos, modo, capacidades, último heartbeat, lease/job, estado pendiente/sincronizado/degradado. No ofrecer «Abrir torniquete real» en demo si es `SIMULATED_OPEN`. Vincular persona/dispositivo mediante identificador opaco confirmado; no auto-link difuso por nombre. En modo online validar readiness EVENT_POLLING y REMOTE_OPEN. #38–41.

## 8. UX-STATE — obligación transversal
Por pantalla: **default, loading, vacío real, error API, error permisos, error validación, sin proveedor, éxito, pendiente/reconcilio** si aplica. Acciones destructivas con confirmación contextual, focus y confirmación persistente. Localización `es-MX`: MXN, fechas operativas por sede, miles/decimales, correo/teléfono. Mantener teclado/focus visible, targets táctiles y orden de tabulación en 390/768/1440/kiosco.

## 9. Criterios de revisión
Por ruta crear screenshot antes/después con SHA/viewport/rol/datos sintéticos; registrar diferencias con figma/mock si existe, nunca inventar coincidencia. E2E debe usar componentes reales sin transformaciones prebuild; arquitectura de componentes propuesta en `39`. Documento comparable a `docs/ux/*` de TrailerSaas y ligado a #48.