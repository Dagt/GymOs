> Continuado y actualizado por [UX comercial del PR #74](ux-commercial-consistency.md): actividad reciente retirada y capturas reemplazadas. Las verificaciones de este informe describen el corte inicial e534bdc8.

# Pulido de membresías de recepción — 10/11/2026

Base: `36b50d545eb3def2871b3c2211872f5329373b04` (main, PR #73 integrado).
Rama: `codex/reception-memberships-visual-polish`.

La foto reportada mostraba acciones sin centrar, un resumen monetario en una sola línea, bloques de saldo extendidos y una confirmación de ancho completo. El formulario no tenía una composición específica para recepción y heredaba reglas globales (incluido el fondo crema de botones secundarios).

Se agregó una hoja CSS con alcance exclusivo de esta pantalla: pasos de búsqueda/operación, cuatro acciones iguales y centradas, resumen compacto del socio y su saldo, campos y resumen de cobro en dos columnas, total destacado y confirmación dentro del resumen. En móvil el formulario se apila y la búsqueda mantiene un botón de ancho completo. Los colores provienen de los tokens existentes del tenant; no se introdujo una paleta independiente. La cotización detallada de membresías es optativa, sólo para recepción. El expediente conserva el orden de sus campos y cotización.

## Evidencia visual

Datos sintéticos, Chromium. Capturas inspeccionadas de la compilación final de producción (`next start`, puerto 3127) con API simulada, sin PII. Renovación con membresía y paquete activos reproduce el contexto de la foto:

- [Escritorio 1440](evidence/reception-polish/renovacion-con-paquete-1440.png)
- [Tablet 768](evidence/reception-polish/renovacion-con-paquete-768.png)
- [Móvil 390](evidence/reception-polish/renovacion-con-paquete-390.png)
- [Venta de paquete](evidence/reception-polish/formulario-paquete.png)
- [Venta de paquete móvil](evidence/reception-polish/cotizacion-paquete-movil.png)
- [Sin socio](evidence/reception-polish/membresias-sin-socio.png)

## Verificación local

Node 24.13.1, npm 11.8.0, Next 16.4.5, Playwright 1.61.1.

| Comando en frontend | Resultado |
| --- | --- |
| `npm.cmd test` | 118/118 |
| `npm.cmd run lint` | PASS |
| `npm.cmd run typecheck` | PASS |
| `npm.cmd run test:e2e -- e2e/console-routing.spec.ts e2e/member-workflows.spec.ts` | 25/25; alta, renovación, cambio, paquetes, permisos, búsqueda, errores y recuperación Point de los cuatro flujos |
| `npm.cmd run test:e2e -- e2e/console-routing.spec.ts --grep "recepción contrata membresías"` | 1/1; evidencia adicional a 1440/768/390 y comprobación de ausencia de desbordamiento horizontal |
| `npm.cmd run test:e2e -- --config playwright.polish.config.ts e2e/console-routing.spec.ts --grep "recepción contrata membresías"` | 1/1 sobre build de producción; configuración temporal con `next start` 3127, eliminada al finalizar |
| `npm.cmd run cf:build` | PASS; ejecuta también build de producción Next con webpack y genera worker OpenNext |

Un intento de lint simultáneo con el arranque de Playwright falló con ENOENT al desaparecer `test-results`; la repetición secuencial de `npm.cmd run lint` terminó con código 0.

Point se prueba con respuestas simuladas; no se ejecutó una operación en terminal física. Backend, PostgreSQL e integraciones conectadas no ejecutados en esta corrección de presentación. No hay cambios de API, permisos, solicitudes de cobro, idempotencia o recuperación. Las advertencias del build corresponden al soporte Windows de OpenNext y a `punycode`; el empaquetado concluyó con código 0. El servidor local de capturas avisó que `output: standalone` recomienda su launcher dedicado; el recorrido sobre `next start` sí completó 1/1. No se usó este servidor para desplegar. CI remoto y aceptación visual del usuario pendientes. Sin merge ni despliegue.
