# Beneficios y elegibilidad

Estado: `ESTABILIZADO`
Última revisión: 21 de agosto de 2026

## Implementado

- definición y asignación de beneficios;
- verificación de beneficios de socios;
- paquetes de visitas;
- integración con membresías, acceso y reservas mediante `EligibilityPolicy`.

## Pendiente

- consumo concurrente de pases de visita;
- pruebas negativas exhaustivas de permisos y aislamiento.
## Estado vigente de elegibilidad

Los beneficios de tipo `ACCESS` administran desde el propio beneficio los planes
que los conceden. La relación muchos-a-muchos se persiste en
`benefit_membership_plan`; no se copian filas por socio. La consulta efectiva
membresía vigente → plan → beneficio ya está disponible desde Access y los
dispositivos pueden declarar beneficios requeridos. Los beneficios de precio se
resuelven en backend según vigencia, evidencia, combinación y prioridad; el
helper frontend dejó de ser la fuente de verdad operacional.

`ACCESS` no acepta valores de descuento ni asignación directa al socio. Los
beneficios económicos exigen una definición válida y sus asignaciones requieren
caducidad. La cotización filtra tenant, sucursal, vigencia, estado y evidencia;
la venta conserva el resultado en `membership_sale_benefit`. Una prueba
PostgreSQL demuestra que crear la relación beneficio–plan actualiza
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
  herencia dinámica `plan → ACCESS`.
- Cliente inexistente/inactivo, sucursal, vigencia y gracia se interpretan igual
  en los tres flujos.

El recurso o dispositivo sigue siendo dueño de su configuración. La política
transversal es dueña de interpretar los hechos y devolver una decisión
consistente; no copia beneficios al socio ni acopla reservas con hardware.
