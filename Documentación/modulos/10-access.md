# Accesos y control físico

Estado: `FASE 1 INICIADA / CONECTOR LOCAL PENDIENTE`
Última revisión: 23 de agosto de 2026

## 1. Propósito del módulo

El módulo `access` es responsable del **control operativo y físico de acceso** a una
sucursal y a sus zonas restringidas. Su pregunta principal es:

> ¿Esta persona puede utilizar este punto de acceso, en esta sucursal, en este momento?

No es responsable de autenticación de usuarios administrativos, RBAC ni permisos
para usar pantallas de GymFort. Tampoco debe convertirse en un segundo módulo de
ventas. La identidad administrativa pertenece a `identity/security`; la venta y
el cobro pertenecen a `sales/payments`; la vigencia comercial pertenece a
`memberships`; y la interpretación común de elegibilidad pertenece a
`eligibility`.

La frontera deseada es:

```text
Client
  -> Membership vigente
  -> MembershipPlan
  -> Benefit tipo ACCESS heredado
  -> EligibilityPolicy
  -> Access
  -> punto físico / proveedor de control de acceso
```

## 2. Decisión de producto vigente

### 2.1 Los derechos físicos se expresan con beneficios `ACCESS`

Los beneficios `ACCESS` son la abstracción canónica para representar derechos
físicos. Un plan no concede acceso por su nombre ni por ser "Premium"; concede
los beneficios `ACCESS` vinculados mediante `benefit_membership_plan`.

Ejemplos:

```text
Plan Mensual Básico
  -> GYM_ACCESS

Plan Mensual Premium
  -> GYM_ACCESS
  -> SAUNA_ACCESS

Plan Visita Diaria
  -> GYM_ACCESS

Plan Visita Diaria Premium
  -> GYM_ACCESS
  -> SAUNA_ACCESS
```

El socio no recibe copias en `client_benefit`. El derecho se resuelve de forma
dinámica a partir de la membresía operativa y su plan actual. Cambiar de plan,
vencer, suspender o revocar la membresía debe cambiar inmediatamente la
eligibilidad calculada.

### 2.2 Una visita diaria se modela como una membresía de un día

Para el alcance real previsto de GymFort no se desarrollará un segundo sistema
comercial de paquetes de N visitas. La visita normal al gimnasio se modelará
como un `membership_plan` con duración de un día.

Ejemplos:

- `Visita diaria`: un día, `GYM_ACCESS`.
- `Visita diaria Premium`: un día, `GYM_ACCESS` + `SAUNA_ACCESS`.

El flujo de un visitante nuevo será:

```text
Recepción
  -> buscar cliente
  -> si no existe, alta rápida de Client
  -> contratar plan Visita diaria / Visita diaria Premium
  -> cobrar mediante el flujo normal de membresía
  -> pago confirmado
  -> Membership activa durante la fecha operativa
  -> beneficios ACCESS heredados
  -> acceso físico
```

No se crea una entidad especial `Visitor`: una persona puede existir como
`Client` sin una membresía permanente y contratar posteriormente cualquier plan.

La visita de un día usa los extremos inclusivos ya definidos por Membresías. Un
plan de duración 1 genera `startsOn == endsOn` para la fecha operativa de la
contratación; no significa 24 horas móviles desde la hora del pago.

### 2.3 Los paquetes de visitas dejan de ser flujo objetivo

El código actual contiene `visit_pass`, creación manual, consulta y consumo de
paquetes. Esta funcionalidad fue útil para el piloto inicial, pero **no forma
parte del flujo comercial objetivo** después de esta decisión.

Por tanto:

- no se ampliará `VisitPass` con integración a Catálogo ni nuevos beneficios;
- no se creará una relación general producto -> beneficio de acceso;
- la sección "Paquetes de visitas" ya fue retirada de la pantalla principal de
  Access; los endpoints y tablas se mantienen temporalmente por compatibilidad;
