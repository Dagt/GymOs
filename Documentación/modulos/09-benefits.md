# Beneficios y elegibilidad

Estado: `ESTABILIZADO`
Última revisión: 25 de agosto de 2026

## Implementado

- definición y asignación de beneficios económicos;
- verificación de beneficios de socios;
- beneficios especiales de tipo `ACCESS` vinculados a planes;
- edición de nombre, estado, reglas económicas y planes vinculados
  desde el mismo beneficio; código, tipo, sucursal e ID se conservan;
- formulario sin vigencias ni control de evidencia para ACCESS; al editar se
  conservan esos valores históricos, sin relajar la elegibilidad del backend;
- integración con membresías, acceso y reservas mediante `EligibilityPolicy`.

## Pendiente

- pruebas negativas exhaustivas de permisos y aislamiento;
- completar la configuración explícita de beneficios `ACCESS` para todos los
  puntos físicos que entren al piloto real.

## Estado vigente de elegibilidad

Los beneficios de tipo `ACCESS` administran desde el propio beneficio los planes
que los conceden. La relación muchos-a-muchos se persiste en
`benefit_membership_plan`; no se copian filas por socio. La consulta efectiva
membresía vigente -> plan -> beneficio ya está disponible desde Access y los
dispositivos pueden declarar beneficios requeridos.

`ACCESS` es la abstracción canónica de los **derechos físicos** del socio. Un
plan no concede acceso por llamarse Básico, Premium o Visita: concede los
beneficios `ACCESS` que tenga relacionados. Esta misma regla aplica a planes de
larga duración y a planes de visita diaria de un solo día.

Ejemplo:

```text
Plan Mensual Básico       -> GYM_ACCESS
Plan Mensual Premium      -> GYM_ACCESS + SAUNA_ACCESS
Plan Visita Diaria        -> GYM_ACCESS
Plan Visita Diaria Premium-> GYM_ACCESS + SAUNA_ACCESS
```

Los dispositivos/zona expresan el requisito del otro lado:

```text
Entrada principal -> GYM_ACCESS
Sauna             -> SAUNA_ACCESS
```

Access compara los derechos efectivos del plan vigente contra lo requerido por
el punto físico. Un punto productivo exige **exactamente un beneficio ACCESS**;
cero o varios deniegan por configuración inválida. Las reglas diagnósticas de
`SIMULATED` no se trasladan a producción.

Para agregar el pase diario VIP al Sauna existente: **Beneficios → Sauna →
Editar → seleccionar VIP y VIP diario → Guardar cambios**. No se crea otro
beneficio ni se cambian los vínculos del punto de acceso. El editor vuelve a
consultar planes y versión al abrirse. Los vínculos existentes a planes retirados
pueden conservarse; sólo los nuevos vínculos exigen planes activos de la misma
organización. Retirar un vínculo retira ese derecho heredado de sus socios.

`PUT /benefits/{id}` exige `benefit.manage`, tenant/sucursal correctos y
`expectedVersion`. Un conflicto no sobrescribe cambios ajenos y mantiene el
formulario para revisión; cerrarlo y reabrirlo consulta la versión actual. El
cambio se audita y publica reconciliación física para la unión de planes
anteriores y nuevos, incluyendo aquellos que perdieron el beneficio. No añade
una llamada al hardware ni modifica la arquitectura provider-neutral.

Pruebas de identidad estable, control optimista, permisos, aislamiento y planes
retirados: `OperationalHardeningPostgresIntegrationTests`. Recorrido del ejemplo
Sauna/VIP diario: `frontend/e2e/operational-hardening.spec.ts`.

Los beneficios de precio se resuelven en backend según vigencia, evidencia,
combinación y prioridad; el helper frontend dejó de ser la fuente de verdad
operacional.

`ACCESS` no acepta valores de descuento ni asignación directa al socio. Los
beneficios económicos exigen una definición válida y sus asignaciones requieren
caducidad. La cotización filtra tenant, sucursal, vigencia, estado y evidencia;
la venta conserva el resultado en `membership_sale_benefit`. Una prueba
PostgreSQL demuestra que crear la relación beneficio-plan actualiza
inmediatamente al socio y que cambiar su plan revoca esa herencia sin copiar
asignaciones.

La ficha del socio presenta dos fuentes sin mezclarlas: `ACCESS` se consulta
como elegibilidad efectiva del plan activo y los descuentos/convenios se
consultan como asignaciones directas. Sólo los beneficios económicos aparecen
en el selector de asignación; todos, incluido `CUSTOM`, requieren fecha de
expiración. Al cambiar plan, la ficha vuelve a consultar los accesos efectivos
para reflejar la nueva relación sin recargar la página.

Los beneficios nuevos se crean con alcance de la sucursal activa. V58 permite
reutilizar el mismo código de negocio en distintas sucursales y el catálogo sólo
expone los beneficios de la sucursal seleccionada más cualquier definición
global deliberada, que se rotula explícitamente. La migración recupera desde
`audit_log` la sucursal de beneficios creados por versiones que omitían
`branch_id`. Las asignaciones directas y su verificación quedan aisladas por
sucursal.

Un descuento con `requiresEvidence=true` no se presenta como vigente mientras
siga en `ASSIGNED`: la ficha lo marca como pendiente y ofrece verificación sólo
a quien tenga `client.benefit.verify`. Sin evidencia requerida, la asignación es
elegible inmediatamente para alta, renovación y cambio de plan.

## Política transversal

`com.gymos.eligibility` es la fuente única de verdad para decidir elegibilidad.
Expone solamente `eligibility.api.EligibilityPolicy`; sus consultas viven en
`eligibility.infrastructure` y reciben siempre organización, sucursal, socio y
fecha efectiva.

- Membresías solicita los beneficios económicos elegibles para cotizar en
  backend.
- Reservas entrega los planes admitidos por el recurso y evalúa la membresía en
  la fecha local de la reserva.
- Acceso entrega los beneficios exigidos por el punto y evalúa la membresía y
  herencia dinámica `plan -> ACCESS`.
- Cliente inexistente/inactivo, sucursal, vigencia y gracia se interpretan igual
  en los tres flujos.

El recurso o dispositivo sigue siendo dueño de su configuración. La política
transversal es dueña de interpretar los hechos y devolver una decisión
consistente; no copia beneficios al socio ni acopla reservas con hardware.

## Relación con proveedores físicos

Los beneficios `ACCESS` permanecen independientes de Hikvision, HikCentral o
cualquier otro fabricante. La integración física debe mapear un beneficio de
GymFort a la noción equivalente del proveedor, por ejemplo un `Access Level` de
HikCentral, sin guardar nombres o identificadores del fabricante dentro de la
regla de elegibilidad.

Ejemplo conceptual:

```text
GymFort GYM_ACCESS   <-> HikCentral Access Level general
GymFort SAUNA_ACCESS <-> HikCentral Access Level sauna
```

Ese mapping es configuración de integración por organización/sucursal. El
beneficio continúa siendo la fuente estable aun cuando el hardware sea
reemplazado posteriormente.

## Visitas y paquetes históricos

La infraestructura histórica de `VisitPass` no se considera parte del modelo
vigente de Benefits. No se añadirá una relación genérica producto -> beneficio
para soportar paquetes de visitas. Las visitas comerciales normales se modelan
como planes de membresía de un día y heredan `ACCESS` exactamente igual que
cualquier otro plan.
