# Accesos y control físico

Estado: `CONTROL PLANE CLOUD ESTABILIZADO PARA INICIAR CONNECTOR / CONNECTOR LOCAL Y ADAPTER HIKCENTRAL PENDIENTES`

Última revisión: 26 de agosto de 2026

## 1. Alcance y fuente de verdad

El módulo `access` decide y distribuye derechos de acceso físico por organización
y sucursal. GymFort es la fuente de verdad comercial:

```text
Client activo
  -> Membership vigente o en gracia
  -> MembershipPlan
  -> Benefit tipo ACCESS
  -> estado deseado por proveedor
  -> GymFort Local Connector
  -> sistema local de control físico
```

`EligibilityPolicy` sigue siendo la única interpretación de cliente, fecha
operativa, membresía, gracia, plan, multi-sucursal y beneficios. `access` no
duplica esas reglas. Tampoco administra usuarios internos, cobros ni biometría.

Una visita comercial normal se representa mediante una membresía de un día. La
infraestructura histórica `visit_pass` permanece por compatibilidad, pero ya no
forma parte del flujo principal de la consola.

El objetivo `ACC-005` agrega paquetes de N visitas sin salir del tenant: un solo
saldo por socio, vigencia, todas o una selección de sucursales admitidas y un
ledger atómico/idempotente que conserva la sucursal de cada consumo. No se
crearán saldos independientes por sede.

## 2. Estado real de la entrega

| Pieza | Estado | Alcance comprobado |
|---|---|---|
| Decisión lógica y bitácora | IMPLEMENTADO | Elegibilidad por membresía/beneficio, autorización temporal y apertura manual simulada |
| Modelo físico provider-neutral | IMPLEMENTADO | Conexión, puntos, recursos externos, niveles, personas, estado deseado y salud por sucursal |
| Control plane del Connector | ESTABILIZADO | Activación, secreto rotatorio, protección de fallos, heartbeat, jobs con lease, resultados, eventos y candidatos |
| Sincronización automática | IMPLEMENTADO | Eventos de membresía, estado del socio y configuración de beneficios; reconciliación manual de recuperación |
| Consola administrativa | ESTABILIZADA | Secuencia proveedor → puntos → derechos → recursos → personas → salud; alerta de puntos legacy con 0 o múltiples derechos |
| GymFort Local Connector ejecutable | PENDIENTE CONNECTOR | Debe instalarse dentro de la LAN del primer cliente |
| Adapter contra HikCentral Professional | PENDIENTE HIKCENTRAL REAL | Requiere descubrimiento de versión, licencia, API/SDK, topología y pruebas físicas |
| Apertura real del torniquete | PENDIENTE HARDWARE | `commands/open` conserva `SIMULATED_OPEN`; no se afirma operación física |

Configurar `HIKCENTRAL` en la consola significa que el contrato cloud está listo
para un Connector. No significa que GymFort esté llamando hoy a HikCentral.

## 3. Proveedores y puntos físicos

`PhysicalAccessProviderCatalog` evita condicionales de marca en el dominio:

- `HIKCENTRAL`: primer contrato cloud configurable; necesita Connector y
  validación contra la instalación real;
- `SIMULATED`: diagnóstico sin hardware y nunca productivo;
- `ZKTECO`, `SUPREMA`, `GENERIC_RELAY` y `CLOUD_PROVIDER`: visibles como
  planificados y no seleccionables.

El proveedor siempre se selecciona explícitamente al crear un punto o una
conexión. Un valor nulo o vacío produce
`PHYSICAL_ACCESS_PROVIDER_REQUIRED`; nunca se interpreta como `HIKCENTRAL`.
Los registros históricos que ya conservan `HIKCENTRAL` siguen siendo válidos.

La conexión V1 es única por organización/sucursal y su proveedor es inmutable.
Una migración de proveedor exige revocación y procedimiento asistido; no se
sobrescribe de forma silenciosa.

Cada punto productivo debe cumplir estas invariantes:

1. pertenece a la organización y sucursal activas;
2. tiene exactamente un beneficio `ACCESS` requerido, nunca cero ni varios;
3. se vincula explícitamente con una referencia de puerta, torniquete o recurso
   externo del mismo proveedor;
4. utiliza un código de dispositivo único y normalizado.

La relación conceptual es:

```text
MAIN_TURNSTILE -> GYM_ACCESS -> external access level ref
SAUNA_DOOR     -> SAUNA_ACCESS -> external access level ref
```

No existe el fallback productivo “sin requisito = cualquier membresía”. El modo
`SIMULATED` conserva flexibilidad sólo para diagnóstico local.

