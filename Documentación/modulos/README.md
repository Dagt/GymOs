# Estado por módulos

Esta carpeta es la fuente canónica del estado técnico y funcional de cada módulo.
Cada archivo debe actualizarse en el mismo commit que cambie su comportamiento,
contratos, pruebas o deuda.

Reglas:

1. Describir únicamente código existente y verificado.
2. Separar claramente `Implementado`, `Pendiente` y `Fuera de alcance`.
3. Referenciar la deuda activa por su ID del documento
   `../28-registro-vivo-deuda-tecnica.md`.
4. Registrar la última validación reproducible.
5. No crear prompts, cierres o bitácoras adicionales por rama; Git conserva el
   historial.

Estados permitidos: `PARCIAL`, `FUNCIONAL`, `ESTABILIZADO`, `PLANIFICADO` y
`NO AUDITADO`.

## Índice

| Archivo | Módulo backend | Superficie principal |
|---|---|---|
| `01-organizations.md` | `organizations` | Gimnasios, sucursales y equipo |
| `02-identity-security.md` | `identity`, `security` | Sesión, usuarios, roles y permisos |
| `03-configuration.md` | `configuration` | Marca y configuración tenant |
| `04-onboarding.md` | `onboarding` | Alta inicial de gimnasio |
| `05-clients.md` | `clients` | CRM, socios e importación |
| `06-catalog.md` | `catalog` | Productos, planes y categorías |
| `07-sales.md` | `sales` | Ventas, pagos y devoluciones |
| `08-memberships.md` | `memberships` | Membresías, renovaciones y cambios |
| `09-benefits.md` | `benefits` | Beneficios y elegibilidad |
| `10-access.md` | `access` | Visitas y control de acceso |
| `11-selfservice.md` | `selfservice` | Kiosco y autoservicio |
| `12-bookings.md` | `bookings` | Recursos, disponibilidad y reservas |
| `13-inventory.md` | `inventory` | Existencias, movimientos y auditorías |
| `14-coaching.md` | `coaching` | Coaches, ejercicios y rutinas |
| `15-wellness.md` | `wellness` | Profesionales y servicios |
| `16-automations.md` | `automations` | Reglas y ejecución programada |
| `17-notifications.md` | `notifications` | Notificaciones y plantillas |
| `18-reporting.md` | `reporting` | Reportes transversales |
| `19-audit.md` | `audit` | Bitácora de acciones |
| `20-common-operations.md` | `common`, infraestructura | API común, base de datos y operación |