- los endpoints/tablas existentes pueden mantenerse temporalmente por
  compatibilidad hasta comprobar que ninguna operación real depende de ellos;
- su eliminación física debe hacerse en una migración posterior y explícita, no
  dentro de un cambio no relacionado.

`UC-002 Registrar visita` y `UC-023 Paquetes de visitas` representan el enfoque
histórico previo a esta decisión. Para la evolución del módulo, este documento
es la fuente vigente: una visita comercial normal se resuelve mediante un plan
diario de membresía.

## 3. Qué está implementado y sí se conserva

### 3.1 Dispositivos de acceso

`access_device` ya modela puntos físicos por organización y sucursal con datos
útiles para la evolución posterior:

- `device_code`;
- `device_type`;
- `status`;
- `provider`;
- `adapter_config_json`;
- `last_seen_at`.

Este modelo se conserva. Un dispositivo representa una puerta, torniquete,
controlador o filtro lógico de acceso, no necesariamente un lector específico.

### 3.2 Relación dispositivo -> beneficio requerido

`access_device_benefit` permite declarar qué beneficios `ACCESS` exige un punto.
Esta es la base correcta para zonas como entrada general, sauna o áreas Premium.

El modelo objetivo es explícito:

```text
MAIN_DOOR
  -> GYM_ACCESS

SAUNA_DOOR
  -> SAUNA_ACCESS
```

El comportamiento actual por el cual una puerta sin beneficios configurados
acepta cualquier membresía vigente se considera **compatibilidad provisional**.
Para una operación física real la entrada principal también debe tener un
beneficio explícito, normalmente `GYM_ACCESS`. De esta manera una membresía que
no conceda entrada física no obtiene acceso por accidente.

### 3.3 `EligibilityPolicy`

`com.gymos.eligibility` se conserva como fuente única de verdad para:

- cliente existente y activo;
- sucursal efectiva;
- fecha operativa;
- membresía vigente y gracia;
- multi-sucursal;
- plan vigente;
- herencia dinámica `plan -> ACCESS`;
- comparación contra beneficios requeridos por el dispositivo.

Access no debe duplicar estas consultas ni interpretar por su cuenta los planes.

### 3.4 Evaluación y bitácora

Se conserva el flujo `requestAccess()` y `access_log` como registro auditable de
intentos y decisiones. La bitácora debe seguir permitiendo distinguir:

- cliente;
- dispositivo;
- decisión `GRANTED` / `DENIED`;
- fuente/tipo de acceso;
- motivo normalizado;
- fecha/hora;
- correlación;
- estado del comando físico cuando exista integración real.

La bitácora deberá aceptar en el futuro tanto decisiones originadas por GymFort
como eventos importados desde un proveedor local, evitando duplicados mediante
identificadores externos/idempotencia.

### 3.5 Apertura manual

La apertura manual se conserva como **override excepcional y auditado**, no como
flujo normal de una visita pagada.

Casos esperados:

- técnico o proveedor;
- emergencia;
- incidencia con credencial;
- discrepancia administrativa;
- autorización expresa de un responsable.

Debe existir una autoridad dedicada `access.manual.open`; el matcher genérico
`access.manage` no debe ser suficiente para este comando sensible.

### 3.6 Autorización temporal

La infraestructura `access_temporary_pass` se conserva, pero su nombre de UX
debe evolucionar de "Pase temporal" a **"Autorización temporal"** para no
confundirla con una visita comercial.

Representa una excepción administrativa limitada por tiempo y/o usos. No es un
producto vendible y no sustituye los beneficios especiales de zonas
restringidas. Si un plan no concede `SAUNA_ACCESS`, una autorización temporal
genérica no debe convertirse en acceso al sauna; para una excepción puntual se
usa apertura manual auditada o una futura autorización explícita por derecho.

## 4. Qué existe hoy pero no está listo para producción física

### 4.1 La apertura es simulada

