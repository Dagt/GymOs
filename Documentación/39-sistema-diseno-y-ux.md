# 39. Sistema de diseño GymFort y especificación de experiencia

**Estado:** directriz de diseño objetivo, no afirma que las pantallas actuales coincidan. Está ligada a [épica UX #26](https://github.com/Dagt/GymOs/issues/26) y #47–50. Conservar marca visible GymFort, nombres de cada tenant y rol operativo; no repetir la pobreza de experiencias técnicamente funcionales pero dispersas.

## 1. Lenguaje visual

**Intención:** SaaS profesional, moderno, legible, de alta densidad *controlada*; recepción táctil y directa, dueño analítico, coach claro, kiosco deliberadamente simple. Evitar dashboards genéricos, saturación de tarjetas, gradientes por todas partes, botones minúsculos o tipografía demasiado pequeña.

**Punto de partida verificable**, ya en `frontend/src/app/globals.css`:
- `--brand-primary: #093d3a`, `--brand-accent: #18c8b2`, `--brand-surface: #f2f8f7` y `--brand-ink: #102321` (valores actuales base, sujetos al tenant).
- Escalas semánticas para fondo, superficie, texto fuerte, texto secundario, bordes, acento, éxito, advertencia, peligro y focus. Nunca codificar directamente el color de un cliente en todos los componentes.
- Definir escalas coherentes de espaciado 4/8/12/16/24/32, radios 8/12/16, elevación discreta, tipo de dato numérico tabular, estados hover/focus/active/disabled.
- Soportar colores tenant sin sacrificar contraste ni utilizar exclusivamente el color para comunicar resultado. Especificar fallback cuando color/logo de marca no sea accesible.

**No se aprueba** una renovación visual sólo por render conceptual. Primero inventario de componentes existentes y screenshots reales.

## 2. Arquitectura de pantallas por rol

| Rol | Inicio recomendado y valor inmediato | Acciones prioritarias |
|---|---|---|
| PLATFORM / operador GymFort | Cartera comercial, salud de clientes, alertas, cobros | Alta, invitaciones, soporte acotado y estado de implementación |
| Dueño | Vista ejecutiva por sucursal y periodo, resultados con definiciones | Ventas, socios, conversión, plan/beneficio, sucursal/equipo |
| Gerente | Operación del día, incidencias y controles permitidos | Atención, reservas, stock, usuarios de alcance, reportes de sede |
| Recepción | Home de acción rápida, no duplicar menú | Buscar socio, registrar, vender, cobrar, renovar, abrir kiosco |
| Coach | Rutinas, asignaciones, ejercicios, indicadores personales | Crear, publicar, asignar, imprimir/descargar PDF |
| Socio en kiosco | «Ya soy socio / Quiero registrarme» cuando habilitado | Identificación segura, contratar/renovar, datos, reserva, salida explícita |

No mostrar opciones sin permiso ni dejar deep links accesibles sin defensa server-side. Selector de sucursal debe mostrar alcance, nunca cambiar de tenant accidentalmente. El carácter de sucursal operativa se mantiene en ventas/inventario/paquetes e identidad visual por sede cuando #51 se implemente.

## 3. Patrones de interacción

- **Listados**: búsqueda y filtros antes de paginar, total del servidor, páginas reales, estado vacío distinto a error de API, tamaños y acciones accesibles; exportación indica alcance.
- **Formulario**: mostrar sólo datos imprescindibles al inicio, progresión lógica, labels visibles, validación cercana al campo, guardado/guardando/error, cancelación sin efectos, no duplicar CTA.
- **Pagos POS**: item/variante/código identificables, confirmación explícita, prevención doble clic, monto y medio, estado pendiente vs confirmado, comprobante y trazabilidad.
- **Membresías**: vigencia exacta y ámbito de cobertura; cotización antes de confirmar; derechos/visitas no como conceptos confusos.
- **Coaches**: builder de pasos manejables, catálogo searchable, prescripción legible, preview PDF y mensajes de publicación/asignación.
- **Kiosco**: botones grandes, sin panel admin, expiración e interrupción segura; reiniciar identidad cuando cambia modo, jamás enseñar rutas HTTP ni errores internos; no cobrar manualmente.
- **Alertas/notificaciones**: errores y confirmaciones accesibles, estados persistentes cuando implican dinero; evitar toast efímero como única prueba de una operación financiera.
- **Configuración**: distinguir estado habilitado por el gimnasio de readiness del proveedor; autosave con rollback accesible.

## 4. Layout y responsividad

- Desktop ≥1440: sidebar estable, encabezado contextual no redundante, tabla con densidad cómoda, detalle en drawer cuando reduce cambios de ruta.
- Laptop/tablet 1024/768: paneles apilados, navegación comprensible, acciones sin solapamientos.
- Móvil 390: recorrido viable de consulta, socio, venta básica y acciones permitidas; sin scroll horizontal inesperado.
- Kiosco 1024/1280 táctil: contraste alto, targets táctiles adecuados, flujo claro de timeout y limpieza de datos entre personas.
- No inventar medidas finales de breakpoints sin inspeccionar CSS/uso real. Target general WCAG 2.2 AA con foco visible, teclado, texto escalable y resultados anunciados.

## 5. Estados obligatorios por pantalla

Para cada vista crítica documentar y capturar **default, loading, empty, error, success, permission-denied y provider-unavailable** si aplica. Para pagos agregar pending/reconciling, duplicado, cancelación, expiración, reintento y recuperación. No simular éxito al recibir un 202/redirect cuando falta webhook.

## 6. QA visual y evidencia

#48 exige un catálogo de capturas del frontend real por rol en 390, 768, 1440 y kiosco, con datos sintéticos, casos normales y errores. Comparar diseño propuesto vs implementación. Reportar desviaciones con captura/URL/rol/contexto/sucursal, no juicios abstractos. #49 debe asegurar que test y build no reescriban páginas. #47 implanta tokens y componentes incrementalmente (primero shell/tablas/formularios; después módulos). #50 añade accesibilidad.

## 7. Organización técnica propuesta

Separar tokens semánticos del CSS de landing, usar componentes compartidos documentados; priorizar `AppShell`, page header, tabs, filtros, tablas, drawers, modals, CTA, toast, confirmaciones, badges de estado y skeletons. Mantener compatibilidad Next/Tailwind instalados; no introducir biblioteca UI nueva sin ADR que justifique peso, accesibilidad, licenciamiento y migración. Establecer pruebas visuales con baseline fijada, datos sintéticos y viewport repetible.