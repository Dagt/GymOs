# Planes, beneficios y paquetes de visitas multi-sucursal

Estado: ARQUITECTURA IMPLEMENTADA / VALIDACIÓN EN CURSO

Corte: 28 de agosto de 2026

Rama: `feature/multibranch-plans-benefits-visits`

## 1. Objetivo

Este documento fija el contrato funcional y técnico para cerrar las necesidades
de planes del primer cliente real sin confundir sucursales con tenants. El
cliente opera dos marcas como sucursales de una sola organización; socios,
catálogo, planes, beneficios y saldos permanecen siempre aislados dentro de esa
organización.

El bloque incluye:

- contratación y administración coherente de membresías multi-sucursal;
- planes privados que Recepción puede vender pero el kiosco no ofrece a nuevos
  compradores;
- exención de inscripción por plan;
- precios especiales aplicables sólo a planes explícitos;
- beneficios `ACCESS` locales o válidos en la cobertura del plan;
- paquetes vendibles de N visitas, con vigencia y saldo único utilizable en las
  sucursales configuradas.

No incluye branding por sucursal, cambios de Stripe Connect, integración física
HikCentral ni rediseños ajenos a estos recorridos.

## 2. Términos y decisiones de dominio

### 2.1 Membresía ilimitada

Una `membership` representa el derecho temporal concedido por un
`membership_plan`. Puede estar limitada a la sucursal de origen o cubrir todas
las sucursales del tenant mediante `multiBranchAccess`.

Una membresía de un día permite entradas ilimitadas durante ese día. No es un
paquete de una visita.

### 2.2 Paquete de visitas

Un paquete de visitas es un producto de acceso consumible. Define:

- número de visitas;
- vigencia en días;
- precio;
- sucursales donde puede consumirse;
- derechos `ACCESS` cubiertos;
- disponibilidad para nuevas compras en kiosco;
- política de inscripción.

Las sucursales y los accesos elegidos responden preguntas diferentes:

- `branchScope/branchIds`: en qué sucursales existe el saldo compartido;
- `benefits[]`: qué accesos forman parte del paquete y cómo interactúan con su
  saldo;
- `benefit.accessScope`: en qué sucursales sigue siendo válido cada acceso.

Un paquete nunca amplía el alcance de un beneficio. La regla efectiva por acceso
es la intersección entre las sucursales del paquete y el alcance del beneficio.
La relación paquete–beneficio, no el `Benefit`, define uno de estos modos:

- `CONSUMES_VISIT`: autoriza el acceso y descuenta una unidad;
- `INCLUDED_WITH_VISIT`: autoriza sin otro descuento sólo después de un consumo
  base del mismo entitlement, misma fecha operativa y misma sucursal.

Por ejemplo, un paquete válido en Centro y Tecnológica puede configurar
`GENERAL_ACCESS` como `CONSUMES_VISIT` en ambas y `SAUNA` local de Centro como
`INCLUDED_WITH_VISIT`. General descuenta en ambas sedes; Sauna sólo se concede
en Centro después de registrar General allí ese día y no vuelve a descontar.
Seleccionar Sauna no crea ni muestra ese derecho en Tecnológica. Cada sucursal
elegida debe tener al menos un acceso `CONSUMES_VISIT`; un paquete compuesto
sólo por accesos incluidos se rechaza.

El mismo `SAUNA` puede ser `INCLUDED_WITH_VISIT` en “10 visitas + Sauna” y
`CONSUMES_VISIT` en “5 sesiones de Sauna”. Ésta es semántica del producto
comercial, nunca una propiedad intrínseca de Sauna.

Para configurar estos productos desde cualquier sede, Catálogo obtiene los
Benefits `ACCESS` activos de toda la organización mediante
`GET /api/v1/catalog/access-benefits`. La interfaz muestra en qué sucursales es
efectivo cada uno. Ver Sauna de Centro al operar en Tecnológica sólo permite
seleccionarlo cuando Centro también forma parte del paquete; no crea Sauna ni
derechos equivalentes en Tecnológica. El backend vuelve a validar la
intersección y no confía en el filtrado de la UI.

