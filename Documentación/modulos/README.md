# Estado por módulos

Corte: 23 de agosto de 2026

Esta carpeta documenta el comportamiento existente de cada módulo backend. Los estados permitidos son `PARCIAL`, `FUNCIONAL`, `ESTABILIZADO`, `PLANIFICADO` y `NO AUDITADO`.

Cada ficha debe separar lo implementado, pendiente y fuera de alcance; enlazar la deuda por ID desde `../28-registro-vivo-deuda-tecnica.md`; y registrar la última validación reproducible.

| Archivo | Módulo backend | Superficie principal |
|---|---|---|
| `01-organizations.md` | `organizations` | Gimnasios, sucursales y equipo |
| `02-identity-security.md` | `identity`, `security` | Sesión, usuarios, roles y permisos |
| `03-configuration.md` | `configuration` | Marca y configuración tenant |
| `04-onboarding.md` | `onboarding` | Alta inicial de gimnasio |
| `05-clients.md` | `clients` | CRM, socios e importación |
| `06-catalog.md` | `catalog` | Productos, planes y categorías |
| `07-sales.md` | `sales` | Ventas, pagos y devoluciones |
| `08-memberships.md` | `memberships` | Membresías, periodos, renovaciones y cambios |
| `09-benefits.md` | `benefits`, `eligibility` | Beneficios y elegibilidad transversal |
| `10-access.md` | `access` | Elegibilidad, control plane físico y Connector API |
| `11-selfservice.md` | `selfservice` | Kiosco y autoservicio |
| `12-bookings.md` | `bookings` | Recursos, disponibilidad y reservas |
| `13-inventory.md` | `inventory` | Existencias, movimientos y auditorías |
| `14-coaching.md` | `coaching` | Coaches, ejercicios y rutinas |
| `15-wellness.md` | `wellness` | Profesionales y servicios |
| `16-automations.md` | `automations` | Reglas, eventos, inbox durable, actividad/problemas y prueba manual |
| `17-notifications.md` | `notifications` | Cola durable, preferencias, branding y entrega ZeptoMail/SMTP |
| `18-reporting.md` | `reporting` | Reportes transversales |
| `19-audit.md` | `audit` | Bitácora de acciones |
| `20-common-operations.md` | `common` e infraestructura | API común, datos y operación |
| `21-payments.md` | `payments` | Stripe, Mercado Pago, webhooks y recurrencia |
| `22-platform-billing.md` | `platformadmin`, `platformbilling` | Cobranza SaaS manual, cartera y contratos por sucursal |

Para el cierre de Automatizaciones/email consultar además:

- `../31-diseño-automatizaciones-kiosco.md`;
- `../../docs/operations/email-delivery.md`;
- `../../docs/operations/zeptomail-email.md`;
- `../../docs/testing/automations-kiosk-acceptance.md`.

Para el control físico consultar además:

- `../../docs/operations/physical-access-connector.md`;
- `../../docs/operations/access-device-troubleshooting.md`.

> **Adenda 29/10/2026:** El Connector V104 sólo está en rama experimental; no se clasifica como integrado a main. Catálogo de planificación y QA en [documentos 35–46](../README.md).