La consola calcula la preparación de todos los puntos y muestra `Correcto 1/1`,
`Configuración incompleta 0/1` o `Configuración inválida N/1`. Ante varios
beneficios legacy no preselecciona ninguno: Dueño o Gerente debe elegir cuál
conservar y el punto continúa no listo para producción hasta guardarlo.

## 4. Estado deseado y sincronización automática

GymFort persiste estado deseado por conexión y socio. No confunde activación de
membresía con entrega exitosa al hardware.

Estados expuestos:

- `PENDING`: existe un job durable por entregar;
- `SYNCED`: el Connector confirmó el job vigente;
- `FAILED`: el último intento falló; el job indica si todavía es reintentable;
- `BLOCKED`: falta un prerrequisito cloud.

`NOT_LINKED` no es un estado persistido: es la condición que presenta la vista
cuando todavía no existe vínculo de persona ni estado deseado para ese socio.

Bloqueos comunes:

- `EXTERNAL_PERSON_LINK_REQUIRED`;
- `ACCESS_LEVEL_MAPPING_REQUIRED`;
- `CONNECTION_DISABLED`;
- `CLIENT_NOT_FOUND`.

Acciones del contrato:

- `UPSERT`: crear o actualizar los niveles deseados;
- `REVOKE`: retirar todos los niveles de GymFort;
- `RECONCILE`: comparar y reparar el estado externo.

Los siguientes cambios recalculan automáticamente a las personas ya vinculadas:

- membresía activada;
- renovación;
- cambio de plan;
- vencimiento;
- cambio de estado del socio;
- alta/configuración de un beneficio `ACCESS`;
- cambio de mapeos físicos.

Vincular una persona también genera su estado inicial. Los botones “Forzar
reconciliación” existen para recuperación operativa; no son el flujo normal.

En el dominio vigente, la suspensión y reactivación operativas se expresan como
cambios de estado del socio; el cambio de plan cancela la membresía anterior y
publica `MembershipPlanChangedV1`. No existe todavía un comando independiente
para cancelar, suspender o reactivar una membresía. Si ese caso de uso se agrega,
deberá publicar un evento de cambio de derechos consumido por esta misma
reconciliación antes de exponerse en producción.

## 5. Entrega durable al Connector

`access_provider_sync_job` es la bandeja durable. Un job contiene únicamente:

- acción;
- referencia opaca de persona;
- referencias de niveles de acceso;
- versión deseada;
- clave de idempotencia;
- estado, intentos, lease, token efímero del claim y error sanitizado.

El Connector reclama lotes con `FOR UPDATE SKIP LOCKED`. El lease y un
`claimToken` nuevo por intento evitan que un worker anterior complete, falle o
extienda un job recuperado. Los jobs de un mismo socio se serializan; una versión
deseada nueva sustituye pendientes/reintentos anteriores y espera a que termine
o venza cualquier intento ya reclamado. Completar actualiza a `SYNCED` sólo si
todavía corresponde a la versión deseada. Un fallo reintentable vuelve a estar
disponible después del retraso indicado; uno terminal queda `FAILED`. Repetir el
mismo evento no crea jobs duplicados.

La cola reside en PostgreSQL y sobrevive a reinicios de GymFort Cloud o del
Connector. Una caída de Internet no cambia la membresía comercial y el sistema
local debe seguir decidiendo con su último estado distribuido.

## 6. API administrativa

Todas las rutas viven bajo `/api/v1/access` y usan JWT, organización y sucursal
del actor:

- `GET|PUT /physical-integration`;
- `GET /physical-integration/clients/{clientId}`;
- `PUT /physical-integration/clients/{clientId}/link`;
- `POST /physical-integration/clients/{clientId}/synchronize`;
- `POST /physical-integration/reconcile`;
- `PUT|DELETE /physical-integration/devices/{deviceId}/link`;
- `POST /physical-integration/connector/activation`;
- `POST /physical-integration/connector/revoke`;
- `GET /physical-integration/candidates`;
- `PUT /physical-integration/candidates/{candidateId}/match`;
- `POST /physical-integration/candidates/{candidateId}/reject`;
- `POST /devices` y `GET|PUT /devices/{deviceId}/benefits`.

Las mutaciones físicas y la revisión de candidatos exigen
`access.physical.manage`, asignado en V84 a Dueño y Gerente. Recepción no lo
recibe. Soporte de plataforma opera mediante su autoridad global y queda
auditado. La lectura operativa sigue usando `access.read`.

`POST /api/v1/access/commands/open` conserva su contrato excepcional:

- permiso separado `access.manual.open`;
- dispositivo explícito;
- motivo obligatorio;
- auditoría;
- resultado `SIMULATED_OPEN` mientras no exista adapter real.

## 7. API propia del GymFort Local Connector