Si una edición posterior de planes/beneficios deja una definición comercial sin
cobertura efectiva o una sede sin `CONSUMES_VISIT`, la definición deja de
publicarse en venta y autoservicio hasta que un administrador la repare. Sigue
visible en el listado administrativo y los entitlements vendidos conservan sus
snapshots; no se mutan compras históricas.

No es una `membership` y no utiliza la restricción de una membresía activa. Se
modela independientemente para no alterar renovaciones, cambios de plan,
domiciliación ni reportes de membresías ilimitadas.

Un socio puede conservar una membresía ilimitada y un paquete de visitas, pero
GymFort concede primero el acceso por membresía. Si la membresía autoriza la
entrada no se descuenta una visita.

### 2.3 Exclusividad del paquete

Un socio no puede tener dos paquetes de visitas abiertos al mismo tiempo dentro
de la misma organización. La exclusividad cubre tanto `PENDING_PAYMENT` como
`ACTIVE`, porque permitir dos Checkouts pendientes podría terminar activando dos
saldos después de dos webhooks válidos.

Puede adquirir otro paquete únicamente cuando el anterior se encuentre
`CONSUMED`, `EXPIRED` o `CANCELLED`. Los saldos no se apilan ni se trasladan al
nuevo paquete.

### 2.4 Producto tipo servicio

El catálogo ya reconoce `SERVICE/VISIT_PASS`, pero hoy esa clasificación sólo
describe un concepto vendible y no concede acceso. Este lote la convierte en la
entrada comercial de un paquete configurado.

Un servicio con nombre “Pase de visita” pero sin definición de paquete no crea
saldo ni derechos de acceso. Los registros existentes no se transforman por
nombre ni se interpretan automáticamente.

## 3. Políticas de planes de membresía

`membership_plan` agrega:

```text
self_service_acquisition_enabled BOOLEAN
registration_fee_policy          DEFAULT | WAIVED
```

`self_service_acquisition_enabled` significa “disponible para nuevas altas y
cambios desde autoservicio”. No significa que el plan o la membresía se oculten
por completo.

| Operación | Habilitado | Deshabilitado |
|---|---:|---:|
| Alta en Recepción | Sí | Sí |
| Cambio en Recepción | Sí | Sí |
| Nueva alta en kiosco | Sí | No |
| Cambio hacia el plan en kiosco | Sí | No |
| Mostrar membresía actual | Sí | Sí |
| Renovar el plan que ya posee | Sí | Sí |

La seguridad se valida en backend. El catálogo del navegador no es una frontera
de autorización.

`registration_fee_policy` funciona así:

- `DEFAULT`: consulta la política general y `inactivityMonths`;
- `WAIVED`: nunca agrega `REGISTRATION_FEE` cuando el plan es el objetivo de la
  operación.

La inscripción continúa como `sale_item` independiente. No crea producto, SKU,
inventario ni descuento adicional. Los beneficios económicos sólo descuentan
el precio del plan.

## 4. Membresías multi-sucursal en kiosco

La disponibilidad de una membresía y la disponibilidad de un plan para nuevas
ventas son conceptos distintos.

Una sesión de la sucursal B debe mostrar una membresía creada en A cuando el plan
sea multi-sucursal. Renovación y cambio dejan de depender estrictamente de
`membership.branch_id`:

- renovación: permitida en cualquier sucursal cubierta; conserva la sucursal de
  origen de la membresía;
- venta, pago y `membership_period`: se atribuyen a la sucursal donde ocurrió la
  operación;
- cambio de plan: el plan destino debe estar disponible en la sucursal actual;
  la nueva membresía toma esa sucursal como origen;