Actualmente una decisión concedida registra `command_status = SIMULATED_OPEN`.
No existe un adaptador productivo que abra el torniquete o una puerta real. Esto
es correcto para el estado actual, pero debe sustituirse por un puerto de
integración antes del piloto físico.

### 4.2 Identificación provisional

`requestAccess()` puede resolver cliente por `clientId` o por identificador de
recepción basado en datos del cliente. No existe todavía un modelo estable de
credencial física propio de GymFort.

Para el primer piloto Hikvision **no es obligatorio crear inmediatamente un
`AccessCredential` universal**: se puede conservar la biometría y la identidad
física dentro de HikCentral y relacionar el `Client` de GymFort con el
identificador opaco de persona del proveedor. Si más adelante se integran QR,
RFID/NFC u otros fabricantes, se evaluará una abstracción de credenciales común.

### 4.3 Dispositivo por defecto

Si no llega `deviceCode`, el repositorio puede seleccionar un dispositivo activo
por defecto. Esto sólo es aceptable para recepción/piloto con una puerta. En una
sucursal con múltiples zonas, toda evaluación física debe identificar de forma
inequívoca el dispositivo/punto de acceso.

### 4.4 Consumo de autorización temporal

El consumo de `TemporaryPass` se realiza mediante actualización condicional y
atómica por organización, sucursal, vigencia, estado y usos disponibles. Un
segundo consumo concurrente se registra como denegado y no abre el acceso.

### 4.5 Mensaje de periodo de gracia

La política permite acceso durante `graceEndsOn`, pero la respuesta actual puede
mostrar únicamente `membership.endsOn`. El motivo visible debe diferenciar
membresía vigente normal y periodo de gracia.

### 4.6 Seguridad y aislamiento

Faltan pruebas negativas completas de tenant/sucursal/dispositivo, respuestas
mínimas para no filtrar información sensible y pruebas específicas del permiso
de apertura manual.

## 5. Pantalla `/access`: objetivo de UX

La pantalla debe convertirse en una **consola de control de acceso**, no en una
segunda caja.

Debe conservar/mostrar:

- cliente seleccionado;
- dispositivo/punto de acceso;
- evaluación de acceso y motivo;
- beneficios `ACCESS` efectivos del cliente;
- apertura manual excepcional;
- autorización temporal;
- bitácora reciente;
- en el futuro, salud/sincronización del proveedor físico.

### 5.1 Consola por intención

La pantalla no debe mezclar todas las tareas en una columna interminable. La
consola se divide en tres vistas dentro de la misma sucursal:

1. **Operar acceso**: elegir el punto físico, encontrar al socio, revisar los
   derechos requeridos y registrar la decisión. La apertura manual y la
   autorización temporal quedan agrupadas como excepciones controladas, no como
   acciones principales.
2. **Configuración física**: registrar el identificador del conector local,
   registrar cada puerta/torniquete, asignarle sus beneficios `ACCESS` requeridos,
   mapear esos beneficios a Access Levels del proveedor y preparar la
   sincronización de un socio seleccionado.
3. **Bitácora**: consultar los eventos recientes sin alargar toda la página.
   El API entrega un conjunto acotado de resultados y la tabla usa un panel con
   desplazamiento propio y encabezado fijo. Para investigación histórica se
   incorporarán filtros y paginación, no una lista sin límite en la consola.

El punto físico seleccionado debe mostrar siempre sus requisitos efectivos. Si
no tiene ningún beneficio `ACCESS` asignado, la pantalla debe advertirlo
claramente antes de una operación real; la configuración se guarda mediante
`POST /api/v1/access/devices` y
`GET`/`PUT /api/v1/access/devices/{deviceId}/benefits`; permanece aislada por
organización y sucursal. Registrar un punto no abre hardware: el estado de
apertura física sólo cambia cuando exista un conector local sano.

La primera base de esa integración ya está disponible en la misma consola:

- configuración por sucursal de una conexión `HIKCENTRAL`;
- identificador público del GymFort Local Connector, sin URL, contraseña ni
  secreto del proveedor;
- mapeo `Benefit ACCESS -> Access Level` externo;
- vínculo `Client -> external_person_ref` opaco;
- estado deseado de sincronización `PENDING`, `BLOCKED`, `SYNCED` o `FAILED`.

La configuración no llama todavía a HikCentral ni manipula biometría. Su único
efecto es persistir el estado deseado que consumirá el conector local.

Debe retirarse del flujo principal:

- creación manual de paquetes de visitas;
- consumo comercial manual de paquetes.

La contratación de una visita diaria se hará desde el mismo flujo de
membresías/recepción que contrata cualquier otro plan, con alta rápida de cliente
cuando la persona todavía no exista.

## 6. Estrategia de control físico

### 6.1 Principio de arquitectura

GymFort debe ser la **fuente de verdad comercial y de derechos**:

```text
Client -> Membership -> Plan -> ACCESS Benefits
```

El proveedor físico debe ser el **ejecutor local** de esos derechos. No se debe
acoplar `AccessService` a marcas concretas.

Arquitectura objetivo:

```text
GymFort Cloud
  -> Access integration port
  -> conector/adaptador de proveedor
  -> sistema local de control de acceso
  -> terminal/torniquete/puerta
```

Las integraciones posteriores deben reutilizar el mismo puerto en lugar de
introducir `if provider == ...` dentro de la lógica de elegibilidad.

### 6.2 Primer proveedor objetivo: HikCentral Professional

El primer gimnasio piloto ya utiliza un torniquete y terminal biométrico
Hikvision administrados por **HikCentral Professional** dentro de la red local.
Actualmente recepción mantiene por separado:

1. el sistema de membresías;
2. las personas/fotografías/reconocimiento facial y permisos en HikCentral.

El objetivo del piloto es eliminar esa doble administración. GymFort controlará
membresías y derechos; HikCentral seguirá administrando el reconocimiento local,
la comunicación con el terminal y la apertura física.

No se integrará inicialmente GymFort directamente contra el torniquete si
HikCentral ya es el sistema que lo administra.

### 6.3 Modo recomendado para HikCentral: sincronización, no decisión cloud por paso

El primer piloto debe usar **sincronización de autorizaciones**:

```text
GymFort calcula derechos
  -> sincroniza persona / Access Levels necesarios en HikCentral
  -> HikCentral distribuye y decide localmente
  -> terminal reconoce rostro/tarjeta
  -> puerta abre o rechaza sin depender de Internet en cada paso
```

Esto evita que una caída de Internet impida entrar al gimnasio y aprovecha el
controlador local que ya existe.

La relación conceptual será:

```text
GymFort GYM_ACCESS   <-> HikCentral Access Level: acceso general
GymFort SAUNA_ACCESS <-> HikCentral Access Level: sauna
```

El nombre exacto y los identificadores se configurarán por sucursal/proveedor;
no se codificarán en el dominio.

### 6.4 Conector local

Como HikCentral vive en la LAN y GymFort en la nube, se prevé un **GymFort Local
Connector** ligero, preferentemente instalado en la misma PC o red donde opera
HikCentral.

El conector debe:

- iniciar comunicaciones salientes seguras hacia GymFort cuando sea posible;
- evitar exponer directamente HikCentral o puertos del torniquete a Internet;
- ejecutar llamadas al API/SDK local del proveedor;
- aplicar comandos/sincronizaciones idempotentes;
- reportar resultado y estado de salud;
- en fases posteriores, reenviar eventos de acceso a GymFort.

No debe contener reglas de membresía ni decidir si un cliente merece acceso; esa
lógica permanece en GymFort.

#### Base de datos y puerto ya preparados

