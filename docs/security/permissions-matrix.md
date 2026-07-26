# GymOS Permissions Matrix

Estado: VIGENTE / COBERTURA PARCIAL
Fuente primaria: `backend/src/main/java/com/gymos/common/security/SecurityConfig.java`  
Última revisión documental: 2026-07-26

Esta matriz documenta el permiso mínimo esperado por endpoint interno. Debe mantenerse alineada con pruebas de autorización. Los endpoints públicos no sustituyen validaciones de negocio ni rate limiting.

## Endpoints públicos

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/actuator/health/**` | ops | Público | No | No | No | Pendiente hardening prod |
| GET | `/api/v1/system/**` | common | Público | No | No | No | Pendiente |
| POST | `/api/v1/auth/login` | security | Público | No | No | No | Pendiente |
| POST | `/api/v1/auth/refresh` | security | Público | No | No | No | Pendiente |
| ANY | `/api/v1/self-service/**` | selfservice | Público endurecido | Sí, por terminal | Sí, por terminal | Sí | `selfServiceSessionCreationIsPublic` parcial |
| GET | `/api/v1/openapi/**` | docs | Público local | No | No | No | Pendiente perfil prod |
| GET | `/api/v1/docs/**` | docs | Público local | No | No | No | Pendiente perfil prod |
| GET | `/swagger-ui/**` | docs | Público local | No | No | No | Pendiente perfil prod |

## Autenticación y organización

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/v1/auth/select-branch` | security | `branch.read` | Sí | Sí | Recomendado | `AuthServiceTests.selectingBranchWithoutUserAccessIsDenied` |
| GET | `/api/v1/organizations/current` | organizations | `organization.read` | Sí | No | No | Pendiente |
| PUT | `/api/v1/organizations/current` | organizations | `organization.update` | Sí | No | Sí | Pendiente |
| GET | `/api/v1/organizations/current/branches` | organizations | `branch.read` | Sí | No | No | Pendiente |
| ANY | `/api/v1/organizations/current/branches/*` | organizations | `branch.update` | Sí | Sí | Sí | Pendiente |

## Onboarding

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/onboarding/**` | onboarding | `onboarding.manage` | Sí | No | No | `onboardingRequiresOnboardingPermission` negativo |
| POST | `/api/v1/onboarding/**` | onboarding | `onboarding.manage` | Sí | Sí cuando aplica | Sí | `onboardingRequiresOnboardingPermission` negativo |

## Clientes

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/clients/**` | clients | `client.read` | Sí | Según consulta | No | Pendiente |
| POST | `/api/v1/clients` | clients | `client.create` | Sí | Sí | Sí | Pendiente |
| PUT | `/api/v1/clients/**` | clients | `client.update` | Sí | Según recurso | Sí | Pendiente |
| POST | `/api/v1/clients/imports` | clients | `client.import` | Sí | Opcional | Sí | `clientImportRequiresImportPermission` negativo |
| POST | `/api/v1/clients/*/benefits` | benefits | `client.benefit.assign` | Sí | Según cliente | Sí | `clientBenefitAssignmentRequiresAssignPermission` negativo |
| POST | `/api/v1/client-benefits/*/verify` | benefits | `client.benefit.verify` | Sí | Según beneficio | Sí | `clientBenefitVerificationRequiresVerifyPermission` negativo |
| GET | `/api/v1/clients/*/workouts` | coaching | `workout.read.own/all` | Sí | Sí; coach propio | No | `CoachingServiceSecurityTests` |

## Catálogo, beneficios y membresías

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/catalog/**` | catalog | `catalog.read` | Sí | No | No | Pendiente |
| POST | `/api/v1/catalog/**` | catalog | `catalog.manage` | Sí | No | Sí | Pendiente |
| GET | `/api/v1/benefits/**` | benefits | `benefit.read` | Sí | No | No | Pendiente |
| POST | `/api/v1/benefits/**` | benefits | `benefit.manage` | Sí | No | Sí | `benefitCreationRequiresManagePermission` negativo |
| GET | `/api/v1/memberships/**` | memberships | `membership.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/memberships/*/renew` | memberships | `membership.renew` | Sí | Sí | Sí | Pendiente |
| POST | `/api/v1/memberships/*/change-plan` | memberships | `membership.change-plan` | Sí | Sí | Sí | Pendiente |

## Ventas, pagos y reembolsos

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/sales/reports/**` | sales/reporting | `report.sales.read` | Sí | Sí | No | `salesReportRequiresReportPermission` negativo |
| GET | `/api/v1/sales/**` | sales | `sale.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/sales` | sales | `sale.create` | Sí | Sí | Sí | `saleCreationRequiresSaleCreatePermission` negativo |
| POST | `/api/v1/sales/*/refunds` | sales | `sale.refund` | Sí | Sí | Sí | `saleRefundRequiresRefundPermission` negativo |
| POST | `/api/v1/sales/*/payments` | sales | `payment.manual.create` | Sí | Sí | Sí | Pendiente |
| POST | `/api/v1/sales/payments/*/validate` | sales | `payment.manual.validate` | Sí | Sí | Sí | Pendiente |
| POST | `/api/v1/sales/payments/*/reject` | sales | `payment.manual.reject` | Sí | Sí | Sí | Pendiente |
| POST | `/api/v1/sales/payments/*/receipt` | sales/storage | `payment.receipt.upload` | Sí | Sí | Sí | Pendiente |

## Accesos y visitas

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/access/visit-passes/**` | access | `visit.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/access/visit-passes` | access | `visit.create` | Sí | Sí | Sí | `visitPassCreationRequiresVisitCreatePermission` negativo |
| POST | `/api/v1/access/visit-passes/*/consume` | access | `visit.consume` | Sí | Sí | Sí | `visitPassConsumeRequiresVisitConsumePermission` negativo |
| GET | `/api/v1/access/**` | access | `access.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/access/**` | access | `access.manage` | Sí | Sí | Sí | Pendiente |

