# Accesos y visitas

Estado: `FUNCIONAL`
Última revisión: 21 de agosto de 2026

## Implementado

- pases y consumo de visitas;
- decisiones de acceso con membresía;
- elegibilidad multi-sucursal resuelta por la política compartida;
- dispositivos/terminales y registro de visitas;
- integración con beneficios y kiosco.

## Pendiente

- pruebas de reintentos, doble consumo y concurrencia;
- privacidad de respuestas de acceso;
- pruebas negativas por dispositivo, tenant y sucursal (`GEN-001`).
## Estado vigente de acceso efectivo

V53 relaciona dispositivos con beneficios `ACCESS`. Las puertas sin relación
siguen aceptando cualquier membresía vigente; las zonas restringidas exigen que
el plan vigente conceda al menos uno de los beneficios configurados. Se añadió
consulta de beneficios efectivos por socio y reemplazo auditable de la
configuración del dispositivo. Los adaptadores físicos y el modo offline están
aplazados deliberadamente y quedan fuera del alcance de
la rama histórica de recepción/POS; no bloquean el cierre de renovaciones, POS ni
beneficios.
