# Autoservicio y kiosco

Estado: `LISTO PARA ACEPTACIÓN` (la validación externa de Stripe sandbox se ejecuta por separado)

## Implementado

- sesión temporal con token aleatorio almacenado como hash;
- verificación genérica de socio por contacto y fecha de nacimiento;
- rate limiting, bloqueo por intentos y auditoría;
- resumen de membresía y pagos pendientes;
- reserva de recursos mediante la API pública del módulo propietario;
- capacidades canónicas por perfil lógico de sucursal, expuestas a la sesión y aplicadas en cada
  endpoint implementado;
- actualización real y auditada de correo/teléfono desde sesión verificada;
- contrato de renovación/cambio sin método manual ni `validateNow`, bloqueado
  hasta disponer de pasarela;
- marca tenant y superficie sin navegación administrativa.
- lanzamiento desde recepción con permiso dedicado, sin registrar una
  computadora o navegador;
- apertura en una pestaña aislada sin colocar la credencial de kiosco en la URL;
  recepción conserva su consola y la pestaña pública elimina el traspaso al consumirlo;
- cierre por inactividad, reinicio para el siguiente socio y límite operativo
  de doce horas;
- contacto enmascarado, PATCH parcial y respuestas `no-store`;
- perfil lógico creado automáticamente por sucursal en migración/onboarding o
  primer lanzamiento; varias pantallas de navegador pueden abrirlo sin
  registrar ni asignar computadoras.
- vinculación Stripe Connect por gimnasio y Checkout alojado para renovación o
  cambio de plan;
- contratación de membresía inicial desde el kiosco, con selección de plan,
  venta/pago pendiente y activación exclusiva por webhook conciliado;
- venta y pago pendientes antes de abandonar GymOS, con idempotencia estable
  y persistida durante la sesión del navegador para recuperar un fallo de red
  o un regreso desde Stripe;
- webhook Stripe firmado, consulta autoritativa, conciliación de monto/moneda y
  worker de recuperación de webhooks perdidos;
- página de retorno neutral que nunca presenta el redirect como comprobante de
  pago;
- consentimiento versionado de domiciliación, captura de tarjeta en Stripe
  Checkout `mode=setup` y persistencia exclusiva de identificadores opacos,
  marca, últimos cuatro y vencimiento;
- mandato revocable por socio, reemplazo seguro del método y cancelación de
  cargos aún no iniciados;
- ciclo recurrente materializado por vigencia: venta/pago idempotente,
  `PaymentIntent` off-session, webhook de éxito/fallo, tres intentos acotados y
  estado `PAST_DUE`;
- extensión de membresía únicamente a través del mismo evento financiero
  conciliado que usa el resto de ventas; la ocurrencia, venta, pago y periodo
  conservan llaves estables ante webhooks o workers repetidos.
- historial paginado por tenant de consentimientos, cargos y webhooks, visible
  para quienes pueden consultar la configuración de pagos y sin payloads ni
  referencias completas de tarjeta;
- métricas Micrometer por tenant para cargos, webhooks, conciliación y
  contención de leases;
- suites opt-in `postgresIntegrationTest` y `stripeTest` para concurrencia,
  duplicados, timeouts, `requires_action`, rechazos y recuperación posterior a
  una caída.
- cotización autoritativa final, incluidos beneficios y descuento, antes de
  crear la venta o abandonar GymOS para abrir Stripe Checkout;
- configuración administrativa de capacidades por sucursal, sin alta ni
  asignación de computadoras o terminales físicas;
- citas wellness gratuitas desde el kiosco; la API sólo lista servicios activos
  con precio cero y rechaza cualquier servicio con costo;
- resolución de IP que ignora `Forwarded`/`X-Forwarded-For` salvo cuando el peer
  inmediato pertenece a los CIDR configurados en `GYMOS_TRUSTED_PROXIES`;
- diagnóstico de mandatos `PAST_DUE`/`PAUSED` y sustitución consentida del
  método tokenizado mediante un nuevo Setup de Stripe;
- seguimiento de pagos Stripe propios del socio y acceso al comprobante
  `receipt_url` del cargo conciliado, sin tratar el redirect como confirmación;
- tablero operativo de pagos por tenant, gauges periódicos, alertas Prometheus
  y runbook de desconexión, rotación y recuperación.

## Pendiente

- ejecutar `stripeTest` con credenciales Stripe exclusivamente test y cuenta
  Connect sandbox. En este corte `postgresIntegrationTest` pasó 34 escenarios
  reales y el E2E de kiosco pasó 7 recorridos; Stripe omitió sus 3 escenarios
  porque el entorno no contiene `GYMOS_STRIPE_TEST_SECRET_KEY`;
- ejecutar los recorridos E2E y las suites conectadas en el ambiente de
  integración para publicar evidencia multi-tenant, expiración, concurrencia y
  recuperación real. No se contempla una consola de computadoras registradas.

Decisión de producto: no se implementarán pagos para recursos reservables ni
servicios de Wellness con costo. Las reservas de autoservicio permanecen
incluidas/sin cobro; Stripe se limita a membresías y domiciliaciones.

Deuda rectora: `SELF-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.

Runbook: `../../docs/operations/kiosk-troubleshooting.md`.
Operación Stripe: `../../docs/operations/stripe-automation-recovery.md`.
