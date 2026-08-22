# Automatizaciones

Estado: `FUNCIONAL / CIERRE DE IMPLEMENTACIÓN`
Última revisión: 22 de agosto de 2026
Rama auditada: `feature/automations-zoho-email`

## Propósito

El módulo permite que cada gimnasio defina reglas del tipo **cuando ocurra X, generar Y** sin acoplarse al proveedor final de correo ni ejecutar directamente lógica de pagos, membresías o reservas. Automatizaciones orquesta eventos y crea efectos idempotentes; `notifications` es responsable de la entrega.

## Arquitectura actual

```text
Evento de dominio / scheduler / ejecución manual
        ↓
automation_trigger_event / regla
        ↓
AutomationService
        ↓
CREATE_NOTIFICATION
        ↓
notification_log
        ↓
NotificationDeliveryWorker
        ↓
EmailDeliveryPort
        ↓
ZeptoMail SMTP
```

El módulo no envía SMTP directamente y no conoce credenciales de ZeptoMail.

## Disparadores ejecutables

- `MANUAL`
- `SCHEDULED`
- `PAYMENT_COMPLETED`
- `PAYMENT_PENDING_VALIDATION`
- `BOOKING_CREATED`
- `BOOKING_CONFIRMED`
- `BOOKING_CANCELLED`
- `BOOKING_REMINDER`
- `MEMBERSHIP_EXPIRING`
- `MEMBERSHIP_EXPIRED`
- `RECURRING_CHARGE_DUE`
- `RECURRING_CHARGE_SUCCEEDED`
- `RECURRING_CHARGE_FAILED`

`RECURRING_CHARGE_DUE` significa **llegó la fecha de un cobro domiciliado y se materializa/procesa la ocurrencia**. No significa que el socio acaba de activar por primera vez la domiciliación. La activación inicial existe como evento de pagos separado, pero todavía no es un trigger configurable independiente en Automatizaciones.

## Acciones

La acción ejecutable es `CREATE_NOTIFICATION` con canal:

- `IN_APP`
- `EMAIL`

Las reglas de email que dependen de un socio reciben el `clientId` desde el evento y `notifications` resuelve el correo real dentro del tenant/sucursal correspondiente.

## Condiciones

Según el disparador se validan condiciones como:

- intervalo de ejecución;
- días antes del vencimiento;
- anticipación de recordatorio de reserva;
- plan;
- método de pago;
- contexto comercial;
- monto mínimo;
- recurso.

La interfaz no expone JSON al usuario.

## Interfaz vigente

La pantalla de Automatizaciones fue simplificada para usuarios de gimnasio. La vista normal tiene sólo:

- **Automatizaciones**: reglas activas/pausadas, resumen, toggle y botón `Probar ahora`;
- **Actividad**: resultado de mensajes y ejecuciones en lenguaje operativo;
- **Problemas**: fallos accionables y reintentos.

Los detalles técnicos de scheduler, leases, trigger references, intentos y dead letters no se muestran como contenido principal al cliente. Permanecen disponibles en backend, métricas, auditoría y APIs de operación.

La creación de una regla se realiza desde cero en un flujo guiado. No existen plantillas ni recetas recomendadas. El usuario define nombre, disparador/condiciones, canal, asunto/mensaje y revisa antes de activar.

## Probar ahora

- Reglas `IN_APP`: pueden ejecutarse directamente de forma manual.
- Reglas `EMAIL`: abren un panel de prueba y exigen seleccionar un socio activo con correo real.
- La selección usa búsqueda dinámica por nombre o correo, con debounce y resultados limitados; no se carga una lista completa de socios.
- La prueba reutiliza asunto, mensaje y diseño configurados en la regla y crea una notificación con `recipientType=CLIENT`.
- La prueba no fabrica un pago/reserva falso ni altera el dominio propietario; valida plantilla + destinatario + cola + proveedor.

## Permisos

- `automation.read`: consultar reglas/estado.
- `automation.manage`: crear, habilitar y deshabilitar reglas.
- `automation.execute`: ejecutar pruebas manuales.
- `automation.scheduler.manage`: pausar/reanudar procesamiento por tenant; no se expone como acción cotidiana.

V80 concede a `Gerente` lectura, administración y ejecución de automatizaciones, además de lectura/gestión de notificaciones. El control global del scheduler sigue reservado a perfiles con el permiso específico.

## Durabilidad e idempotencia

- `automation_trigger_event` funciona como inbox durable de eventos.
- Las ocurrencias conservan identidad idempotente por regla/evento.
- Las ejecuciones pasan por `PROCESSING`, `SUCCEEDED`, `FAILED`, `RETRY_SCHEDULED`, `DEAD_LETTER` o `DISCARDED`.
- El worker reclama trabajo mediante lease recuperable.
- Los reintentos usan backoff exponencial acotado.
- La selección es justa entre organizaciones para que un tenant con backlog no monopolice el lote.
- La pausa por tenant es auditada.
- `CREATE_NOTIFICATION` es idempotente por regla/ocurrencia.

## Eventos de dominio

Existen productores durables para pagos, reservas, ciclo de membresía y cobros recurrentes. Las reservas programan recordatorios próximos según la anticipación de la regla. Membresías por vencer/vencidas se producen con identidad diaria estable.

## Observabilidad

- auditoría de creación/cambio/ejecución y recuperación;
- métricas Micrometer por tenant, disparador y resultado;
- gauges de backlog/leases/fallos;
- Prometheus/Actuator para operación;
- mensajes de error sanitizados para la UI.

## Estado de cierre

El alcance funcional del módulo está implementado y probado manualmente con entrega real de correo. Quedan como trabajo posterior, no como bloqueo de esta implementación:

- mantener `postgresIntegrationTest` y E2E conectados en CI;
- añadir un trigger independiente para **domiciliación activada** si producto requiere avisar justo al vincular el mandato;
- ampliar canales futuros (WhatsApp/push) a través de `notifications`, sin modificar el motor de reglas;
- enriquecer telemetría de entregabilidad cuando se incorporen webhooks de rebote/queja del proveedor de email.

Decisión de producto: no se implementan recursos reservables con costo ni servicios wellness con costo dentro de este alcance.

Diseño rector: `../31-diseño-automatizaciones-kiosco.md`.
Runbook de aceptación: `../../docs/testing/automations-kiosk-acceptance.md`.
