# Automatizaciones

Estado: `PARCIAL`

## Implementado

- alta, consulta, habilitación, deshabilitación y ejecución manual de reglas;
- programación por intervalo con `next_run_at` y reclamo condicional mediante
  `locked_until`;
- acción inicial `CREATE_NOTIFICATION` sobre la cola interna;
- alta limitada a disparadores realmente ejecutables (`MANUAL`, `SCHEDULED`) y
  acción soportada `CREATE_NOTIFICATION`;
- validación del mensaje y rechazo de tipos desconocidos;
- formulario guiado para intervalo, asunto y mensaje, con explicación de casos
  de uso;
- registro de ejecuciones y auditoría;
- aislamiento de consultas administrativas por organización y sucursal;
- permisos de lectura y administración.

## Pendiente

- implementar entrega durable para disparadores de membresía, pago, reserva y
  cobro recurrente antes de volver a mostrarlos en la interfaz;
- aplicar contratos tipados de disparador, condición y acción;
- usar `retryLimit`, backoff, estado terminal e idempotencia durable;
- evitar duplicados después de una caída entre efecto y confirmación;
- completar estados de reintento/fallo terminal y permisos granulares;
- historial operativo, vista previa, observabilidad y pruebas de concurrencia.

Deuda rectora: `AUT-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.