- domiciliación: conserva por ahora la restricción de la sucursal del mandato
  hasta diseñar atribución, recibo y conciliación multi-sucursal.

Un plan está disponible comercialmente en una sucursal cuando:

```text
membership_plan.multi_branch_access = true
OR
catalog_product_branch contiene esa sucursal
```

Para una adquisición o cambio desde kiosco también se exige:

```text
self_service_acquisition_enabled = true
```

Una renovación deriva plan y precio desde la membresía existente y no confía en
un `planId` libre enviado por el navegador. Las cotizaciones deben expresar su
operación (`ENROLLMENT`, `RENEWAL` o `PLAN_CHANGE`) para aplicar la política
correcta y evitar contratar un plan privado mediante una llamada directa.

## 5. Beneficios económicos

### 5.1 Precio especial

`SPECIAL_PRICE` representa un precio absoluto y debe tener uno o más planes
aplicables. Se crea una relación diferente de la relación de acceso:

```text
pricing_benefit_membership_plan
├── organization_id
├── benefit_id
└── membership_plan_id
```

Semántica:

```text
socio posee beneficio económico
        ↓
beneficio puede aplicarse a planes seleccionados
```

La elegibilidad de precio recibe siempre el plan objetivo. Un `SPECIAL_PRICE`
sin relación no se aplica. Los registros preexistentes quedan pendientes de
clasificación; no se adivina su plan por nombre o importe.

`PERCENT` y `FIXED` conservan inicialmente su alcance general para no complicar
la operación existente.

### 5.2 Beneficios de acceso

Un beneficio `ACCESS` agrega alcance explícito:

```text
LOCAL
PLAN_COVERAGE
```

- `LOCAL`: sólo se reconoce en `benefit.branch_id`;
- `PLAN_COVERAGE`: se reconoce en las sucursales donde sea válido el plan que lo
  concede.

No se copian beneficios por sucursal. El alcance del beneficio y la cobertura
del plan se evalúan conjuntamente. Los mapeos de Access Levels físicos continúan
siendo propios de cada sucursal.

## 6. Arquitectura de paquetes de visitas

### 6.1 Definición vendible

Una nueva definición `visit_package_plan` se vincula uno a uno con un
`catalog_product` de tipo `SERVICE` y subtipo `VISIT_PASS`.

Campos mínimos:

```text
id
organization_id
product_id
total_visits
validity_days
branch_scope                  ALL_BRANCHES | SELECTED_BRANCHES
self_service_acquisition_enabled
registration_fee_policy      DEFAULT | WAIVED
status
version / auditoría temporal
```

Relaciones:

- `visit_package_plan_branch`: sucursales permitidas cuando el alcance es
  `SELECTED_BRANCHES`;
- `visit_package_plan_benefit`: beneficios `ACCESS` que puede satisfacer el
  paquete y su `usage_mode` (`CONSUMES_VISIT|INCLUDED_WITH_VISIT`).

La configuración exige visitas y vigencia mayores a cero, al menos una sucursal
para alcance seleccionado y al menos un beneficio `ACCESS`. Cuando se crea o
edita desde una sucursal activa, la cobertura debe incluir esa sucursal: puede
ser sólo la actual o la actual más otras, nunca exclusivamente una sede ajena.
Cada sede efectiva debe tener por lo menos un acceso `CONSUMES_VISIT`; ningún
beneficio puede quedar completamente fuera del alcance del paquete.

### 6.2 Reserva y saldo del socio

`visit_package_entitlement` representa la compra individual:

```text
id
organization_id
home_branch_id
client_id
visit_package_plan_id
sale_id
status                       PENDING_PAYMENT | ACTIVE | CONSUMED |
                             EXPIRED | CANCELLED
total_visits
remaining_visits
valid_from
valid_until
activated_at
pending_expires_at           expiración del Checkout pendiente
version / auditoría temporal
```

La definición comercial se materializa como snapshot al reservar la compra
pendiente. Se copian las sucursales, beneficios y modos comprados a:

