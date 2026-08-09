# Diseño rector de Automatizaciones y Kiosco

Estado: DISEÑO APROBADO / IMPLEMENTACIÓN INICIADA
Corte: 30 de julio de 2026  
Rama: `feature/automations-self-service-kiosk`

## 0. Decisión de producto actualizada — 3 de agosto de 2026

El kiosco no representa una computadora registrada ni exige provisionar un ID
por navegador. `self_service_terminal` se conserva como perfil lógico de
capacidades de una sucursal. Recepción abre el modo kiosco desde una sesión
autenticada; el backend emite la sesión restringida y el navegador elimina las
credenciales operativas. Para regresar a POS o recepción se requiere iniciar
sesión nuevamente.

Se separan tres modalidades de tarjeta:

- **Recepción con terminal independiente:** recepción captura el importe en la
  terminal física, registra `CARD_MANUAL` y valida el pago en GymOS. El dueño o
  el administrador puede deshabilitar esta modalidad.
- **Recepción con Mercado Pago Point integrado:** GymOS enviará una order a la
  terminal Point y conciliará su resultado. Requiere cuenta vinculada, terminal
  en modo PDV, idempotencia y notificación verificada. El dueño o administrador
  decide si queda disponible para recepción.
- **Kiosco con Stripe:** self checkout y domiciliación usan Stripe Checkout o
  componentes alojados/tokenizados. El socio captura sus datos de tarjeta;
  GymOS no recibe PAN completo ni CVV y sólo confirma efectos después de un
  webhook firmado e idempotente.

No se debe confundir la terminal lógica de kiosco con una terminal física de
cobro de Mercado Pago.

## 1. Dictamen ejecutivo

Los dos módulos tienen una base reutilizable, pero ninguno está listo para
declararse cerrado:

- **Automatizaciones está LISTO PARA ACEPTACIÓN**. Permite crear reglas, habilitarlas,
  ejecutarlas manualmente y programar un intervalo que genera una notificación
  interna. Pagos, reservas y membresías ya ingresan por una bandeja durable, conservan una
  ocurrencia idempotente y aparecen en el historial operativo con sus
  reintentos. Las condiciones están tipadas y el fallo definitivo puede
  reintentarse o descartarse con auditoría. Los eventos de domiciliación ya
  entran por la misma bandeja; el historial está paginado y las métricas se
  separan por tenant. Las pruebas PostgreSQL de concurrencia/caída ejecutaron
  34 escenarios reales sin fallos ni omisiones; falta repetirlas en CI.
- **Kiosco está LISTO PARA ACEPTACIÓN funcional y todavía requiere evidencia
  externa antes de un piloto financiero**. El lanzamiento autenticado, la sesión restringida, privacidad,
  inactividad, capacidades, Stripe Checkout y la domiciliación tokenizada y
  revocable ya están implementados, junto con historiales paginados y
  telemetría tenant, además de la reserva de Wellness sin costo. Falta publicar
  la ejecución del ciclo en sandbox/infraestructura real. El
  producto acepta **exclusivamente tarjeta de crédito o débito** para cualquier
  cobro en autoservicio; no hay efectivo, transferencia ni tarjeta manual.

El diseño objetivo conserva una regla central: **Automatizaciones orquesta;
Kiosco presenta y solicita; los módulos propietarios deciden y ejecutan**.
Automatizaciones no debe escribir directamente membresías, pagos o reservas.
Kiosco no debe calcular precios, elegibilidad, vigencias ni aprobar pagos.

## 2. Fronteras de responsabilidad

### 2.1 Automatizaciones

Es responsable de:

- registrar reglas tipadas por organización y alcance de sucursal;
- recibir eventos o materializar ocurrencias programadas;
- evaluar condiciones declarativas;
- crear trabajos idempotentes;
- invocar una acción pública del módulo propietario;
- registrar intentos, resultado, duración y error;
- aplicar reintentos y enviar a revisión los fallos definitivos.

No es responsable de:

- modificar tablas de otros módulos;
- decidir vigencias, descuentos, elegibilidad o estados de pago;
- entregar directamente correo o WhatsApp;
- aceptar tipos de disparador, condición o acción sin contrato.

### 2.2 Kiosco

Es responsable de:

- resolver el perfil lógico de capacidades de la sucursal que abrió recepción;
- abrir una sesión pública efímera y restringida;
- verificar al socio sin facilitar enumeración;
- mostrar únicamente capacidades habilitadas;
- solicitar cotizaciones y operaciones a los módulos propietarios;
- cerrar la sesión por acción, expiración o inactividad;
- auditar toda mutación con origen `SELF_SERVICE_KIOSK`.

No es responsable de:

- validar efectivo, transferencia o tarjeta manual;
- activar membresías antes de un pago completado;
- decidir si un plan, beneficio o recurso es elegible;
- usar el código visible del perfil lógico como credencial;
- convertirse en un portal remoto permanente del socio.

## 3. Estado real de Automatizaciones

### 3.1 Implementación aprovechable

- `automation_rule` y `automation_execution` están aisladas por organización y
  sucursal.
- La API exige `automation.read` para lectura, `automation.execute` o
  `automation.manage` para ejecución manual y `automation.manage` para
  administrar reglas.
- Las reglas manuales, programadas y disparadas por pagos, reservas o
  membresías pueden crear
  registros idempotentes en `notification_log`.
- El scheduler consulta reglas vencidas y usa una actualización condicional de
  `locked_until` para reducir ejecuciones simultáneas.
- `automation_trigger_event` deduplica eventos por tenant/tipo/referencia y usa
  un lease recuperable para procesarlos.
- `retryLimit` produce backoff, `RETRY_SCHEDULED` y `DEAD_LETTER`; la versión
  funcional de la regla no cambia al tomar o liberar el lease.
- La interfaz ofrece vista previa e historial con intento, duración, referencia
  y error sanitizado.
- La interfaz ofrece una bandeja de fallos definitivos con reintento o descarte
  y motivo auditable.
- La creación, habilitación, deshabilitación y ejecución generan auditoría.
- El intervalo programado se limita entre 1 y 10,080 minutos.

### 3.2 Fallos y deuda

| Prioridad | Hallazgo | Impacto |
|---|---|---|
| RESUELTO | Pagos, reservas, ciclo de membresía y cargos domiciliados tienen listeners/productores y bandeja durable. | Los disparadores visibles cuentan con productor y consumidor reales. |
| RESUELTO | `retryLimit` participa en ejecuciones programadas y de eventos con backoff acotado y cierre `DEAD_LETTER`. | Los fallos ya tienen presupuesto y estado terminal. |
| RESUELTO Y EJECUTADO | La notificación y la ejecución usan llaves idempotentes estables; Testcontainers cubre competencia de leases, deduplicación, vencimiento, equidad y paginación tenant. | El corte ejecutó 34 escenarios PostgreSQL reales sin fallos ni omisiones. |
| RESUELTO | El alta rechaza `actionType` y disparadores desconocidos. | Ya no se registran falsos positivos por acciones inexistentes. |
| RESUELTO | `conditionJson` valida campos, tipos, rangos e identificadores según cada disparador y la UI sólo presenta campos guiados. | Las reglas visibles no aceptan condiciones ambiguas o desconocidas. |
| RESUELTO | Los estados operativos son `PROCESSING`, `SUCCEEDED`, `FAILED`, `RETRY_SCHEDULED` y `DEAD_LETTER`. | API, persistencia e interfaz comparten el mismo diagnóstico. |
| RESUELTO | El formulario ofrece vista previa y el historial paginado muestra error, intento, referencia, duración y total. | Operación puede diagnosticar grandes volúmenes sin consultar la base. |
| RESUELTO EN UI | La carga conjunta de reglas y notificaciones fallaba si el usuario no podía leer notificaciones y los botones no se ocultaban por permiso. | Las cargas y acciones visibles ahora respetan permisos por separado. |
| RESUELTO | `automation.execute` protege ejecuciones manuales y `automation.scheduler.manage` controla pausa/reanudación por tenant con auditoría. | Un incidente de un gimnasio puede aislarse sin detener a los demás. |
| RESUELTO | Los lotes programados y de eventos se intercalan por organización y excluyen tenants pausados al seleccionar y reclamar. | Un tenant ruidoso no puede monopolizar el lote global. |

## 4. Diseño objetivo de Automatizaciones

### 4.1 Catálogo tipado V1

Disparadores iniciales:

- `MANUAL`;
- `SCHEDULED`;
- `MEMBERSHIP_EXPIRING`;
- `MEMBERSHIP_EXPIRED`;
- `PAYMENT_COMPLETED`;
- `PAYMENT_PENDING_VALIDATION`;
- `BOOKING_CREATED`;
- `BOOKING_CONFIRMED`;
- `BOOKING_CANCELLED`.
- `RECURRING_CHARGE_DUE`;
- `RECURRING_CHARGE_SUCCEEDED`;
- `RECURRING_CHARGE_FAILED`.

Acciones iniciales:

- `CREATE_NOTIFICATION`;
- `CREATE_STAFF_TASK`, si se incorpora una bandeja operativa;
- `QUEUE_MESSAGE`, sólo cuando exista un adaptador de notificaciones con
  consentimiento, plantilla y proveedor.
- `REQUEST_RECURRING_CHARGE`, únicamente a través del puerto público de pagos y
  con una autorización de domiciliación vigente.

Cada combinación debe publicar un esquema de configuración. La API rechaza
tipos desconocidos y la interfaz presenta campos guiados; el JSON queda como
detalle interno, no como formulario principal.

### 4.2 Ejecución durable

1. Un evento de dominio o el scheduler crea una ocurrencia con una llave estable.
2. Se genera una ejecución única por
   `organization + rule + triggerInstance + ruleVersion`.
3. Un worker reclama la ejecución mediante lease.
4. La condición se evalúa con una instantánea mínima y datos consultados por
   puertos públicos.
5. La acción se invoca con su propia llave de idempotencia.
6. Se registra `SUCCEEDED`, `SKIPPED`, `RETRY_SCHEDULED` o `DEAD_LETTER`.
7. Los reintentos usan backoff acotado y nunca duplican el efecto.

La ejecución debe conservar como mínimo: regla y versión, tenant, sucursal,
llave de idempotencia, referencia del disparador, intento, estado, programada,
inicio, fin, siguiente intento, código de error y resumen sanitizado.

### 4.3 Experiencia administrativa

- biblioteca de plantillas frecuentes;
- constructor por evento, condición y acción;
- alcance explícito: organización o sucursal;
- zona horaria y siguiente ejecución legibles;
- validación y vista previa de destinatarios sin ejecutar;
- prueba manual marcada como prueba;
- historial por regla con filtros, intento, duración y error;
- pausar, reactivar, duplicar y archivar;
- permisos separados para leer, administrar, ejecutar y gestionar schedules.

## 5. Estado real del Kiosco

### 5.1 Implementación aprovechable

- perfil lógico ligado a organización y sucursal, sin registrar computadoras;
- modo de navegador de hasta doce horas con token aleatorio de 256 bits
  almacenado como hash y reinicio de identidad tras tres minutos de inactividad;
- verificación genérica por correo/teléfono y fecha de nacimiento;
- bloqueo de sesión por intentos y rate limiting distribuido con Redis fuera de
  perfiles local/test;
- resumen acotado de membresías y pagos pendientes;
- uso de `MembershipOperations`, `ClientLookupOperations` y
  `BookingOperations`, sin acceso desde aplicación a infraestructura ajena;
- auditoría de sesión, verificación, reservas, renovaciones y cambios;
- identidad visual de la organización;
- cotización y efectos finales delegados a membresías/ventas.

### 5.2 Fallos y deuda

