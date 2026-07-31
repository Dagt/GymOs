# Autoservicio y kiosco

Estado: `PARCIAL`

## Implementado

- sesión temporal con token aleatorio almacenado como hash;
- verificación genérica de socio por contacto y fecha de nacimiento;
- rate limiting, bloqueo por intentos y auditoría;
- resumen de membresía y pagos pendientes;
- renovación, cambio de plan y reserva mediante APIs públicas de los módulos
  propietarios;
- marca tenant y superficie sin navegación administrativa.

## Pendiente

- impedir que el cliente público autovalide pagos manuales;
- aplicar `allowed_actions_json` con un contrato canónico de capacidades;
- identidad global y credencial provisionada de terminal en lugar de depender
  de `KIOSK-MAIN`;
- cierre real por inactividad y límites agregados contra creación de sesiones;
- renovación de vencidas, membresía inicial, confirmación/cotización y
  comprobantes;
- implementar o retirar los contratos placeholder de pagos y contacto;
- consola de terminales, telemetría y E2E multi-tenant/expiración/concurrencia.

Deuda rectora: `SELF-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.

Runbook: `../../docs/operations/kiosk-troubleshooting.md`.