- `visit_package_entitlement_branch`;
- `visit_package_entitlement_benefit`.

V100 agrega `visit_package_entitlement_benefit_branch`, que materializa la
intersección acceso–sucursal comprada. V101 añade `usage_mode` tanto al snapshot
general como a esta combinación exacta. El motor exige esa fila: comprobar por
separado “sucursal incluida” y “beneficio incluido” era insuficiente porque
podía combinar permisos que nunca coexistieron en la misma sede.

Editar un producto después no modifica derechos ya pagados.

Una restricción parcial única por `(organization_id, client_id)` impide más de
un entitlement en `PENDING_PAYMENT` o `ACTIVE`. La creación también valida el
conflicto con bloqueo transaccional y devuelve un error de negocio legible.

### 6.3 Venta y activación

Flujo:

```text
selección del paquete
→ cotización autoritativa
→ venta + partida VISIT_PACKAGE
→ entitlement PENDING_PAYMENT
→ pago confirmado y conciliado
→ entitlement ACTIVE
```

Recepción con pago manual confirmado utiliza el mismo evento de finalización.
Stripe nunca activa el saldo por redirect; sólo lo hace el webhook conciliado.
La llave de idempotencia conserva venta, partida, cargo de inscripción y
entitlement.

Un Checkout pendiente tiene vencimiento operativo. Al vencer se cancela la
reserva para permitir una compra nueva, sin borrar venta, pago ni auditoría.

### 6.4 Consumo en acceso

El orden de decisión es:

1. validar socio, sucursal, dispositivo y beneficio requerido;
2. intentar conceder acceso mediante membresía vigente;
3. sólo si la membresía no concede ese derecho, buscar un paquete activo,
   vigente y una combinación snapshot exacta sucursal–beneficio compatible;
4. si el modo es `CONSUMES_VISIT`, exigir saldo, descontar exactamente una
   unidad e insertar el consumo base;
5. si el modo es `INCLUDED_WITH_VISIT`, localizar un consumo exitoso del mismo
   entitlement, misma `business_date` y misma sucursal; conceder sin decremento;
6. registrar idempotentemente la decisión y su relación con el consumo base;
   si falta la visita base, conservar también la denegación sin saldo para que
   el mismo evento nunca cambie de respuesta al reintentarse.

`visit_package_consumption` conserva:

```text
id
organization_id
entitlement_id
benefit_id
branch_id
access_device_id / access_log_id
idempotency_key
business_date
occurred_at
status                       CONSUMED | REVERSED
reason / reversal metadata
```

Una llave única por organización y evento impide descontar dos veces cuando el
lector, Connector o API reintentan. La actualización exige
`remaining_visits > 0`, estado `ACTIVE` y vigencia actual. Al llegar a cero, el
entitlement cambia a `CONSUMED` en la misma transacción.

`visit_package_included_access` conserva cada uso incluido sin alterar saldo:

```text
entitlement_id / benefit_id / branch_id
base_consumption_id
access_device_id / access_log_id
idempotency_key
status                       GRANTED | DENIED
remaining_after
business_date / occurred_at
```

La referencia compuesta al consumo base obliga a que pertenezca al mismo
entitlement, organización, sucursal y fecha operativa. `business_date` se
calcula con el timezone de la sucursal, no con UTC ni con la zona del servidor.
Una fila `GRANTED` exige `base_consumption_id`; una fila `DENIED` exige que sea
nulo. Ambas quedan vinculadas a la decisión final de `access_log`, por lo que
un reintento devuelve la primera decisión aunque entre ambos intentos se haya
registrado una visita General.
Un bloqueo transaccional por socio protege el saldo, y un advisory lock por
organización+llave protege la idempotencia incluso ante eventos concurrentes.