| Prioridad | Hallazgo | Impacto |
|---|---|---|
| RESUELTO EN BLOQUE 0 | Renovar y cambiar plan enviaban `method: CASH` y `validateNow: true` desde un cliente público. | El contrato público ya no acepta esos campos y las operaciones permanecen bloqueadas hasta conectar tarjeta en línea. |
| RESUELTO EN BLOQUE 0 | `allowed_actions_json` no se leía ni aplicaba y existían claves incompatibles. | V60 unifica las claves; backend y frontend aplican las capacidades efectivas. |
| RESUELTO | `KIOSK-MAIN` era ambiguo sin contexto autenticado. | El lanzamiento ahora parte de la sesión de recepción, resuelve organización y sucursal y crea/reutiliza un perfil lógico; no se usa como identidad de computadora. |
| ACEPTADO POR PRODUCTO | No existe una credencial o vínculo de instalación del navegador. | Es intencional: recepción autenticada abre el modo restringido; no se registran computadoras ni se distribuyen credenciales de dispositivo. |
| RESUELTO | No había cierre automático por inactividad. | Backend valida tres minutos de inactividad verificada y frontend reinicia la pantalla a los 150 segundos, ocultando la PII del socio. |
| RESUELTO | El límite de intentos se aplicaba sólo por sesión. | Se agregaron límites por IP, sesión e identificador anonimizado más perfil lógico, y sólo un usuario con permiso puede abrir nuevas sesiones de navegador. |
| RESUELTO | La interfaz ya contempla renovar una membresía vencida y contratar una membresía inicial. | Ambos flujos crean venta/pago pendiente y abren Stripe Checkout sin activar por el redirect. |
| RESUELTO | La llave de idempotencia se generaba de nuevo en cada clic. | Ahora se conserva en la sesión del navegador y se respalda con unicidad en servidor/proveedor durante los reintentos de una misma intención. |
| RESUELTO | El kiosco obtiene una cotización autoritativa y confirma plan, lista, descuento y total antes de generar la operación Stripe. | El socio ve el compromiso final antes de abandonar GymOS. |
| RESUELTO | `contact-data`, Checkout y domiciliación tokenizada/revocable ya operan desde la sesión verificada. | El kiosco sólo ofrece domiciliación cuando Stripe recurrente está habilitado por el gimnasio. |
| RESUELTO SEGÚN PRODUCTO | No existe CRUD de computadoras/terminales físicas. | Las capacidades se administran por sucursal; cualquier navegador abierto por recepción usa el perfil lógico. |
| IMPLEMENTADO, VALIDACIÓN EXTERNA PENDIENTE | Las pruebas unitarias pasaron 318 escenarios backend y 67 frontend; Testcontainers PostgreSQL pasó 34 y el E2E de kiosco pasó 7 recorridos. La suite E2E completa cubre 38 recorridos. La suite Stripe test está lista y sus 3 escenarios se omiten si no hay secreto sandbox. | Falta ejecutar `stripeTest` con `GYMOS_STRIPE_TEST_SECRET_KEY`/cuenta Connect test y publicar esa evidencia en CI. |
| RESUELTO | La IP efectiva sólo acepta `Forwarded`/`X-Forwarded-For` si el peer inmediato está en `GYMOS_TRUSTED_PROXIES` y recorre la cadena de derecha a izquierda. | Se evita confiar en cabeceras falsificadas sin romper límites detrás de proxies declarados. |

## 6. Diseño objetivo del Kiosco

### 6.1 Perfil lógico de kiosco

Cada sucursal conserva un perfil lógico reutilizable con:

- UUID interno técnico y organización/sucursal propietarias;
- etiqueta humana estable, sin identificar una computadora;
- estado habilitado/deshabilitado;
- conjunto canónico de capacidades;
- auditoría de cambios y de quién abrió cada sesión restringida.

No existe activación, secreto, inventario ni asignación de navegadores. Una
cuenta de recepción con `selfservice.session.launch` abre el perfil de su
sucursal; el backend emite una sesión limitada y el frontend elimina las
credenciales de recepción. Varias pantallas pueden usar el mismo perfil lógico.

### 6.2 Capacidades canónicas

- `membershipRenewal`;
- `membershipEnrollment`;
- `membershipPlanChange`;
- `resourceBooking`;
- `wellnessBooking`, sólo para servicios sin costo;
- `profileUpdate`;
- `autopay`.

El backend valida la capacidad en cada endpoint y la sesión devuelve sólo las
capacidades efectivas. El frontend renderiza a partir de esa respuesta. La
autorización comercial del terminal y la disponibilidad técnica son datos
distintos: por ejemplo, `membershipRenewalAllowed=true` no habilita el botón si
`onlineCardPaymentsReady=false`.

### 6.3 Sesión e identidad

- token en memoria o `sessionStorage`, nunca persistente a largo plazo;
- expiración absoluta y expiración por inactividad de 2–3 minutos después de
  verificar;