La migración `V83__physical_access_integration_foundation.sql` crea los
registros de conexión, mapeos de beneficios, vínculos opacos de persona y estado
de sincronización. El puerto de aplicación
`PhysicalAccessSynchronizationPort` define el límite que implementará el
conector/adaptador. No existe todavía una implementación remota o embebida en
GymFort Cloud: esa separación evita acoplar la elegibilidad comercial a una
marca, LAN, token o puerto de Hikvision.

### 6.5 Biometría

GymFort no debe convertirse en motor de reconocimiento facial. Para el primer
piloto:

- HikCentral/Hikvision conserva y procesa la biometría;
- GymFort debe guardar como máximo referencias opacas necesarias para relacionar
  un `Client` con la persona del proveedor;
- no se copiarán plantillas biométricas al backend de GymFort salvo una decisión
  posterior, necesidad técnica demostrada y revisión específica de seguridad,
  privacidad y cumplimiento.

La fotografía de perfil de GymFort y la fotografía/plantilla biométrica del
proveedor son conceptos distintos aunque en una etapa futura pueda automatizarse
el enrolamiento utilizando capacidades oficiales de HikCentral.

## 7. Fases del piloto HikCentral

### Fase 0 - Descubrimiento técnico

Antes de escribir el adaptador se debe confirmar en el gimnasio:

- versión exacta de HikCentral Professional;
- modelo y firmware del terminal/controlador Hikvision;
- disponibilidad, versión y licencia de OpenAPI/Open Platform o SDK aplicable;
- endpoint/puerto y autenticación permitidos;
- estructura actual de Persons, Doors y Access Levels;
- posibilidad oficial de alta/actualización de persona, rostro, asignación de
  Access Level, apertura remota y consulta/suscripción de eventos.

Ningún endpoint o puerto de proveedor debe quedar codificado hasta confirmar
estas capacidades contra la instalación real.

### Fase 1 - Sincronización mínima de permisos

Objetivo: eliminar la duplicidad diaria de altas/bajas de autorización.

El enrolamiento facial puede seguir siendo manual una sola vez en HikCentral.
GymFort almacena el identificador externo de la persona y sincroniza derechos:

- membresía activada/pagada -> conceder Access Levels correspondientes;
- renovación -> mantener/actualizar vigencia;
- cambio de plan -> recalcular derechos y retirar/agregar niveles;
- vencimiento/cancelación/suspensión -> revocar niveles;
- reconciliación periódica -> reparar diferencias entre estado deseado y estado
  real del proveedor.

### Fase 2 - Alta de persona/enrolamiento asistido

Si la versión/licencia lo permite de forma oficial y segura:

- crear persona desde GymFort;
- enviar/actualizar fotografía apta para enrolamiento;
- conservar el identificador externo;
- seguir dejando el matching biométrico dentro de Hikvision.

### Fase 3 - Eventos y bitácora

Recibir o consultar eventos de HikCentral y conciliarlos con `access_log` para
mostrar en GymFort accesos reales, rechazos, dispositivo y hora, con idempotencia
y aislamiento por organización/sucursal.

### Fase 4 - Resiliencia y otros proveedores

Sólo después del piloto:

- telemetría y heartbeat;
- cola/reintentos del conector;
- reconciliación después de desconexión;
- adaptación a un segundo fabricante;
- abstracción común de credenciales si aparece una necesidad real.

No se diseñarán desde ahora múltiples adapters hipotéticos sin un segundo caso
real.

## 8. Sincronización de derechos: comportamiento objetivo

La integración física debe trabajar con **estado deseado**, no con comandos
frágiles enviados una sola vez.

Ejemplo:

```text
Client Pedro
Membership Premium ACTIVE
Benefits deseados: [GYM_ACCESS, SAUNA_ACCESS]

HikCentral actual:
[GYM_ACCESS]

Reconciliación:
+ SAUNA_ACCESS
```

Si la llamada falla, GymFort conserva que el estado físico está pendiente y
reintenta/reconcilia. Un pago confirmado no debe considerarse "sincronización de
hardware exitosa" sólo porque la membresía quedó activa.