V1 exige estrictamente un consumo base del mismo paquete. Si una membresía
concede General, el paquete no se consume; por tanto Sauna
`INCLUDED_WITH_VISIT` de ese paquete no se habilita por esa entrada. Una futura
regla de “visita base concedida por cualquier producto” requeriría otra decisión
de negocio y no se infiere silenciosamente.

El paquete no se consume cuando una membresía ya autorizó el acceso, la sucursal
no está cubierta, el dispositivo requiere otro beneficio o la decisión termina
denegada.

## 7. Reembolsos, expiración y conservación histórica

- un paquete pendiente puede cancelarse sin saldo;
- un reembolso total antes de consumos cancela el entitlement;
- no se permite un reembolso automático total después de haber consumido
  visitas; requiere resolución administrativa explícita;
- expiración cambia estado, no elimina filas;
- consumos, ventas, pagos y snapshots son históricos e inmutables salvo una
  reversión auditada;
- este lote no implementa transferencia de saldos ni combinación de paquetes.

## 8. API objetivo

Catálogo comercial, en la misma pantalla donde viven los planes de membresía:

```text
POST   /api/v1/catalog/visit-package-plans
PATCH  /api/v1/catalog/visit-package-plans/{id}
GET    /api/v1/catalog/visit-package-plans
```

Planes de membresía extienden sus contratos actuales con:

```text
selfServiceAcquisitionEnabled
registrationFeePolicy
```

Beneficios extienden sus contratos con:

```text
accessScope
applicablePlanIds
```

Paquetes de visitas aceptan y devuelven la semántica de cada relación:

```json
{
  "benefits": [
    { "benefitId": "...GENERAL...", "usageMode": "CONSUMES_VISIT" },
    { "benefitId": "...SAUNA...", "usageMode": "INCLUDED_WITH_VISIT" }
  ]
}
```

`benefitIds` continúa aceptándose temporalmente en escritura como contrato
legacy y se interpreta de forma conservadora como `CONSUMES_VISIT`. Las
respuestas incluyen `benefits` y conservan `benefitIds` derivado para clientes
anteriores.

Comercio/Recepción:

```text
POST /api/v1/visit-packages/quotes
POST /api/v1/visit-packages/purchases
GET  /api/v1/clients/{clientId}/visit-package
```

Kiosco expone opciones y compra sólo si la capacidad de sucursal está
configurada, el paquete permite autoservicio y Stripe Checkout está listo. La
intención configurada permanece guardada aunque Stripe esté pendiente.

Contratos de kiosco implementados:

```text
GET  /api/v1/self-service/sessions/{sessionId}/visit-package-options
GET  /api/v1/self-service/sessions/{sessionId}/visit-package-quotes/{planId}
GET  /api/v1/self-service/sessions/{sessionId}/visit-package
POST /api/v1/self-service/sessions/{sessionId}/visit-package-purchases
```

`allowed_actions_json.visitPackagePurchase` representa intención por sucursal.
Si está encendida y Stripe sigue pendiente, opciones y cotización permanecen
visibles, pero el POST de compra rechaza la operación antes de crear venta o
entitlement. Cuando Stripe cambia a ready, el mismo valor configurado habilita
Checkout sin volver a guardar ajustes.

La consulta del paquete vigente devuelve `204 No Content` cuando no existe un
saldo abierto. Ese resultado es normal. Registro e identificación no dependen de
que el socio tenga paquete y una falla de carga secundaria no revierte ni oculta
una identidad ya verificada.

La identidad se resuelve dentro de toda la organización. `home_branch_id` no
impide reconocer al socio desde otra sede; la autorización concreta sigue
evaluándose con la sucursal del kiosco. Alternar entre identificación y registro
reinicia la sesión antes de capturar a la siguiente persona.

Access es el punto de entrada del consumo. ACC-006 incorpora la confirmación
del ingreso presencial de un socio en Recepción, con elegibilidad y movimiento
de ledger vinculados a la entrada.

## 9. UX objetivo

### Plan de membresía