- actualización atómica de `last_request_at`;
- cierre y limpieza visual al vencer, cerrar o quedar inactivo;
- respuestas `Cache-Control: no-store`;
- errores genéricos de verificación;
- rate limit por IP confiable, terminal, sesión e identificador hasheado;
- preferencia futura por QR/PIN de socio rotatable; contacto + nacimiento puede
  mantenerse como alternativa configurable.

### 6.4 Membresías y pagos

Flujo obligatorio:

1. El socio elige operación.
2. Backend devuelve una cotización con lista, beneficio, descuento, total,
   vigencia resultante y estado esperado.
3. El kiosco pide confirmación explícita.
4. Backend crea una intención `CARD_ONLINE` y solicita una sesión efímera al
   proveedor. El navegador nunca envía método, referencia ni `validateNow`.
5. Los campos sensibles se capturan en componentes alojados/tokenizados por la
   pasarela; GymOS no recibe PAN completo ni CVV.
6. Sólo `PaymentCompletedV1`, originado por un webhook firmado, idempotente y
   conciliado con importe/moneda, activa la renovación o el cambio.
7. El kiosco muestra confirmación final o un estado recuperable; nunca marca
   pago por la respuesta del navegador.

La llave de idempotencia se crea al iniciar la intención y se conserva hasta
obtener un resultado definitivo. Renovación vencida, alta inicial y cambio
deben usar los mismos cotizadores, descuentos y política transversal de
elegibilidad que recepción.

Las reservas de recursos y bienestar se limitan a operaciones incluidas o con
total cero y se confirman directamente. Por decisión de producto no se
implementarán recursos reservables ni servicios de Wellness con costo.

### 6.5 Domiciliación

La domiciliación no es “guardar una tarjeta” en GymOS. Debe funcionar así:

1. El socio acepta términos, frecuencia, importe o regla de cálculo y política
   de cancelación.
2. La pasarela tokeniza la tarjeta y devuelve identificadores opacos de cliente,
   método y mandato.
3. GymOS almacena sólo proveedor, identificadores opacos, marca/últimos cuatro,
   vencimiento, consentimiento versionado y estado.
4. La membresía conserva la preferencia de renovación automática, pero no datos
   de tarjeta.
5. Pagos materializa la ocurrencia durable; Membresías prepara la venta
   idempotente y Pagos ejecuta el cargo. Automatizaciones observa el evento
   para notificaciones, pero no es requisito para la corrección financiera.
6. Un webhook firmado publica éxito o fallo. Membresías sólo renueva con éxito.
7. Los fallos usan reintentos acotados, aviso al socio y estado
   `PAST_DUE`; nunca generan múltiples cargos por la misma vigencia.
8. El socio puede pausar/cancelar la autorización y sustituir su método.

Tablas implementadas:

- `payment_customer`;
- `stored_payment_method`;
- `recurring_payment_mandate`;
- `recurring_charge_occurrence`;
- `payment_provider_operation`;
- `payment_provider_webhook_event`.

No se almacenarán PAN, banda, NIP ni CVV. La selección de proveedor debe
considerar tokenización, 3DS, webhooks firmados, cargos recurrentes, reembolsos
y conciliación en México.

### 6.6 Privacidad y experiencia

- pantalla táctil de una sola tarea por paso;
- resumen mínimo, sin historial financiero completo;
- confirmación antes de toda mutación;
- ocultar correo y teléfono salvo el fragmento necesario;
- comprobante alojado por Stripe recuperado del `latest_charge` conciliado, sin
  almacenar datos de tarjeta ni confiar en la página de retorno;
- actualización sólo de campos permitidos y, cuando aplique, verificación del
  nuevo contacto;
- retorno automático a la pantalla inicial;
- métricas sin PII: sesiones iniciadas/verificadas, abandono, operación,
  latencia, error y terminal.

## 7. Orden de implementación

### Bloque 0 — Seguridad y verdad funcional

1. Retirar efectivo, transferencia, tarjeta manual y `validateNow` del contrato
   público; bloquear operaciones financieras mientras no exista pasarela.
2. Definir y aplicar capacidades del perfil lógico de sucursal.
3. Implementar actualización acotada de datos de contacto.
4. Sustituir el lanzamiento público ambiguo por lanzamiento autenticado desde
   recepción y contexto de sucursal.
5. Limitar Automatizaciones a disparadores/acciones realmente soportados o
   implementar sus consumidores antes de mostrarlos.
