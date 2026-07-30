# Beneficios y elegibilidad

Estado: `PARCIAL`

## Implementado

- definición y asignación de beneficios;
- verificación de beneficios de socios;
- paquetes de visitas;
- integración parcial con membresías, acceso y reservas.

## Pendiente

- fuente única de verdad para elegibilidad;
- cobertura de expiración, consumo y concurrencia;
- pruebas de permisos y aislamiento.
## Evolución en `feature/reception-pos-init`

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