- toggle “Mostrar para contratar en kiosco”; al apagarlo el plan queda oculto
  para nuevas altas/cambios de autoservicio, pero no se elimina ni se oculta en
  Recepción;
- selector “Inscripción: configuración general / no cobrar”;
- indicador claro de cobertura de sucursales;
- un plan privado sigue visible y seleccionable en Recepción.

### Beneficio

- `SPECIAL_PRICE` muestra “Aplica a” y exige al menos un plan;
- `ACCESS` separa dos decisiones: qué planes lo incluyen y dónde es válido;
- “Sucursal actual” mantiene el acceso local aunque el plan sea multisucursal;
- “Sucursales de cada plan” sigue su cobertura sin duplicar beneficios;
- no se obliga a crear un beneficio para vender un plan privado.

### Paquete de visitas

Los paquetes no viven en Configuración del sistema. Se administran en
`Catálogo`, inmediatamente junto a `Planes de membresía`, porque ambos son
ofertas comerciales vendibles. Configuración sólo conserva la capacidad por
sucursal que permite o impide ofrecer paquetes en ese kiosco.

Las acciones de alta son locales a cada pestaña del Catálogo: artículo/cargo y
variantes en `Artículos`, nuevo plan en `Planes de membresía`, nuevo paquete en
`Paquetes de visitas` y categorías dentro de `Categorías`.

El alta solicita únicamente:

- nombre, SKU, precio y descripción;
- número de visitas;
- vigencia;
- sucursal base fija y sucursales adicionales opcionales;
- accesos del paquete y, por cada uno, “Consume una visita” o “Se incluye con
  la visita”;
- inscripción general o exenta;
- disponible o no en kiosco.

La interfaz llama a las decisiones “Dónde puede usarse” y “Accesos del
paquete”. La lista se filtra con las sucursales elegidas y no repite debajo de
cada acceso leyendas “Disponible en / No disponible en”; la cobertura exacta se
valida y snapshottea en backend. La sucursal activa se muestra como base fija y
no aparece como checkbox; únicamente se marcan las sedes adicionales. Otra sede
sin ningún acceso queda deshabilitada. Antes de guardar,
la interfaz y backend comprueban que cada sede tenga al menos un acceso que
consuma una visita y explican que un acceso incluido requiere una visita base
ese día en esa misma sucursal.

El expediente muestra saldo, vigencia, estado y últimos consumos. Si ya existe
un paquete pendiente o activo, la UI no ofrece comprar otro y explica por qué.

En el kiosco no existe una sección comercial separada para paquetes: planes y
paquetes se presentan en la misma lista incluso si el socio ya tiene una
membresía y puede cambiarla. El nombre y la metadata distinguen el producto; el
plan sólo muestra días y el paquete muestra visitas más vigencia. La UI omite
la gracia y usa acciones de revisión compactas y centradas. Una membresía y un
paquete pueden coexistir; si ya hay otro paquete `PENDING_PAYMENT` o `ACTIVE`,
el kiosco explica la exclusividad sin mostrar el error HTTP ni códigos internos.
Los dos productos activos comparten una cuadrícula de “Servicios activos”; la
renovación ocupa una barra completa debajo de ellos y las cotizaciones aparecen
al final de las opciones como un checkout, sin reordenar el contenido hacia
arriba.

En la ficha administrativa del socio, los accesos del paquete se muestran
separados de los accesos heredados del plan. Se leen del snapshot del entitlement,
se filtran por la sucursal operada y enseñan su modo de uso. Así, una sesión de
Sauna consumible es visible para validación manual y para explicar la decisión
del dispositivo, sin afirmar incorrectamente que fue heredada de la membresía.

El POS genérico no permite agregar al carrito un `SERVICE/VISIT_PASS` que ya
está administrado por `visit_package_plan`; debe utilizarse la operación
dedicada para no crear una venta sin entitlement. Los servicios históricos no
configurados no se convierten por nombre.

## 10. Migraciones y compatibilidad