El Connector no usa JWT de empleados. Sus rutas están separadas bajo
`/api/v1/physical-connectors`:

- `POST /activate`;
- `POST /heartbeat`;
- `POST /jobs/claim`;
- `POST /jobs/{jobId}/complete`;
- `POST /jobs/{jobId}/fail`;
- `POST /jobs/{jobId}/lease`;
- `POST /events`;
- `POST /person-candidates`.

Después de activarse, autentica cada llamada con:

```text
X-GymFort-Connector-Key
X-GymFort-Connector-Secret
```

Flujo de credenciales:

1. un Dueño o Gerente genera un código de activación válido por 15 minutos;
2. el código se muestra una vez y en base sólo se almacena su hash BCrypt;
3. `/activate` consume el código de forma atómica y devuelve un secreto una sola
   vez;
4. GymFort almacena únicamente el hash BCrypt del secreto;
5. una rotación revoca la credencial anterior; una revocación corta heartbeat,
   jobs y eventos inmediatamente;
6. autenticación inválida responde `401 CONNECTOR_AUTH_INVALID` y se audita sin
   registrar el secreto.

Los fallos repetidos de activación y autenticación se limitan por origen y por
la combinación origen/connector key. Las claves del limitador sólo contienen
huellas SHA-256; nunca incluyen el secret ni el código de activación. Producción
usa el limitador Redis ya compartido por las instancias; perfiles `local` y
`test` usan memoria y su alcance es deliberadamente por proceso. Una petición
limitada conserva la misma respuesta genérica para no revelar si la key existe.
Los límites y ventanas se configuran bajo
`gymos.access.physical-connector.*`.

`last_authenticated_at` representa actividad autenticada reciente, no la hora
exacta de cada request. La validación del secret ocurre siempre, pero PostgreSQL
sólo actualiza ese timestamp de forma atómica si es nulo o más antiguo que
`last-authenticated-write-interval` (cinco minutos por defecto); una petición
dentro de la ventana no incrementa `version`.

Las credenciales del proveedor, URL/puerto de LAN y cualquier material
biométrico deben residir sólo en el almacén seguro del Connector local.

## 8. Eventos físicos y conciliación de personas

El Connector puede publicar eventos reales una vez que el adapter exista. Cloud
acepta únicamente eventos cuyo proveedor y recurso externo coinciden con la
conexión autenticada. `externalEventId` es único por conexión, por lo que un
reintento devuelve el registro existente sin duplicar `access_log`.

Los metadatos están limitados en cantidad y tamaño. Se rechazan nombres de campo
sensibles como rostro, imagen, huella, plantilla, contraseña, secreto, token o
credencial.

Para importar personas, el Connector envía referencia opaca y, opcionalmente,
nombre/contacto. GymFort sólo sugiere una coincidencia cuando correo o teléfono
normalizados producen un único resultado exacto visible en esa sucursal. Las
coincidencias ambiguas quedan `CONFLICT`; nunca se enlaza automáticamente por
similitud de nombre. Dueño o Gerente confirma o descarta desde la consola.

GymFort Cloud no recibe fotografías de enrolamiento, plantillas faciales,
huellas ni resultados de matching biométrico.

## 9. Salud y operación

La salud derivada de una conexión puede ser:

- `DISABLED`;
- `AWAITING_CONNECTOR`;
- `HEALTHY`;
- `DEGRADED`;
- `OFFLINE` después de cinco minutos sin heartbeat.

La consola muestra último heartbeat, versión del Connector, última entrega,
jobs pendientes/fallidos y último error. Para un incidente:

1. verificar que la membresía y el estado deseado sean correctos;
2. revisar heartbeat y credencial activa;
3. revisar jobs fallidos sin copiar secretos a tickets o logs;
4. recuperar/reiniciar el Connector dentro de la LAN;
5. forzar reconciliación completa;
6. comprobar que pendientes bajen y que el proveedor conserve los niveles
   esperados;
7. usar apertura manual sólo con autorización y motivo mientras el adapter real
   siga pendiente.

Contratos y procedimientos detallados:

- [GymFort Local Connector](../../docs/operations/physical-access-connector.md);
- [diagnóstico de acceso físico](../../docs/operations/access-device-troubleshooting.md).

## 10. Secuencia de alta para el primer cliente

La consola guía este orden:

1. seleccionar `HIKCENTRAL` y guardar una conexión de la sucursal;
2. registrar cada punto físico con exactamente un beneficio `ACCESS`;
3. vincular cada punto con la referencia real de puerta/torniquete;
4. mapear cada beneficio a un Access Level existente;
5. generar el código y activar el GymFort Local Connector;
6. importar/revisar personas y confirmar vínculos;
7. observar heartbeat, jobs y reconciliación.