6. Rechazar acciones desconocidas y alinear estados de ejecución.

### Bloque 1 — Durabilidad

1. Ejecución idempotente y versionada.
2. Reintentos con backoff y estado terminal.
3. Bandeja durable para eventos/ocurrencias.
4. Inactividad real de kiosco e idempotencia persistente de intenciones.
5. Pruebas PostgreSQL de concurrencia y fallos intermedios.

### Bloque 2 — Producto operable

1. Constructor guiado y plantillas de automatización.
2. Historial y diagnóstico de ejecuciones.
3. Administración de capacidades del perfil lógico de sucursal.
4. Cotización/confirmación de membresía, vencidas y alta inicial.
5. Catálogo y reserva sin costo de recursos y profesionales de bienestar, si
   se prioriza; no se implementarán reservas con costo.
6. Actualización de datos permitidos.
7. Administración de método y consentimiento de domiciliación.

### Bloque 3 — Piloto

1. E2E por rol, tenant, sucursal, terminal y expiración.
2. Threat model y prueba de enumeración/fuerza bruta.
3. Telemetría, alertas y runbook verificable.
4. Prueba de recuperación tras caída sin efectos duplicados.
5. Proveedor externo sólo detrás de puertos y webhooks firmados.

## 8. Criterios de cierre

Automatizaciones podrá subir a `ESTABILIZADO` cuando:

- cada opción visible tenga disparador y acción reales;
- una ocurrencia no produzca dos efectos tras concurrencia, timeout o caída;
- `retryLimit` determine intentos reales y exista estado de fallo definitivo;
- el historial explique qué pasó sin revisar la base;
- permisos y aislamiento tenant/sucursal tengan pruebas positivas y negativas.

Kiosco podrá subir a `ESTABILIZADO` cuando:

- no exista método manual en el contrato público y ningún navegador pueda
  aprobar un pago;
- cada endpoint aplique capacidades del perfil lógico de sucursal;
- el lanzamiento autenticado seleccione inequívocamente organización y
  sucursal, sin registrar ni asignar la computadora del navegador;
- la sesión cierre por inactividad y resista creación masiva de sesiones;
- renovación vigente/vencida, alta, cambio, reserva y pago pendiente tengan E2E;
- se demuestre que no activa membresía sin `PaymentCompletedV1`;
- la domiciliación no pueda duplicar un cargo ni renovar después de revocar el
  consentimiento;
- consola, auditoría, telemetría y runbook permitan operarlo en un gimnasio.

El hardware de control de acceso físico permanece fuera de este alcance.

## 9. Primer bloque implementado

El 30 de julio de 2026 inició el Bloque 0:

- capacidades canónicas leídas desde `allowed_actions_json`, devueltas a la
  sesión y aplicadas en backend para reservas, membresías y perfil;
- compatibilidad temporal de lectura con las claves antiguas y migración V60 a
  un único contrato;
- `onlineCardPaymentsReady=false` y
  `recurringCardPaymentsReady=false` mientras no exista adaptador real;
- renovación y cambio de plan bloqueados antes de tocar ventas/membresías;
- el contrato público ya no acepta método, referencia ni `validateNow`;
- actualización real y auditada de correo/teléfono desde una sesión verificada;
- frontend sin efectivo ni pagos manuales, preparado para tarjeta,
  domiciliación y Wellness exclusivamente sin costo;
- idempotencia estable en el navegador durante reintentos de una misma
  intención;
- Automatizaciones acepta nuevas reglas sólo para disparadores reales
  (`MANUAL`, `SCHEDULED`) y acción `CREATE_NOTIFICATION`; las opciones ficticias
  se retiraron de la interfaz.
- pruebas unitarias de política/servicio y E2E de navegador demuestran que no
  aparece pago manual, la tarjeta queda cerrada sin proveedor y el contacto se
  actualiza mediante el contrato permitido.

## 10. Segundo bloque iniciado — navegador y configuración de cobros

- el inicio de kiosco requiere `selfservice.session.launch` y una sucursal
  activa; dejó de ser una operación pública basada en encabezados declarativos;
- al abrir el modo kiosco el frontend conserva sólo el token restringido y
  elimina access/refresh tokens de recepción;
