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
- marca tenant y superficie sin navegación administrativa.

## Pendiente

- integrar pago en línea exclusivamente con tarjeta tokenizada y webhook
  firmado;
- implementar cotización, confirmación y conciliación de renovación/cambio;
- integrar catálogo y reserva de profesionales de Wellness;
- implementar autorización, método tokenizado y gestión de domiciliación;
- identidad global y credencial provisionada de terminal en lugar de depender
  de `KIOSK-MAIN`;
- cierre real por inactividad y límites agregados contra creación de sesiones;
- renovación de vencidas, membresía inicial, confirmación/cotización y
  comprobantes;
- consola de terminales, telemetría y E2E multi-tenant/expiración/concurrencia.

Deuda rectora: `SELF-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.

Runbook: `../../docs/operations/kiosk-troubleshooting.md`.