Antes de ejecutar los pasos 3–7 contra hardware debe completarse descubrimiento
en sitio:

- nombre y versión exacta de HikCentral Professional;
- modelo y firmware del terminal/controlador Hikvision;
- licencia y disponibilidad de OpenAPI/Open Platform o SDK oficial;
- autenticación, endpoint y puerto permitidos dentro de la LAN;
- estructura actual de Persons, Doors y Access Levels;
- operaciones oficiales disponibles para personas, niveles, apertura y eventos;
- política de disponibilidad cuando Internet o Cloud no responden.

No se codifica ningún endpoint, puerto ni payload de HikCentral hasta validar esa
instalación concreta.

## 11. Datos y migraciones

V83 creó la base provider-neutral. V84 agrega y endurece:

- identidades y claves foráneas compuestas de tenant/sucursal;
- una conexión por sucursal y `connector_key` global único sin distinguir
  mayúsculas;
- vínculo `access_device -> external_device_ref`;
- activaciones y credenciales hasheadas;
- versión del estado deseado;
- jobs durables, índices de claim, lease y reintentos;
- conversión de estados V83 pendientes/fallidos en jobs V84 cuando conservan
  conexión activa y vínculo de persona; los demás quedan `BLOCKED` explícitos;
- candidatos de personas;
- referencias externas e idempotencia en `access_log`;
- `access.physical.manage` y reparación de `access.manual.open` para Dueño y
  Gerente.

No se almacenan contraseñas del proveedor ni secretos del Connector en texto
plano.

## 12. Evidencia automatizada

La cobertura dedicada demuestra, entre otros casos:

- catálogo de proveedores y rechazo de proveedores planificados;
- proveedor obligatorio sin fallback implícito a `HIKCENTRAL`;
- punto productivo con cero o múltiples beneficios rechazado;
- estados de preparación de puntos productivos y ausencia de selección
  automática ante múltiples beneficios legacy;
- aislamiento por organización, sucursal, dispositivo, recurso y candidato;
- UPSERT, REVOKE y BLOCKED según elegibilidad/mapeo;
- triggers de activación, renovación, cambio, vencimiento, estado de socio y
  beneficios;
- código de activación de un uso y secretos hasheados/revocables;
- limitación configurable de fallos sin enumeración de key y escritura
  condicional de `last_authenticated_at`;
- claim exclusivo concurrente, rotación del token al recuperar un lease,
  serialización de versiones deseadas y reintentos;
- idempotencia de jobs y eventos físicos;
- upgrade V83 → V84 y migración limpia en PostgreSQL;
- permisos separados para configuración física y apertura manual.

## 13. Pendiente antes de producción física

La entrega deja GymFort Cloud listo para iniciar la integración, no listo para
abrir el torniquete. Faltan obligatoriamente:

1. descubrimiento técnico de la instalación del cliente;
2. implementar, empaquetar y actualizar de forma segura GymFort Local Connector;
3. implementar el adapter HikCentral contra API/SDK oficial confirmado;
4. comprobar alta/actualización, concesión, revocación y reconciliación reales;
5. comprobar eventos reales, reloj, duplicados y mapeo de dispositivos;
6. probar caída/recuperación de Internet, Cloud, Connector y HikCentral;
7. definir instalación como servicio, logs sanitizados, métricas, alertas,
   backup de configuración y rotación de secretos;
8. ejecutar pruebas de aceptación con el torniquete y obtener aprobación del
   cliente;
9. revisar privacidad y contratos aplicables al tratamiento biométrico que
   seguirá realizando Hikvision/HikCentral;
10. reemplazar `SIMULATED_OPEN` sólo cuando el puerto productivo tenga resultado
    confirmado y runbook probado.

## 14. Paquetes de visitas multi-sucursal

V98–V100 implementan un entitlement con saldo compartido y un ledger idempotente
por evento. La decisión vigente evalúa primero membresía y sólo después paquete; una
concesión por membresía nunca resta visitas. El consumo requiere estado
`ACTIVE`, vigencia, saldo y una combinación exacta sucursal–beneficio compatible
y cambia a
`CONSUMED` al llegar a cero en la misma transacción.

`visit_package_entitlement_benefit_branch` evita que dos snapshots separados se
recombinen: paquete A+B y Sauna `LOCAL` de A no autoriza Sauna en B. El saldo
sigue siendo único y puede gastarse en B mediante otro acceso válido allí.

La API manual histórica `visit_pass` queda para lectura mientras se regularizan
datos. Sus operaciones de alta y consumo rechazan nuevas escrituras con
`VISIT_PASS_LEGACY_READ_ONLY`.
