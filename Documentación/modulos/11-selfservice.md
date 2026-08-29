# Autoservicio y kiosco

Estado: `FUNCIONAL / LISTO PARA ACEPTACIÓN CONECTADA`
Última revisión: 26 de agosto de 2026

## Implementado

- sesión temporal con token aleatorio almacenado como hash;
- apertura exclusiva desde Recepción; dueño, administrador, gerente y soporte
  pueden configurar lo permitido por sus permisos, pero no lanzan sesiones;
- verificación genérica de socio por contacto y fecha de nacimiento, incluso
  cuando todavía no tiene ninguna membresía y sin limitar la identidad a su
  sucursal de alta;
- alta de un socio nuevo desde el propio kiosco cuando Recepción habilita la
  capacidad `clientRegistration` para la sucursal; el alta deja la sesión
  verificada y abre la contratación del primer plan; el selector de entrada
  distingue visualmente entre socio existente y registro nuevo;
- membresías, pagos, reservas y domiciliación disponibles también en una sede
  secundaria cuando el plan vigente es multi-sucursal;
- rate limiting, bloqueo por intentos y auditoría;
- resumen de membresía y pagos pendientes;
- reserva de recursos mediante la API pública del módulo propietario; el catálogo
  oculta desde el inicio los recursos para los que la membresía del socio no es
  elegible y vuelve a validar la elegibilidad al consultar horarios y reservar;
- capacidades canónicas por perfil lógico de sucursal, expuestas a la sesión y aplicadas en cada
  endpoint implementado;
- capacidades configuradas persistidas como intención, sin convertir a `false`
  compra, renovación, cambio o domiciliación porque Stripe siga pendiente;
- disponibilidad efectiva resuelta al ejecutar como intención configurada más
  readiness de Stripe; el catálogo y la cotización permanecen consultables y
  Checkout continúa protegido por readiness;
- reinicio de sesión que vuelve a consultar el perfil vigente del terminal, de
  modo que cambios como `clientRegistration=true` aparecen sin conservar una
  copia antigua dentro de la sesión;
- cambio entre `Ya soy socio` y `Quiero registrarme` mediante ese reinicio,
  limpiando la identidad anterior antes de capturar a la siguiente persona;
- actualización real y auditada de correo/teléfono desde sesión verificada;
- contrato de alta/renovación/cambio sin método manual ni `validateNow`,
  conectado a Stripe Checkout y confirmado sólo por webhook/conciliación;
- marca tenant, atribución GymFort y superficie sin navegación administrativa;
- feedback de sesión compacto, descartable y temporal para que un mensaje de
  éxito no permanezca visible para el siguiente socio;
- fallo de identificación con mensaje público breve dentro de la tarjeta, sin
  desplazar el formulario ni exponer detalles técnicos de la API;
- una consulta secundaria fallida de planes, paquetes o reservas se muestra en
  su sección y no convierte una identificación/alta exitosa en error de datos;
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
  venta/pago pendiente y activación exclusiva por webhook conciliado; sólo se
  muestran planes asociados a la sucursal activa;
- venta y pago pendientes antes de abandonar GymFort, con idempotencia estable
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
  crear la venta o abandonar GymFort para abrir Stripe Checkout;
- desglose de inscripción en la contratación inicial y conciliación del total
  completo por Stripe;
- configuración administrativa de capacidades por sucursal, sin alta ni
  asignación de computadoras o terminales físicas; la pantalla muestra una sola
  configuración correspondiente a la sucursal elegida en el selector global,
  guarda cada toggle automáticamente y revierte el cambio si el backend falla;
- navegación del socio organizada por tareas (`Membresías`, `Reservar`,
  `Wellness`, `Pagos`, `Mis datos` y `Domiciliación`) para evitar mezclar todos
  los formularios en una sola pantalla;
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

- resolver nombre, logo y colores desde la sucursal del kiosco, usando la marca
  de organización sólo como fallback (`CFG-003`);
- ejecutar `stripeTest` con credenciales Stripe exclusivamente test y cuenta
  Connect sandbox dentro de CI protegido; el corte actual validó backend,
  frontend, ruteo por consola, Compose y la imagen real Stripe CLI, pero la
  suite conectada continúa opt-in;
- ejecutar los recorridos E2E y las suites conectadas en el ambiente de
  integración para publicar evidencia multi-tenant, expiración, concurrencia y
  recuperación real. No se contempla una consola de computadoras registradas.

Los recursos incluidos en el plan no generan cobro. Las reservas con costo
pueden abrir Stripe Checkout y sólo se confirman después del webhook; Wellness
con costo permanece fuera del autoservicio del corte.

Deuda rectora: `SELF-001`.

Diseño objetivo: `../31-diseño-automatizaciones-kiosco.md`.

Runbook: `../../docs/operations/kiosk-troubleshooting.md`.
Operación Stripe: `../../docs/operations/stripe-automation-recovery.md`.
Pruebas Stripe: `../../docs/testing/stripe-kiosk-test-mode.md`.

## Planes privados y paquetes

La membresía actual multi-sucursal se muestra en cualquier sede cubierta. Las
nuevas altas y cambios sólo ofrecen planes con
`selfServiceAcquisitionEnabled=true`; renovar el plan que el socio ya posee
sigue permitido. Esta autorización también se valida en backend.

Los paquetes se muestran con Stripe pendiente, pero Checkout queda bloqueado.
`visitPackagePurchase` conserva por sucursal la intención del gimnasio y una
sesión nueva/reiniciada resuelve ese valor vigente. El catálogo exige además que
la definición esté activa, cubra la sucursal y permita autoservicio. Cuando
Stripe pasa a ready, Checkout se habilita sin volver a guardar configuración.
Un paquete ya pendiente o activo muestra saldo/estado y oculta el CTA para
comprar otro.

Los planes de membresía tienen su propia política
`selfServiceAcquisitionEnabled`: al apagarla quedan ocultos para altas y cambios
del kiosco, pero no para Recepción ni para renovar la membresía que el socio ya
posee. `registrationFeePolicy=WAIVED` también pertenece al plan de membresía,
no únicamente a los paquetes.
