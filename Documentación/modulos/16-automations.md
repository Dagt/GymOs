# Automatizaciones

Estado: `PARCIAL`

## Implementado

- alta, consulta, habilitación, deshabilitación y ejecución manual de reglas;
- programación por intervalo con `next_run_at` y reclamo condicional mediante
  `locked_until`;
- acción inicial `CREATE_NOTIFICATION` sobre la cola interna;
- registro de ejecuciones y auditoría;
- aislamiento de consultas administrativas por organización y sucursal;
- permisos de lectura y administración.

## Pendiente

- implementar los disparadores de membresía, pago y reserva que hoy sólo
  aparecen en la interfaz;
- aplicar contratos tipados de disparador, condición y acción;
- usar `retryLimit`, backoff, estado terminal e idempotencia durable;
- evitar duplicados después de una caída entre efecto y confirmación;
- alinear estados de ejecución y permisos granulares;
- separar la lectura de reglas de la lectura de notificaciones en frontend;
- historial operativo, vista previa, observabilidad y pruebas de concurrencia.

Deuda rectora: `AUT-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.
