# Diseño rector de Automatizaciones y Kiosco

Estado: DISEÑO APROBADO / IMPLEMENTACIÓN INICIADA
Corte: 30 de julio de 2026  
Rama: `feature/automations-self-service-kiosk`

## 1. Dictamen ejecutivo

Los dos módulos tienen una base reutilizable, pero ninguno está listo para
declararse cerrado:

- **Automatizaciones está PARCIAL**. Permite crear reglas, habilitarlas,
  ejecutarlas manualmente y programar un intervalo que genera una notificación
  interna. Los disparadores de membresías, pagos y reservas visibles en la
  interfaz no tienen consumidores de eventos. `retryLimit` se persiste pero no
  se usa y una acción desconocida termina registrada como completada.
- **Kiosco está PARCIAL y no es seguro todavía para un piloto financiero**. La sesión
  temporal, el token aleatorio, la verificación por contacto y fecha de
  nacimiento, los límites de frecuencia y el consumo de APIs públicas de otros
  módulos son una buena base. El primer bloque ya retiró efectivo y
  `validateNow`, aplica las capacidades y permite actualizar el contacto. Aún
  no existe pasarela y el código público `KIOSK-MAIN` sigue siendo ambiguo entre
  gimnasios. El producto
  objetivo acepta **exclusivamente tarjeta de crédito o débito** para cualquier
  operación con cobro; no habrá efectivo, transferencia ni tarjeta manual en
  autoservicio.

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

- identificar un terminal provisionado y su sucursal;
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
- usar el código visible del terminal como credencial;
- convertirse en un portal remoto permanente del socio.

## 3. Estado real de Automatizaciones

### 3.1 Implementación aprovechable

- `automation_rule` y `automation_execution` están aisladas por organización y
  sucursal.
- La API exige `automation.read` para lectura y `automation.manage` para
  mutaciones.
- Las reglas manuales y programadas pueden crear registros en
  `notification_log`.
- El scheduler consulta reglas vencidas y usa una actualización condicional de
  `locked_until` para reducir ejecuciones simultáneas.
- La creación, habilitación, deshabilitación y ejecución generan auditoría.
- El intervalo programado se limita entre 1 y 10,080 minutos.

### 3.2 Fallos y deuda

| Prioridad | Hallazgo | Impacto |
|---|---|---|
| PARCIAL | `MEMBERSHIP_EXPIRING`, `PAYMENT_COMPLETED` y `BOOKING_CONFIRMED` no tienen listeners ni bandeja durable; ya fueron retirados del alta en la interfaz hasta implementarlos. | Ya no se venden como funcionales, pero aún faltan sus consumidores. |
| P0 | `retryLimit` no participa en la ejecución. Toda regla se reprograma incluso después de fallar. | No hay reintentos ni tratamiento real de fallos. |
| P0 | Una caída después de crear el efecto y antes de registrar/reprogramar permite repetirlo al vencer el lock. | La exclusión concurrente no equivale a idempotencia ante fallos. |
| P1 | Cualquier `actionType` es aceptado; si no es `CREATE_NOTIFICATION`, no hace nada y queda `COMPLETED`. | Falso positivo operativo. |
| P1 | Sólo se valida que los JSON sean sintácticamente correctos. | Configuraciones inválidas llegan a ejecución. |
| P1 | La documentación promete `SUCCEEDED`, `SKIPPED`, `FAILED`; el código usa `COMPLETED` y `FAILED`. | Contrato y observabilidad inconsistentes. |
| PARCIAL | El formulario inicial ya no obliga a editar JSON; aún no muestra historial de ejecuciones, error, intento ni vista previa. | Se redujo el riesgo de configuración, pero el diagnóstico sigue pendiente. |
| RESUELTO EN UI | La carga conjunta de reglas y notificaciones fallaba si el usuario no podía leer notificaciones y los botones no se ocultaban por permiso. | Las cargas y acciones visibles ahora respetan permisos por separado. |
| P2 | `automation.execute` y `automation.scheduler.manage` existen, pero las rutas sólo usan `automation.manage`. | Los permisos granulares no tienen efecto. |
| P2 | El scheduler toma 25 reglas globales por fecha, sin equidad por tenant ni zona horaria de negocio. | Un tenant ruidoso puede retrasar a otros. |

## 4. Diseño objetivo de Automatizaciones

### 4.1 Catálogo tipado V1

Disparadores iniciales:

- `MANUAL`;
- `SCHEDULED_INTERVAL`;
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

- `CREATE_INTERNAL_NOTIFICATION`;
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