La secuencia aplicada es:

- V96: políticas de membresía y aplicabilidad de `SPECIAL_PRICE`;
- V97: alcance de beneficios `ACCESS`;
- V98: definición, entitlement, snapshots y ledger de paquetes de visitas.
- V99: snapshot de `validity_days` en el entitlement para que una compra
  pendiente conserve la vigencia cotizada aunque la definición cambie antes de
  confirmar el pago.
- V100: snapshot de cada combinación `benefit_id + branch_id` autorizada para el
  entitlement; corrige paquetes multi-sucursal sin alterar V96–V99.
- V101: modo de uso en la relación comercial y snapshots, fecha operativa en el
  consumo base y ledger separado de accesos incluidos. Relaciones preexistentes
  migran como `CONSUMES_VISIT`; no se reinterpretan ventas anteriores.
- V102: compatibilidad incremental del connector físico. V84 permanece igual a
  la versión publicada en `main`; un callback `beforeValidate` reconoce sólo
  los checksums exactos del V84 piloto y del primer V102 de prueba, y V102
  converge las formas conocidas, reencolando de
  forma idempotente trabajos `CLAIMED` anteriores al token de reclamo. Un
  callback `beforeEachMigrate` acotado a la transición V85→V86 prepara la
  nulabilidad de identidad que V86 necesita.

Defaults compatibles:

```text
self_service_acquisition_enabled = true
registration_fee_policy = DEFAULT
access_scope = LOCAL
```

No se modifica V95. La tabla histórica `visit_pass` se conserva para
compatibilidad de lectura y no se mezcla con el nuevo saldo sin una migración
explícita. Sus endpoints de alta y consumo operativo rechazan escrituras nuevas;
las compras pasan exclusivamente por el dominio `visit_package_entitlement`.
Los servicios existentes de subtipo `VISIT_PASS` quedan “sin configurar” hasta
que un administrador defina visitas, vigencia, sucursales y accesos; no se
infieren valores por nombre. El producto técnico que respalda una definición
nueva tampoco aparece como artículo vendible en el catálogo/POS genéricos: se
opera exclusivamente desde Paquetes de visitas.

Al habilitar las compras nuevas, el alta manual histórica pasa a sólo lectura.
La consulta de exclusividad también considera un `visit_pass` histórico activo:
no permite comprar un entitlement nuevo hasta regularizarlo. Si una base ya
contiene varios pases históricos activos para el mismo socio, la migración no
elige ni combina saldos silenciosamente; se reportan para resolución operativa.

La actividad de un paquete también participa en el ciclo de vida del socio: su
vigencia más reciente se compara junto con la última membresía para determinar
inactividad y una posible reinscripción. Un paquete activo, vigente y con saldo
evita una baja automática por inactividad.

## 11. Pruebas obligatorias

La matriz operativa completa para Sucursal Centro y Sucursal Tecnológica está
en [34-matriz-pruebas-centro-tecnologica.md](34-matriz-pruebas-centro-tecnologica.md).
Incluye productos creados desde cualquiera de las dos sedes, instalaciones
premium exclusivas de Centro y las combinaciones válidas e inválidas de
`LOCAL`, `PLAN_COVERAGE`, `CONSUMES_VISIT` e `INCLUDED_WITH_VISIT`.

### Planes y kiosco

- plan multisucursal asignado en A aparece en B;
- renovación en B conserva origen A y registra venta/periodo en B;
- plan privado no aparece en alta/cambio del kiosco;
- el dueño de un plan privado puede renovarlo;
- una llamada directa no permite contratar un plan privado;
- `WAIVED` nunca genera inscripción y `DEFAULT` conserva `inactivityMonths`;
- domiciliación continúa protegida por su alcance vigente.

### Beneficios