- la sesión verificada expira a los tres minutos de inactividad y puede
  reiniciarse para el siguiente socio sin recuperar permisos de recepción;
- cada ejecución de navegador tiene un máximo operativo de doce horas;
- respuestas de autoservicio usan `Cache-Control: no-store`;
- el rate limit incorpora terminal lógica e identificador anonimizado, y el
  contador de intentos se incrementa atómicamente;
- correo y teléfono salen enmascarados y un PATCH conserva campos omitidos;
- `organization_payment_configuration` separa terminal manual, Mercado Pago
  Point, Stripe self checkout y Stripe recurrente;
- sólo dueño/administrador puede modificar modalidades; recepción puede
  consultar la configuración efectiva;
- Mercado Pago y Stripe no pueden activarse hasta que exista una vinculación
  real de cuenta;
- notificaciones creadas por automatizaciones incorporan llave idempotente por
  regla y ocurrencia; el registro de fallos ya no comparte la transacción del
  efecto;
- `retryLimit` controla reintentos con backoff acotado y una ocurrencia conserva
  su identidad hasta terminar en éxito o `DEAD_LETTER`;
- migración/onboarding crean el perfil lógico por sucursal y el primer
  lanzamiento lo completa para sucursales nuevas; nunca se solicita código ni
  nombre de computadora.

## 10.1 Bloque de Automatizaciones por eventos

- V66 agrega `automation_trigger_event` y `automation_execution.rule_version`;
- los listeners Modulith de ventas y reservas materializan ocurrencias dentro
  de una entrega transaccional y el worker las reclama con lease;
- cada regla usa una llave `organization + rule + trigger + ruleVersion`; la
  notificación comparte la misma identidad para tolerar reinicios entre efecto,
  ejecución y cierre del inbox;
- la ejecución se reserva primero como `PROCESSING`; sólo después se crea el
  efecto y se cierra el intento, eliminando la ventana sin identidad durable;
- reintentar conserva la versión original incluso si el estado administrativo
  de la regla cambia después;
- el historial operativo y la vista previa ya están disponibles en la consola;
- `MEMBERSHIP_EXPIRING` y `MEMBERSHIP_EXPIRED` usan productores transaccionales,
  marcador diario/terminal e identidad funcional estable por vigencia;
- las condiciones de los disparadores visibles se validan con contrato tipado;
- la consola lista fallos definitivos y permite reintentar o descartar con
  motivo obligatorio y auditoría;
- `RECURRING_CHARGE_DUE`, `RECURRING_CHARGE_SUCCEEDED` y
  `RECURRING_CHARGE_FAILED` usan la misma bandeja durable y ya aparecen en el
  selector.

## 11. Tercer bloque iniciado — proveedores y conciliación

- V64 agrega conexiones de proveedor, solicitudes OAuth de un solo uso,
  operaciones idempotentes y deduplicación durable de webhooks;
- las credenciales de Mercado Pago se cifran con AES-GCM y los tokens nunca se
  copian al JSON de metadatos;
- Stripe usa una cuenta Express conectada por gimnasio y Account Links; el
  kiosco redirige a Checkout alojado y GymOS no recibe PAN ni CVV;
- Mercado Pago usa OAuth por gimnasio, permite consultar una Point y la
  configura en modo PDV; el POS envía Orders con llave de idempotencia;
- tanto Stripe como Mercado Pago crean primero venta/pago pendiente. Sólo un
  webhook firmado o la conciliación autoritativa puede completar el pago;
- el monto, moneda, organización, sucursal, venta, pago y método se cotejan
  antes de publicar la finalización financiera;
- un worker consulta operaciones pendientes durante siete días para recuperar
  webhooks perdidos sin emitir un segundo cargo;
- el detalle y POS no permiten validar manualmente pagos administrados por
  proveedor;
- la domiciliación se habilita por gimnasio sólo junto con Stripe Checkout;
  usa consentimiento versionado y Checkout `mode=setup`, no guarda tarjeta en
  GymOS y no crea una suscripción ciega: cada vigencia materializa una venta y
  un PaymentIntent off-session propios;
- éxito/fallo se decide por webhook firmado o conciliación autoritativa. El
  éxito completa la venta y extiende la membresía mediante el flujo financiero
  idempotente existente; el fallo agenda hasta tres intentos y deja `PAST_DUE`.
