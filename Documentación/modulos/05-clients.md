# Socios y CRM

Estado: `FUNCIONAL`
Última revisión: 26 de agosto de 2026

## Implementado

- alta, búsqueda, consulta y actualización de socios;
- listado paginado en servidor con total real, selección de 25/50/100 socios
  por página y búsqueda en todos los registros visibles de la sucursal;
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
- V93 agrega reglas por organización para inscripción y baja automática. La
  baja es `INACTIVE`, conserva el historial financiero/auditable y registra
  fecha y motivo; los expedientes dados de baja automáticamente pueden volver a
  activarse al identificarse o registrarse en el kiosco.
- un socio sin plan puede verificarse desde el kiosco de su sucursal de alta;
  ya no se exige una membresía histórica para encontrarlo.

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

Paginación: `ClientPaginationIntegrationTests` recorre 127 socios y comprueba
aislamiento por organización/sucursal; `ClientPaginationControllerTests` y
`ClientPaginationServiceTests` cubren contrato y autorización. La navegación,
respuestas fuera de orden y vista móvil están en `e2e/pagination.spec.ts`.
Contrato y límites: [paginación de listados](../../docs/operations/list-pagination.md).

V93 se cubre con `ClientLifecycleServiceTests`,
`ClientPaginationIntegrationTests` y la suite de migraciones PostgreSQL.
