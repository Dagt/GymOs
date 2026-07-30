# Socios y CRM

Estado: `FUNCIONAL`

## Implementado

- alta, búsqueda, consulta y actualización de socios;
- información personal y contacto;
- membresía inicial y pago de entrada;
- importación de socios;
- detalle frontend y operación por sucursal.
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

Última evidencia: `DatabaseMigrationTests` sobre PostgreSQL limpio y E2E de
botón de alta visible para gerencia y recepción, 30-jul-2026.
