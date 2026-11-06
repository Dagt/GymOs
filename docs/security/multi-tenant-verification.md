# Plan de verificación multi-tenant de GymFort

Estado: VIGENTE / EJECUCIÓN PARCIAL
Última revisión documental: 2026-11-05

## Auditoría del issue #29

Base revisada `58a5d4d4b24bcc598059b05a4ac70d11a2fe937b`, que incorpora el PR #65. El [inventario de endpoints](endpoint-permissions.md) registra 298 rutas de Spring y sus autoridades. Las pruebas HTTP usan JWT firmados por el backend, el filtro real, servicios y JDBC con PostgreSQL 18 migrado por Flyway. La emisión de sesión usa Redis 8 efímero. Las pruebas Playwright de Equipo prueban el contrato visual con API interceptada.

| Superficie | Límite aplicado | Evidencia y hallazgos |
| --- | --- | --- |
| Autenticación, sesiones y soporte | El JWT fija el tenant y la sede. El filtro revalida versión de credenciales, cuenta, tenant activo y sede activa y asignada. Soporte opera con tenant explícito y temporal. | HTTP/PostgreSQL para cambio de sede, intento contra otro tenant, revocación de asignación, suspensión y soporte. El ciclo de vida de refresh, cierre y revocación global sigue en [#28](https://github.com/Dagt/GymOs/issues/28). |
| Plataforma, gimnasios y sucursales | Consola SaaS con permiso de plataforma. La consulta por tenant explícito admite administrador o soporte del tenant con `branch.read`. El directorio de sucursales del mismo tenant sigue disponible para destinos de inventario y flujos de catálogo. | HTTP/PostgreSQL para límites de soporte. Inspección del alta, suspensión, cobro comercial y marca pública. |
| Equipo, roles y permisos | Permisos HTTP efectivos. Gerencia ve personal de la sede activa y administra una cuenta sólo si su alcance cubre todas las sedes de destino. Dueño conserva ámbito de organización y protección de otros dueños. Soporte queda acotado al tenant y la recuperación interna exige identidad tenant. | PATCH y GET sobre cuentas reales, permiso parcial, alcance multisede y auditoría. Playwright para ocultación de acciones. |
| Socios, membresías y paquetes | Para operaciones de personal por UUID, expediente de la sede o socio visible por cobertura de membresía vigente, incluida gracia según su vigencia. La cobertura compartida de los paquetes en check-in sigue en Eligibility y el ledger, y Kiosco usa el socio verificado. | GET/POST con UUID de otra sede y tenant. Caso positivo con membresía multisede y denegación después de su vigencia. Las fichas históricas sin sede conservan visibilidad de organización. |
| Beneficios | Definiciones locales y asignaciones operan sobre la sede activa. El catálogo de accesos mantiene definiciones de organización con cobertura de plan. | POST con `branchId` de otra sede y tenant. GET de asignaciones con visibilidad del socio. Playwright verifica que un beneficio compartido se pueda consultar sin mostrar la acción de editar desde otra sede. |
| Catálogo e inventario | Definiciones de catálogo compartidas con disponibilidades por sede. Stock local. Transferencias sólo entre sedes de la misma organización. Origen envía y destino recibe. | Transferencia HTTP/PostgreSQL denegada sin escritura, flujo positivo multisede e inspección de permisos separados de catálogo y archivos. |
| Ventas, pagos y devoluciones | Venta, pago y archivo se vinculan al tenant y sede de la operación. La carga de comprobante genera la clave en servidor. | HTTP/PostgreSQL reprodujo la lectura de detalle POS de otra sede antes de agregar el filtro. También cubre pago ajeno e inyección de `receiptObjectKey`. Conciliación, devoluciones y consistencia financiera de extremo a extremo siguen en [#33](https://github.com/Dagt/GymOs/issues/33). |
| Accesos y check-in manual | Sólo tenant en sede activa para entradas y reversión, con permiso distinto, cliente visible y pertenencia en SQL. Conector usa su propio alcance; los paquetes conservan idempotencia y locks. | HTTP/PostgreSQL para `branchId` manipulado, socio de otro tenant y ausencia de historial. Las suites Access y de paquetes cubren reversiones y concurrencia. |
| Kiosco y configuraciones operativas | Las operaciones de socio se vinculan al UUID y token de la sesión y su contexto persistido. La configuración del kiosco admite las sucursales asignadas o las del tenant de soporte. | HTTP/PostgreSQL para configurar una sede no asignada y otra asignada. Inspección de capacidades, identidad, expiración y parámetros de recursos de Kiosco. |
| Recursos, reservas y Bienestar | Recursos y citas de la sede. Alta de reserva/cita con socio visible en backoffice. Una cita no puede enlazar un socio de otro tenant en persistencia. | GET, PUT y DELETE contra recurso local/ajeno. POST y lectura de citas con JDBC real. Se conservan transacciones de reservas y flujos de autoservicio. |
| Coaches, ejercicios y rutinas | Consultas de sede y propiedad de la rutina según `own/all`. El perfil se puede editar en sedes asignadas al operador. | Pruebas de módulo e inspección. La actualización de biografía y especialidades filtra las sucursales administradas en SQL. |
| Automatizaciones y notificaciones | Reglas, ejecuciones, destinos, colas y configuración se resuelven con el tenant del principal y el ámbito de cada operación. La operación manual por UUID exige destinatario visible y el reintento limita el mensaje a la sede o al ámbito organizacional. | HTTP/PostgreSQL para destinatarios y reintentos de otra sede o tenant. Otras rutas administrativas cuentan con inspección y suites de módulo. |
| Reportes | Reporte del dueño y ventas se calculan en la sucursal activa. Las altas se atribuyen a la sede de alta. Historial de proveedores y otros agregados definidos como organizacionales exigen su permiso específico. | HTTP/PostgreSQL con ventas y socios simultáneos en dos sucursales y otro tenant. |
| Stripe y Mercado Pago | Configuración del proveedor por tenant, modalidades por sede, callback OAuth y webhooks autenticados por protocolo con conciliación de cuenta y operación. | Inspección y suites de pagos. Falta prueba conectada de los escenarios cruzados con sandbox del proveedor. |
| Almacenamiento | Objetos de marca pública servidos por el identificador de organización. Imágenes de producto y comprobantes se escriben con prefijos del recurso. El proxy Next de marca codifica el UUID y sólo consulta la ruta pública de marca. | Inyección de referencia de comprobante por HTTP cubierta. Falta verificación de la política real del bucket MinIO y revisión de referencias históricas anteriores a esta corrección. |

| Perfil | Regla efectiva por operación |
| --- | --- |
| Administración de plataforma | `platform.admin` para cartera, configuración comercial y entrada temporal a soporte. Fuera de soporte carece de contexto tenant. |
| Soporte temporal | Autoridades operativas derivadas del contexto de soporte, tenant seleccionado y expiración. Sin permisos de plataforma, check-in manual, lanzamiento de kiosco ni recuperación interna. |
| Dueño | Acciones según las authorities asignadas. Administración de equipo y configuración del tenant con límites para cuentas dueñas. Operación local desde la sucursal seleccionada. |
| Gerencia | Permisos de lectura y escritura independientes. Cuenta multisede administrable sólo con alcance sobre todas sus sedes y pertenencia a la activa. |
| Recepción | Permisos de operación y check-in separados de reversión, apertura física, proveedores y administración. Kiosco requiere el permiso y rol operativo de recepción. |
| Coach | Operaciones propias con `*.own`, ampliación únicamente con los permisos explícitos `*.all`. |
| Permiso parcial o rol personalizado | El matcher usa authorities. La jerarquía de equipo, el kiosco, propiedad de rutinas y los ámbitos de plataforma/sede agregan condiciones de dominio. |

### CI externo

Workers Builds de `gymfort-web` ya fallaba en ramas secundarias antes de #29, incluida una rama documental. El build interno OpenNext es una comprobación independiente. El estado de CI de #29 se registra por SHA en su PR. Si Workers Builds sigue fallando, se requiere verificación del proveedor o una excepción explícita del propietario para este PR. [Check histórico del PR #60](https://github.com/Dagt/GymOs/runs/123714043406).

La deuda y criterios pendientes derivados de este plan se registran en
`../../Documentación/28-registro-vivo-deuda-tecnica.md` (`GEN-001` y deudas por
módulo).

GymFort debe aislar cada operación por `organization_id` y, cuando el flujo sea físico o localizable, por `branch_id`. Este documento define la estrategia mínima de pruebas y revisión para declarar el aislamiento apto para piloto.

## Principios

1. Todo recurso operativo pertenece a una organización.
2. Todo recurso de operación física pertenece además a una sucursal.
3. El frontend puede ocultar opciones, pero la seguridad real vive en backend.
4. Las respuestas ante UUID ajenos no deben permitir enumerar recursos.
5. Archivos en MinIO deben tener prefijo por organización y recurso propietario.
6. Redis debe usar claves con contexto suficiente: organización, sucursal, terminal o sesión según aplique.

## Recursos críticos

| Recurso | Organización requerida | Sucursal requerida | Riesgo principal |
| --- | --- | --- | --- |
| Usuario interno | Sí | Sucursales permitidas | acceso cruzado por rol global |
| Cliente/persona | Sí | No siempre | lectura o edición cruzada |
| Venta | Sí | Sí | pago/reembolso de venta ajena |
| Pago | Sí | Sí | validación o recibo ajeno |
| Conexión/webhook/mandato Stripe | Sí | Según operación | cuenta, evento o cargo de otro tenant |
| Membresía | Sí | Sí | activación/renovación cruzada |
| Kiosco | Sí | Sí | terminal pública de otro gimnasio |
| Acceso | Sí | Sí | apertura/registro en sucursal equivocada |
| Visit pass | Sí | Sí | consumo de saldo ajeno |
| Inventario | Sí | Sí | stock compartido por error |
| Reserva | Sí | Sí | doble reserva o pago cruzado |
| Coach/rutina | Sí | Sí | asignación a cliente ajeno |
| Wellness | Sí | Sí | agenda cruzada |
| Automatización | Sí | No siempre | ejecución sobre tenant incorrecto |
| Notificación | Sí | No siempre | envío a destinatario ajeno |
| Reporte | Sí | Opcional | agregados con datos de otro tenant |
| Archivo/recibo | Sí | Sí | descarga por object key conocido |

## Casos negativos obligatorios

Cada caso debe crearse con dos organizaciones (`org_a`, `org_b`) y al menos una sucursal por organización.

| Código | Caso | Resultado esperado |
| --- | --- | --- |
| MT-AUTH-001 | Usuario de `org_a` intenta seleccionar sucursal de `org_b` | 403 o error semántico sin filtrar datos |
| MT-CLIENT-001 | Leer cliente de `org_b` con token de `org_a` | 404/403 |
| MT-CLIENT-002 | Editar cliente de `org_b` con token de `org_a` | 404/403 |
| MT-SALE-001 | Consultar venta de `org_b` | 404/403 |
| MT-SALE-002 | Agregar pago a venta de `org_b` | 404/403 |
| MT-SALE-003 | Validar pago de `org_b` | 404/403 |
| MT-SALE-004 | Solicitar reembolso de venta de `org_b` | 404/403 |
| MT-MEM-001 | Renovar membresía de `org_b` | 404/403 |
| MT-KIOSK-001 | Usar terminal de `org_b` desde contexto de `org_a` | respuesta genérica |
| MT-ACCESS-001 | Consumir visita de `org_b` | 404/403 |
| MT-INVENTORY-001 | Crear movimiento sobre stock de `org_b` | 404/403 |
| MT-BOOKING-001 | Vincular pago de `org_a` a reserva de `org_b` | 404/403 |
| MT-PAY-001 | Consultar operación, mandato o webhook Stripe de `org_b` | 404/403 |
| MT-PAY-002 | Webhook firmado de `org_b` intenta completar venta de `org_a` | evento rechazado/sin efecto |
| MT-MULTI-001 | Plan multi-sucursal de `org_a` se usa en sucursal de `org_b` | rechazo genérico; nunca cruza tenant |
| MT-MULTI-002 | Plan multi-sucursal se usa en otra sucursal activa de la misma organización | permitido, conservando origen de membresía y destino de reserva |
| MT-AUTO-001 | Ejecutar automatización de `org_b` | 404/403 |
| MT-REPORT-001 | Reporte de `org_a` incluye datos de `org_b` | test falla si aparece agregado cruzado |
| MT-FILE-001 | Descargar o asociar recibo con key de `org_b` | 404/403 |

## Revisión de MinIO

Estado observado:

- Los recibos se guardan con prefijo `receipts/{organizationId}/{saleId}/{paymentId}/...`.

Pendiente:

- Revisar referencias históricas ya persistidas y comprobar permisos del bucket real. La API rechaza claves aportadas al crear pagos nuevos.
- Validación de tamaño máximo y MIME.
- Normalización de nombre de archivo ya existe parcialmente; revisar extensiones permitidas.

## Revisión de Redis

Pendiente:

- Auditar claves de rate limiting.
- Confirmar contexto de organización/terminal/sesión en kiosco.
- Confirmar locks de automatizaciones con organización o regla.

## Estrategia de pruebas

1. Crear fixtures Testcontainers PostgreSQL con dos organizaciones.
2. Crear usuarios con roles equivalentes en cada organización.
3. Generar tokens reales o usar MockMvc con principal equivalente.
4. Ejecutar casos negativos por endpoint.
5. Verificar que no se escriben efectos secundarios en `audit_log`, `payment`, `membership`, `inventory_movement`, `booking_payment` para recursos ajenos.
6. Agregar los casos a CI.

## Criterio para `PILOT_READY`

El sistema requiere al menos:

- Casos negativos de `auth`, `clients`, `sales/payments`, `memberships`, `selfservice`, `access`, `inventory`, `bookings`, `reporting`.
- Evidencia de que object keys de MinIO no pueden reutilizarse entre tenants.
- Evidencia de que Redis no colisiona entre tenants para rate limits, sesiones y locks.