- `SPECIAL_PRICE` sólo modifica planes asociados;
- otro plan conserva precio lista;
- un precio especial sin planes es rechazado;
- beneficio `LOCAL` no cruza sucursal;
- beneficio `PLAN_COVERAGE` se hereda en la sede secundaria;
- los mapeos físicos siguen aislados por sucursal.

### Paquetes de visitas

- compra crea un único saldo después del pago confirmado;
- dos compras pendientes/activas para el mismo socio son rechazadas;
- paquete consumido o vencido permite una nueva compra;
- cada entrada descuenta exactamente una visita;
- General `CONSUMES_VISIT` + Sauna `INCLUDED_WITH_VISIT` descuenta una sola
  unidad durante la visita;
- un acceso incluido sin consumo base se deniega;
- consumos de otro entitlement, fecha o sucursal no habilitan el incluido;
- un paquete exclusivo de Sauna funciona con Sauna `CONSUMES_VISIT`;
- un reintento con la misma llave no descuenta dos veces;
- consumo concurrente nunca produce saldo negativo;
- el saldo se comparte entre sucursales permitidas;
- una combinación `paquete A+B + General A+B + Sauna LOCAL A` consume General
  en ambas y Sauna únicamente en A;
- un `PLAN_COVERAGE` sólo usa la intersección entre sedes del paquete y cobertura
  de los planes vinculados al beneficio;
- una sucursal no permitida no consume;
- una membresía que ya concede acceso evita el consumo del paquete;
- al llegar a cero cambia a `CONSUMED`;
- cambios posteriores al plan no alteran snapshots vendidos;
- migración completa desde base vacía y sobre V95;
- no regresiones de POS, membresías, ventas, Stripe, acceso y reportes.

## 12. Secuencia de implementación

1. [implementado] políticas de plan y autorización de operaciones de kiosco;
2. [implementado] consulta/renovación multi-sucursal;
3. [implementado] relación de `SPECIAL_PRICE` y cotización por plan objetivo;
4. [implementado] alcance explícito de beneficios `ACCESS`;
5. [implementado] tablas y dominio de paquetes de visitas;
6. [implementado] integración transaccional con venta/pago y activación;
7. [implementado] consumo idempotente con prioridad de membresía;
8. [implementado] UX base de Catálogo, Recepción, expediente y kiosco;
9. [implementado] historial operativo de saldos y últimos consumos, retiro de
   escrituras legacy y política segura de reembolso después de consumos;
10. [implementado y validado en PostgreSQL] semántica
    `CONSUMES_VISIT|INCLUDED_WITH_VISIT`, snapshot y auditoría sin doble
    decremento;
11. [validado el 29/08/2026] migraciones y 115 pruebas PostgreSQL reales de
    concurrencia, idempotencia, dos sedes, tenant isolation, base limpia y
    upgrades desde V83/V84/V85/V99 hasta V102, sin fallos ni omisiones; el
    volumen histórico V85 también se actualizó sin `repair` ni pérdida de datos.

Cada etapa debe mantener compilación y pruebas verdes. No se habilita compra de
paquetes en producción hasta que venta, activación, consumo y exclusividad estén
integrados de extremo a extremo. La evidencia local no sustituye la aceptación
externa con Stripe sandbox conectado ni la prueba de los dispositivos físicos.

## ACC-006 — Paquetes en el registro presencial

Recepción confirma una entrada por un derecho concreto, reusando el ledger
`visit_package_consumption`. Con `CONSUMES_VISIT` se descuenta una unidad y
`access_log_id` identifica la entrada. `INCLUDED_WITH_VISIT` se muestra como
parte de la visita base para esa sucursal. Un beneficio incluido por el paquete
queda fuera de las opciones de ingreso independiente. Una membresía que concede
el mismo derecho tiene prioridad. El historial de paquetes conserva el consumo
original y su estado `REVERSED` cuando un supervisor acredita la unidad con
motivo. Se conserva la vigencia original y el máximo de un paquete abierto.
Ver el [runbook](../docs/operations/reception-manual-check-in.md).
