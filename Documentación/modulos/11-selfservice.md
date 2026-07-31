# Autoservicio y kiosco

Estado: `PARCIAL`

## Implementado

- sesión temporal con token aleatorio almacenado como hash;
- verificación genérica de socio por contacto y fecha de nacimiento;
- rate limiting, bloqueo por intentos y auditoría;
- resumen de membresía y pagos pendientes;
- reserva de recursos mediante la API pública del módulo propietario;
- capacidades canónicas por terminal, expuestas a la sesión y aplicadas en cada
  endpoint implementado;
- actualización real y auditada de correo/teléfono desde sesión verificada;
- contrato de renovación/cambio sin método manual ni `validateNow`, bloqueado
  hasta disponer de pasarela;
- marca tenant y superficie sin navegación administrativa;
- migración `V61__provision_default_kiosk_per_organization.sql` para asegurar
  que cada organización existente tenga un terminal temporal `KIOSK-MAIN` y
  evitar que recepción resuelva por error el terminal del tenant demo.

## Estado temporal de identificación de terminales

`KIOSK-MAIN` es actualmente un código temporal por organización. No es una
identidad pública global, no representa una credencial de dispositivo y no debe
considerarse el modelo definitivo del kiosco.

Durante la etapa actual, cada organización debe tener su propio registro
`KIOSK-MAIN`. Cuando el kiosco se abre desde recepción, la organización y la
sucursal activas se envían como contexto para resolver exclusivamente el
terminal del tenant correspondiente.

La migración `V61__provision_default_kiosk_per_organization.sql` crea un
terminal `KIOSK-MAIN` para cada organización que todavía no lo tenga. En
organizaciones con varias sucursales, la migración lo asocia temporalmente a la
primera sucursal activa según `created_at` e `id`. Esta asociación existe sólo
para habilitar las pruebas funcionales actuales y no debe interpretarse como la
configuración final de una cadena.

El diseño definitivo debe permitir:

- un terminal independiente por sucursal y por instalación física;
- múltiples terminales dentro de una misma sucursal;
- una URL pública globalmente única por terminal;
- una credencial provisionada y rotatable por dispositivo;
- activación, suspensión, revocación y telemetría por terminal;
- administración explícita de capacidades y sucursal desde la consola.

## Pendiente

- integrar pago en línea exclusivamente con tarjeta tokenizada y webhook
  firmado;
- implementar cotización, confirmación y conciliación de renovación/cambio;
- integrar catálogo y reserva de profesionales de Wellness;
- implementar autorización, método tokenizado y gestión de domiciliación;
- eliminar la dependencia operativa del código temporal `KIOSK-MAIN`;
- implementar identidad global, URL pública única y credencial provisionada
  para cada terminal;
- permitir terminales independientes por sucursal y múltiples terminales por
  organización;
- cierre real por inactividad y límites agregados contra creación de sesiones;
- renovación de vencidas, membresía inicial, confirmación/cotización y
  comprobantes;
- consola de terminales, telemetría y E2E multi-tenant/expiración/concurrencia.

Deuda rectora: `SELF-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.

Transición de identidad de terminales:
`../32-transicion-identidad-terminales-kiosco.md`.

Runbook: `../../docs/operations/kiosk-troubleshooting.md`.
