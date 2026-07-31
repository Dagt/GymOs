# Autoservicio y kiosco

Estado: `PARCIAL`

## Decisión funcional vigente

El kiosco de GymOS es un **modo restringido de la aplicación web iniciado desde
una sesión autenticada de recepción**. No requiere registrar cada computadora,
tablet o navegador como una terminal física independiente.

El flujo obligatorio es:

1. recepción inicia sesión con una cuenta autorizada;
2. selecciona una sucursal permitida;
3. abre el modo kiosco desde su panel;
4. el backend obtiene organización y sucursal del principal autenticado;
5. crea una sesión efímera de autoservicio separada de la sesión administrativa;
6. el socio opera únicamente con el token efímero del kiosco.

Varias computadoras o tablets pueden abrir el modo kiosco para la misma sucursal.
Cada apertura genera una sesión de autoservicio independiente, pero no obliga a
crear una entidad de dispositivo por equipo.

La cuenta de recepción sólo autoriza el arranque y aporta el contexto. El token
administrativo no debe utilizarse durante las operaciones del socio ni quedar
expuesto en la interfaz de autoservicio.

## Alcance por sucursal

Una sucursal puede utilizar el kiosco en cero, uno o varios equipos web. La
configuración funcional del autoservicio pertenece a la sucursal y define, como
mínimo:

- marca de la organización;
- capacidades habilitadas;
- estado de disponibilidad;
- contexto de organización y sucursal para todas las consultas y mutaciones.

No se requiere una URL diferente por dispositivo físico mientras el kiosco sólo
pueda iniciarse desde recepción. La identidad individual de dispositivos queda
como una capacidad futura y opcional para escenarios que necesiten revocación,
telemetría o políticas distintas por equipo.

## Compatibilidad técnica temporal

La tabla `self_service_terminal` y el código `KIOSK-MAIN` provienen del diseño
inicial basado en dispositivos. Durante la transición pueden conservarse como
configuración interna, pero no deben ser la fuente pública de identidad ni la
forma principal de determinar el tenant.

El flujo iniciado desde recepción debe resolver siempre el contexto mediante el
principal autenticado y su sucursal activa. Nunca debe degradarse a un registro
de otra organización ni seleccionar un kiosco global por coincidencia de código.

La migración V61 sólo permitió recuperar las pruebas existentes y asignó el
registro temporal a la primera sucursal activa de cada organización. No define
el comportamiento objetivo y debe ser reemplazada por configuración de
kiosco por sucursal o por un modelo que elimine por completo esa dependencia.

## Implementado

- sesión temporal con token aleatorio almacenado como hash;
- verificación genérica de socio por contacto y fecha de nacimiento;
- rate limiting, bloqueo por intentos y auditoría;
- resumen de membresía y pagos pendientes;
- reserva de recursos mediante la API pública del módulo propietario;
- capacidades canónicas aplicadas en cada endpoint implementado;
- actualización real y auditada de correo/teléfono desde sesión verificada;
- contrato de renovación/cambio sin método manual ni `validateNow`, bloqueado
  hasta disponer de pasarela;
- marca tenant y superficie sin navegación administrativa;
- protección contra fallback de terminales entre organizaciones.

## Pendiente

- iniciar el kiosco mediante un endpoint autenticado que use organización y
  sucursal del principal de recepción;
- retirar `KIOSK-MAIN` del recorrido funcional iniciado desde recepción;
- convertir la configuración técnica existente en configuración por sucursal o
  desacoplarla de `self_service_terminal`;
- integrar pago en línea exclusivamente con tarjeta tokenizada y webhook
  firmado;
- implementar cotización, confirmación y conciliación de renovación/cambio;
- integrar catálogo y reserva de profesionales de Wellness;
- implementar autorización, método tokenizado y gestión de domiciliación;
- cierre real por inactividad y límites agregados contra creación de sesiones;
- renovación de vencidas, membresía inicial, confirmación/cotización y
  comprobantes;
- telemetría y E2E multi-tenant/expiración/concurrencia.

## Evolución opcional de dispositivos

Sólo si el producto necesita identificar equipos físicos de manera individual se
incorporará un modelo adicional con:

- identificador público global;
- credencial de activación o dispositivo;
- revocación independiente;
- último contacto y telemetría;
- capacidades diferentes por equipo.

Este modelo no es requisito para el kiosco web iniciado desde recepción.

Deuda rectora: `SELF-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.

Decisión de transición: `../32-transicion-identidad-terminales-kiosco.md`.

Runbook: `../../docs/operations/kiosk-troubleshooting.md`.
