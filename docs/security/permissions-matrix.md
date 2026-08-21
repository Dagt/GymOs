# Matriz de permisos

Estado: VIGENTE / COBERTURA PARCIAL
Fuente primaria: `backend/src/main/java/com/gymos/common/security/SecurityConfig.java`  
Última revisión: 21 de agosto de 2026

Esta matriz resume el permiso mínimo HTTP. Los servicios deben volver a validar
organización, sucursal, propiedad y estado del recurso; una coincidencia de ruta
no sustituye autorización de negocio.

## Rutas públicas endurecidas

| Ruta | Regla adicional |
|---|---|
| `/api/v1/auth/login`, `/api/v1/auth/refresh` | Credenciales/refresh válidos y rate limit aplicable |
| `/api/v1/self-service/**` excepto alta de sesión | Token temporal, sesión activa, tenant/sucursal y capacidad |
| `POST /api/v1/self-service/kiosk-sessions` | No es pública: `selfservice.session.launch` o `platform.admin` |
| `/api/v1/payment-webhooks/**` | Firma del proveedor, inbox durable, deduplicación y conciliación |
| Callback OAuth Mercado Pago | `state` de un solo uso y PKCE |
| `GET /api/v1/organizations/*/logo` | Sólo marca pública por UUID; sin datos tenant adicionales |
| Health/system/docs | Deben restringirse por perfil antes de producción |

## Familias internas

| Superficie | Lectura | Mutación/operación |
|---|---|---|
| Organización | `organization.read` | `organization.update`; alta/estado tenant: `platform.admin` |
| Sucursales | `branch.read` | `branch.create`, `branch.update` |
| Equipo | `user.read` | `user.manage` con filtro de roles/sucursales |
| Onboarding | `onboarding.manage` | `onboarding.manage` |
| Clientes | `client.read` | `client.create`, `client.update`, `client.import` |
| Catálogo | `catalog.read` y compatibilidades de consumo | `catalog.create`, `catalog.update`, `catalog.status.manage`, `catalog.archive`, `catalog.image.manage`, `catalog.category.manage` |
| Beneficios | `benefit.read` o lectores efectivos | `benefit.manage`, `client.benefit.assign`, `client.benefit.verify` |
| Membresías | `membership.read` | `membership.create/update/renew/change-plan` o `sale.create` según flujo |
| Ventas | `sale.read`, `report.sales.read` | `sale.create`, `sale.refund` |
| Pagos manuales | lectura por venta | `payment.manual.create/validate/reject`, `payment.receipt.upload` |
| Acceso/visitas | `access.read`, `visit.read` | `access.manage`, `visit.create`, `visit.consume` |
| Inventario | `inventory.read` | `inventory.manage` |
| Recursos/reservas | `resource.read`, `booking.read` | `resource.manage`, `booking.manage`, `booking.payment.manage` |
| Coaches | `coach.read`, `coach.metrics.read` | `coach.manage`, `coach.profile.manage` |
| Ejercicios/rutinas | `exercise.read`, `workout.read.own/all` | `exercise.create/manage`, `workout.manage.own/all`, `workout.assign`, `workout.pdf.download` |
| Wellness | `professional.read` o `booking.read` | `professional.manage` o `booking.manage` |
| Notificaciones | `notification.read` | `notification.manage`; SMTP tenant: `notification.settings.manage` |
| Automatizaciones | `automation.read` | `automation.manage`, `automation.execute`, `automation.scheduler.manage` |
| Reporte dueño | `report.owner.read` | No aplica |

## Separaciones críticas vigentes

| Acción | Permiso | Roles esperados |
|---|---|---|
| Abrir una sesión de kiosco | `selfservice.session.launch` | Recepción; soporte mediante `platform.admin` |
| Configurar capacidades del kiosco | `selfservice.settings.manage` | Dueño, gerente autorizado, soporte |
| Consultar cobros | `payment.settings.read` o `payment.settings.manage` | Roles autorizados |
| Activar/desactivar modalidades | `payment.settings.manage` | Dueño, gerente autorizado, soporte |
| Conectar/refrescar Stripe | `payment.provider.stripe.manage` | Dueño y soporte; no gerente |
| Configurar correo tenant | `notification.settings.manage` | Dueño, gerente autorizado, soporte |

V76 retira el lanzamiento del kiosco a Gerencia. V77 separa administrar la
cuenta Stripe de sus interruptores operativos. El frontend oculta acciones por
permiso, pero el backend es autoritativo.

## Reglas de rol relevantes

- Gerencia no crea/modifica dueño, gerente ni administrador de plataforma.
- Dueño y gerente sólo consultan métricas de coaches; no reciben por defecto las
  operaciones `workout.*` del Coach.
- Recepción puede operar POS, socios, reservas y kiosco según sus permisos, pero
  no administrar tenant/proveedores.
- Soporte usa `platform.admin`, contexto tenant explícito y auditoría
  `PLATFORM_SUPPORT`.

## Brechas de cobertura

1. Convertir cada familia en pruebas positivas y negativas por rol, tenant,
   sucursal y UUID ajeno (`GEN-001`).
2. Deshabilitar docs/actuator públicos en producción mediante perfil.
3. Verificar que cada mutación sensible escriba `audit_log` (`AUD-001`).
4. Mantener regresión explícita de V76/V77 y de planes multi-sucursal.
