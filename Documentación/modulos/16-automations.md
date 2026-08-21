# Automatizaciones

Estado: `FUNCIONAL / LISTO PARA ACEPTACIÓN`
Última revisión: 21 de agosto de 2026

## Implementado

- alta, consulta, habilitación, deshabilitación y ejecución manual de reglas;
- centro de control oscuro con acciones, salud operativa, reglas y entregas en
  superficies diferenciadas, integrado al feedback global de GymFort;
- programación por intervalo con `next_run_at` y reclamo condicional mediante
  `locked_until`;
- acción inicial `CREATE_NOTIFICATION` sobre la cola interna;
- alta limitada a disparadores realmente ejecutables: `MANUAL`, `SCHEDULED`,
  `PAYMENT_COMPLETED`, `PAYMENT_PENDING_VALIDATION`, `BOOKING_CREATED`,
  `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `BOOKING_REMINDER`, `MEMBERSHIP_EXPIRING` y
  `MEMBERSHIP_EXPIRED`, además de `RECURRING_CHARGE_DUE`,
  `RECURRING_CHARGE_SUCCEEDED` y `RECURRING_CHARGE_FAILED`;
- acción soportada `CREATE_NOTIFICATION`;
- validación del mensaje y rechazo de tipos desconocidos;
- condiciones tipadas y validadas por disparador: intervalo, días previos al
  vencimiento, plan, método/monto de pago, contexto comercial y recurso;
- inicio guiado con recetas para membresía por vencer, pago pendiente, recordatorio
  previo de reservación, reserva cancelada y fallo de domiciliación, sin guardar
  hasta que el usuario revise;
- formulario en tres pasos (`cuándo`, `a quién` y `mensaje`) para intervalo,
  vencimiento, filtros de pago, asunto y contenido, sin edición de JSON;
- registro de ejecuciones y auditoría;
- aislamiento de consultas administrativas por organización y sucursal;
- permisos de lectura, ejecución y administración;
- efecto `CREATE_NOTIFICATION` idempotente por regla y ocurrencia programada;
- persistencia de fallos separada de la transacción que crea la notificación;
- reserva durable de cada intento en `PROCESSING` antes de crear el efecto, de
  modo que una caída intermedia retome la misma llave;
- `retryLimit` efectivo con backoff exponencial acotado, identidad estable de
  ocurrencia, estado `RETRY_SCHEDULED` y cierre `DEAD_LETTER`;
- bandeja durable `automation_trigger_event` con payload tipado, lease,
  reintento y deduplicación para eventos de pagos, reservas y membresías;
- eventos públicos de creación y cambio de estado de reservas;
- productor transaccional de membresías por vencer/vencidas, con marcador
  diario y una identidad de ocurrencia estable por membresía y vigencia;
- productor durable de recordatorios de reservación que selecciona reservas
  próximas según la anticipación de cada regla, deduplica por regla, versión y
  reservación, y respeta el aislamiento y la pausa del tenant;
- reglas heredadas con disparadores sin consumidor se conservan deshabilitadas
  y no pueden reactivarse hasta que su contrato sea soportado;
- historial operativo en la interfaz con estado, intento, referencia,
  duración y error sanitizado, paginado en servidor con total estable;
- bandeja de fallos definitivos con reintento/descarte manual, motivo
  obligatorio, auditoría y paginación estable en servidor;
- vista previa guiada de la notificación antes de crear una regla.
- productores y consumidores durables para inicio, éxito y fallo de cada
  ocurrencia domiciliada, incluyendo plan, intento y agotamiento de reintentos.
- contadores y tiempos Micrometer por `organization_id`, disparador y resultado,
  exportables en `/actuator/metrics` y `/actuator/prometheus`;
- pruebas Testcontainers para exclusión mutua de leases, deduplicación del inbox,
  recuperación tras vencimiento y paginación aislada por tenant.
- selección equitativa del scheduler por organización: un tenant con backlog no
  puede agotar el lote antes de que otros tenants obtengan turno;
- pausa y reanudación por tenant con `automation.scheduler.manage`, motivo
  auditado y exclusión atómica tanto al seleccionar como al reclamar trabajo;
- tablero operativo avanzado por sucursal con pendientes, reintentos, dead
  letters y leases vencidos, oculto durante la operación normal y visible
  automáticamente ante incidencias, más gauges y reglas Prometheus de alerta;
- configuración operativa dividida en guía inicial, cobros, kiosco, correo e
  historial técnico, mostrando un solo bloque a la vez.

## Reglas iniciales

Las organizaciones nuevas no reciben automatizaciones activas de manera
automática. Las recetas de la interfaz sólo preparan un borrador entendible y el
usuario debe revisarlo y seleccionar `Guardar y activar`. La base de demostración
histórica conserva una regla sembrada para el gimnasio demo; no forma parte del
onboarding normal ni se replica a otros gimnasios.

## Pendiente

- repetir la suite `postgresIntegrationTest` en CI; el corte local ejecutó 37
  escenarios PostgreSQL reales sin fallos ni omisiones;
- ejecutar los escenarios E2E conectados en el ambiente de integración para
  conservar evidencia de aislamiento, expiración y recuperación extremo a
  extremo.

Decisión de producto: no se implementarán recursos reservables con costo ni
servicios de wellness con costo. Las automatizaciones de reservas se limitan a
los flujos incluidos/sin cobro existentes.

Deuda rectora: `AUT-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.