## Inventario y reservas

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/inventory/**` | inventory | `inventory.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/inventory/**` | inventory | `inventory.manage` | Sí | Sí | Sí | Pendiente |
| GET | `/api/v1/resources/**` | bookings | `resource.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/resources/**` | bookings | `resource.manage` | Sí | Sí | Sí | Pendiente |
| GET | `/api/v1/bookings/**` | bookings | `booking.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/bookings/**` | bookings | `booking.manage` | Sí | Sí | Sí | `bookingCreationRequiresManagePermission` negativo |
| POST | `/api/v1/bookings/*/payments` | bookings | `booking.payment.manage` | Sí | Sí | Sí | `bookingPaymentRequiresPaymentManagePermission` negativo |

## Coaching, wellness, notificaciones y automatizaciones

| Método | Endpoint | Módulo | Permiso | Organización | Sucursal | Auditoría | Prueba actual |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/v1/coaches/**` | coaching | `coach.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/coaches/**` | coaching | `coach.manage` | Sí | Sí | Sí | `coachCreationRequiresManagePermission` negativo |
| GET | `/api/v1/workouts/**` | coaching | `workout.read.own/all` | Sí | Sí; coach propio/global | No | `CoachingServiceSecurityTests`; permiso legacy negativo |
| POST/PUT | `/api/v1/workouts/**` | coaching | `workout.manage.own/all` | Sí | Sí; coach propio/global | Sí | `CoachingServiceSecurityTests`; `workoutAssignmentRequiresManagePermission` |
| GET | `/api/v1/reports/coaches` | coaching/reporting | `report.coaches.read` | Sí | Sí | No | Pendiente |
| GET | `/api/v1/exercises/**` | coaching | `exercise.read` | Sí | Según origen | No | Implementado; negativos completos pendientes |
| POST/PUT/DELETE | `/api/v1/exercises/**` | coaching | `exercise.create` / `exercise.manage` | Sí | Propio/organización | Sí | SYSTEM inmutable, propiedad y medios probados |
| PATCH | `/api/v1/exercises/**` | coaching | `exercise.manage` | Sí | Organización | Sí | `exerciseMutationRequiresCreateOrManagePermission` |
| GET | `/api/v1/workout-versions/**` | coaching | `workout.read.own/all` | Sí | Sí; coach propio/global | No | Filtro de sucursal y propiedad |
| POST | `/api/v1/workout-assignments` | coaching | `workout.assign` | Sí | Sí | Sí | Implementado con idempotencia |
| PATCH | `/api/v1/workout-assignments/**` | coaching | `workout.assign` | Sí | Sí; coach propio/global | Sí | Motivo obligatorio y negativo de permiso |
| POST | `/api/v1/workout-assignments/*/replace` | coaching | `workout.assign` | Sí | Sí; coach propio/global | Sí | E2E de reemplazo e idempotencia |
| GET | `/api/v1/workout-assignments/*/pdf` | coaching | `workout.pdf.download` | Sí | Sí; coach propio/global | No | PDFBox y filtro de propiedad |
| GET | `/api/v1/reports/coaches/workout-assignments/**` | coaching/reporting | `coach.metrics.read` | Sí | Sí; coach propio/global | No | Comparación, filtro, paginación y E2E |
| GET | `/api/v1/professionals/**` | wellness | `professional.read` | Sí | Sí | No | Pendiente |
| POST | `/api/v1/professionals/**` | wellness | `professional.manage` | Sí | Sí | Sí | `professionalCreationRequiresManagePermission` negativo |
| GET | `/api/v1/notifications/**` | notifications | `notification.read` | Sí | No | No | Pendiente |
| POST | `/api/v1/notifications/**` | notifications | `notification.manage` | Sí | No | Sí | `notificationCreationRequiresManagePermission` negativo |
| GET | `/api/v1/automations/**` | automations | `automation.read` | Sí | No | No | Pendiente |
| POST | `/api/v1/automations/**` | automations | `automation.manage` | Sí | No | Sí | `automationCreationRequiresManagePermission` negativo |
| GET | `/api/v1/reports/owner-dashboard` | reporting | `report.owner.read` | Sí | Opcional | No | Pendiente |

## Brechas inmediatas

1. Convertir cada fila `Pendiente` en prueba MVC o integración.
2. Dividir endpoints públicos por perfil: OpenAPI/Swagger no deben quedar públicos en producción sin decisión explícita.
3. Sustituir los permisos amplios de coaching por lectura, gestión propia/global,
   asignación, PDF y métricas; coach solo ve sus rutinas/asignaciones y dueño/gerente
   quedan limitados por organización y sucursales autorizadas.
4. Agregar prueba negativa de selección de sucursal ajena.
5. Agregar prueba negativa de UUID ajeno por módulo crítico.
6. Verificar que toda operación marcada con auditoría escriba realmente `audit_log`.