- terminal ligado a organización y sucursal;
- sesión de 15 minutos con token aleatorio de 256 bits almacenado como hash;
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
| P0 | `KIOSK-MAIN` es único sólo dentro de una organización. Sin contexto autenticado, la consulta pública puede encontrar varios terminales. | El kiosco autónomo es ambiguo o produce error; los encabezados públicos de contexto no son identidad de dispositivo. |
| P0 | No existe una credencial o vínculo de instalación del terminal. Conocer el código permite abrir sesiones desde cualquier cliente. | La ruta pública no demuestra que opere desde un dispositivo provisionado. |
| P1 | No hay cierre automático por inactividad. `last_request_at` existe pero no se actualiza ni valida. | PII queda visible hasta 15 minutos aunque el usuario abandone la pantalla. |
| P1 | El límite de intentos se aplica por sesión; crear sesiones nuevas reinicia el contador. No hay límite agregado por identificador anonimizado y terminal. | La protección contra adivinación se puede eludir. |
| PARCIAL | La interfaz ya contempla renovar una membresía vencida, pero no permite contratar una membresía inicial. | El alta inicial sigue pendiente del checkout con tarjeta. |
| RESUELTO EN CLIENTE | La llave de idempotencia se generaba de nuevo en cada clic. | Ahora se conserva durante los reintentos de una misma intención; falta persistencia servidor/proveedor. |
| P1 | No hay confirmación previa con cotización final, descuentos aplicados, método y estado esperado. | El socio no ve el compromiso antes de generar la operación. |
| PARCIAL | `contact-data` ya actualiza y audita correo/teléfono; checkout, Wellness y domiciliación aún no existen. | Las capacidades no listas se comunican como preparación, no como operación disponible. |
| P1 | No existe CRUD de terminales aunque está definido `selfservice.terminal.manage`. | No se pueden rotar credenciales, cambiar acciones, desactivar o consultar actividad desde la consola. |
| P1 | No hay pruebas de servicio, persistencia ni E2E del recorrido del kiosco. | Los invariantes públicos y financieros no tienen regresión demostrable. |
| P2 | `getRemoteAddr()` no documenta una política de proxies confiables. | Detrás de un proxy todos pueden compartir una IP o confiarse incorrectamente en encabezados. |

## 6. Diseño objetivo del Kiosco

### 6.1 Provisionamiento de terminal

Cada instalación debe tener:

- UUID interno;
- `publicTerminalId` aleatorio y globalmente único para la URL;
- nombre visible y sucursal;
- secreto de activación rotatable almacenado como hash o credencial de
  dispositivo equivalente;
- estado `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `REVOKED`;
- conjunto canónico de capacidades;
- fecha de último contacto, versión de cliente y metadatos mínimos;
- auditoría de alta, activación, rotación y revocación.

`KIOSK-MAIN` puede conservarse como etiqueta humana, pero no como identidad
pública ni secreto. La consola administrativa debe permitir crear, activar,
desactivar, rotar, copiar la URL y configurar capacidades.

### 6.2 Capacidades canónicas

- `membershipRenewal`;
- `membershipPlanChange`;
- `resourceBooking`;
- `wellnessBooking`;
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
   conciliado con importe/moneda, activa la renovación, el cambio o una reserva
   con costo.
7. El kiosco muestra confirmación final o un estado recuperable; nunca marca
   pago por la respuesta del navegador.

La llave de idempotencia se crea al iniciar la intención y se conserva hasta
obtener un resultado definitivo. Renovación vencida, alta inicial y cambio
deben usar los mismos cotizadores, descuentos y política transversal de
elegibilidad que recepción.

Las reservas incluidas en el plan o con total cero se confirman directamente.
Una reserva de recurso o bienestar con total mayor a cero permanece retenida
por tiempo limitado hasta recibir el evento de pago completado.

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
5. Automatizaciones materializa el intento próximo; Pagos ejecuta el cargo.
6. Un webhook firmado publica éxito o fallo. Membresías sólo renueva con éxito.
7. Los fallos usan reintentos acotados, aviso al socio y estado
   `PAST_DUE`; nunca generan múltiples cargos por la misma vigencia.
8. El socio puede pausar/cancelar la autorización y sustituir su método.

Tablas objetivo:

- `payment_customer`;
- `stored_payment_method`;
- `recurring_payment_mandate`;
- `payment_intent`;
- `payment_attempt`;
- `provider_webhook_event`.

No se almacenarán PAN, banda, NIP ni CVV. La selección de proveedor debe
considerar tokenización, 3DS, webhooks firmados, cargos recurrentes, reembolsos
y conciliación en México.

### 6.6 Privacidad y experiencia

- pantalla táctil de una sola tarea por paso;
- resumen mínimo, sin historial financiero completo;
- confirmación antes de toda mutación;
- ocultar correo y teléfono salvo el fragmento necesario;
- comprobantes en almacenamiento de objetos con tipo/tamaño acotados;
- actualización sólo de campos permitidos y, cuando aplique, verificación del
  nuevo contacto;
- retorno automático a la pantalla inicial;
- métricas sin PII: sesiones iniciadas/verificadas, abandono, operación,
  latencia, error y terminal.

## 7. Orden de implementación

### Bloque 0 — Seguridad y verdad funcional

1. Retirar efectivo, transferencia, tarjeta manual y `validateNow` del contrato
   público; bloquear operaciones financieras mientras no exista pasarela.
2. Definir y aplicar capacidades del terminal.
3. Implementar actualización acotada de datos de contacto.
4. Sustituir el código público ambiguo por identidad global de terminal.
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
3. Administración de terminales y capacidades.
4. Cotización/confirmación de membresía, vencidas y alta inicial.
5. Catálogo y reserva de recursos y profesionales de bienestar.
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
- cada endpoint aplique capacidades del terminal;
- una URL identifique inequívocamente una instalación provisionada;
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
  domiciliación y Wellness;
- idempotencia estable en el navegador durante reintentos de una misma
  intención;
- Automatizaciones acepta nuevas reglas sólo para disparadores reales
  (`MANUAL`, `SCHEDULED`) y acción `CREATE_NOTIFICATION`; las opciones ficticias
  se retiraron de la interfaz.
- pruebas unitarias de política/servicio y E2E de navegador demuestran que no
  aparece pago manual, la tarjeta queda cerrada sin proveedor y el contacto se
  actualiza mediante el contrato permitido.