Debe poder distinguirse al menos:

- estado comercial de membresía;
- derechos calculados por GymFort;
- estado de sincronización del proveedor;
- último error/última sincronización exitosa.

## 9. Permisos y auditoría objetivo

Permisos existentes/recomendados:

- `access.read`: dispositivos, decisiones, bitácora y estado de sincronización;
- `access.manage`: configuración funcional de dispositivos y beneficios;
- `access.manual.open`: apertura excepcional inmediata;
- los permisos de visita históricos se mantendrán sólo mientras sobreviva el
  flujo legacy de `visit_pass`.

Toda mutación física relevante debe auditar actor, organización, sucursal,
dispositivo, motivo, correlación y resultado. Nunca se deben registrar secretos,
plantillas biométricas ni payloads sensibles completos del proveedor.

## 10. Matriz mínima de pruebas antes de producción física

- membresía vigente con `GYM_ACCESS` -> permitido;
- membresía vigente sin beneficio requerido -> denegado;
- plan de visita diaria básico -> entrada general y no sauna;
- plan de visita diaria Premium -> entrada general y sauna;
- vencida fuera de gracia -> denegado/revocado en proveedor;
- gracia -> comportamiento y mensaje correctos;
- cliente suspendido/inactivo -> denegado/revocado;
- cambio de plan -> revocación y concesión correctas;
- multi-sucursal según alcance real del beneficio;
- dispositivo de otra sucursal/tenant -> rechazo;
- apertura manual requiere permiso dedicado y deja auditoría;
- consumo concurrente de autorización temporal;
- reintento de sincronización idempotente;
- reconciliación de diferencia GymFort/HikCentral;
- evento duplicado del proveedor no duplica `access_log`;
- desconexión del conector no altera incorrectamente la membresía comercial.

## 11. Prioridad de cambios

Orden recomendado para la siguiente evolución del módulo:

1. Retirar de la UX principal "Paquetes de visitas" y renombrar "Pase temporal"
   a "Autorización temporal".
2. Crear/configurar planes `Visita diaria` y `Visita diaria Premium` usando el
   flujo normal de membresías y beneficios `ACCESS`.
3. Exigir `GYM_ACCESS` explícito en la entrada principal del piloto en vez de
   depender de "sin requisitos = cualquier membresía".
4. Corregir `access.manual.open` para que proteja realmente la apertura manual.
5. Hacer atómico el consumo de autorización temporal y ampliar matriz de
   seguridad/concurrencia (`ACC-001`).
6. Introducir el puerto de integración física sin acoplar `AccessService` a
   Hikvision.
7. Ejecutar Fase 0 de descubrimiento contra la instalación real de HikCentral.
8. Implementar conector local + sincronización mínima de derechos para el primer
   gimnasio piloto.
9. Incorporar eventos reales a la bitácora.
10. Automatizar enrolamiento sólo si el API/licencia instalados lo permiten y
    después de validar privacidad/seguridad.
11. Abstraer otros fabricantes únicamente cuando exista un segundo caso real.

## 12. Criterio de éxito del módulo

Access se considerará listo para el primer gimnasio real cuando:

- una membresía pagada y vigente determine sus derechos exclusivamente mediante
  `ACCESS`;
- una visita diaria use el mismo motor de membresías, sin `VisitPass` comercial;
- la puerta principal y las zonas restringidas tengan requisitos explícitos;
- GymFort pueda sincronizar concesión y revocación con HikCentral sin que
  recepción mantenga manualmente permisos en ambos sistemas;
- el reconocimiento y la decisión local puedan seguir funcionando aunque
  GymFort Cloud no sea alcanzable momentáneamente;
- los eventos relevantes puedan auditarse/reconciliarse;
- apertura manual y autorizaciones temporales permanezcan como excepciones
  controladas, no como camino comercial normal.
