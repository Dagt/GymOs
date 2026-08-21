# Socios y CRM

Estado: `FUNCIONAL`
Última revisión: 21 de agosto de 2026

## Implementado

- alta, búsqueda, consulta y actualización de socios;
- información personal y contacto;
- visibilidad en sedes secundarias cuando la membresía activa permite acceso
  multi-sucursal;
- membresía inicial y pago de entrada;
- importación de socios;
- detalle frontend y operación por sucursal.
- el listado proyecta el plan operativo actual y su vigencia: `ACTIVA` en
  pistache tenue, `VENCIDA` en rojo tenue y `SIN MEMBRESÍA` en neutral;
- ficha operativa con membresía, periodos, accesos heredados del plan y
  descuentos asignados directamente al socio;
- asignación de beneficios económicos desde la ficha, con expiración
  obligatoria y actualización visible después de guardarlos;
- V57 crea de forma canónica los permisos `client.*` y garantiza que dueño,
  gerencia y recepción puedan consultar, registrar y actualizar socios también
  en instalaciones iniciadas desde una base vacía.

## Matriz operativa

- dueño: alta, consulta y actualización;
- gerencia: alta, consulta y actualización;
- recepción: alta, consulta y actualización; la membresía inicial depende
  además de `sale.create`;
- coach: sólo consulta cuando su rol la tenga asignada.

## Pendiente

- cobertura CRUD y privacidad de PII;
- duplicados e importaciones fallidas;
- pruebas negativas multi-sucursal;
- puertos de aplicación consistentes (`GEN-002`).

Última evidencia: `DatabaseMigrationTests` sobre PostgreSQL limpio, proyección
de plan vigente validada sobre PostgreSQL y E2E de botón de alta visible para
gerencia y recepción; `member-workflows.spec.ts` valida el listado, beneficios,
accesos y cambio de plan desde la ficha, 30-jul-2026.
