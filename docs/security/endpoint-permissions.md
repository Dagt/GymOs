# Inventario de endpoints y permisos efectivos de GymFort

Base auditada `58a5d4d4b24bcc598059b05a4ac70d11a2fe937b` con el PR #65. Inventario estático de las 298 operaciones de los 39 controladores Spring y del primer matcher aplicable en `SecurityConfig`. Las comprobaciones de dominio pueden limitar aún más una autoridad HTTP. El alcance se clasifica por el dato principal de la operación. Las excepciones y la evidencia de ejecución se mantienen en la [matriz de aislamiento](multi-tenant-verification.md).

`SecurityAuthorizationTests.documentedOperationsMatchSpringRoutingAndEffectiveAuthorities` compara estas filas con el registro real de rutas y el autorizador de Spring. Comprueba solicitudes anónimas, sesiones sin permisos y cada autoridad individual del inventario. La prueba verifica la capa HTTP de autorización. La matriz de aislamiento indica qué operaciones también tienen prueba de pertenencia con PostgreSQL.

`hasAnyAuthority` se representa con «o». «Credencial del protocolo» indica que el filtro de JWT de personal no exige autoridad para la ruta y que la autenticación específica se hace en el flujo. Las operaciones de sistema y marca pública son deliberadamente públicas. La regla final deniega las rutas sin matcher.

| Código | Alcance |
| --- | --- |
| A | Plataforma y `platform.admin` efectivo. Una sesión de soporte carece de autoridades de plataforma. |
| S | Sesión y transición de contexto autenticada, o credencial de autenticación propia del endpoint. |
| O | Organización del principal, o ámbito de plataforma explícito cuando el servicio lo permite. |
| B | Organización y sucursal activa. |
| V | Visibilidad del socio de la sede con excepciones de cobertura de membresía. |
| M | Transferencia entre sucursales del mismo tenant, con origen/destino según operación. |
| R | Asignación de sucursales, destinatario, propiedad del recurso o vínculo entre recursos de un mismo socio. |
| T | Identidad tenant y permisos operativos, con controles adicionales en el servicio. |
| C | Activación con código o credencial del conector vinculada a su organización y sucursal. |
| W | Protocolo del proveedor, firma de webhook o estado OAuth de un solo uso. |
| K | Credencial de un solo uso o sesión kiosco por UUID y token, con socio verificado en las operaciones que lo requieren. |
| F | Archivo asociado al recurso autorizado. |
| P | Público intencional. |

## access

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/access/devices` | `access.read` | B | `AccessController` |
| `POST /api/v1/access/devices` | `access.physical.manage` | B | `AccessController` |
| `GET /api/v1/access/devices/{deviceId}/benefits` | `access.read` | B | `AccessController` |
| `GET /api/v1/access/logs` | `access.read` | B | `AccessController` |
| `GET /api/v1/access/visit-passes` | `visit.read` | B | `AccessController` |
| `GET /api/v1/access/clients/{clientId}/effective-benefits` | `access.read` o `client.read` o `membership.read` o `benefit.read` | B | `AccessController` |
| `GET /api/v1/access/physical-integration` | `access.read` | B | `AccessController` |
| `PUT /api/v1/access/physical-integration` | `access.physical.manage` | B | `AccessController` |
| `GET /api/v1/access/physical-integration/clients/{clientId}` | `access.read` | B | `AccessController` |
| `PUT /api/v1/access/physical-integration/clients/{clientId}/link` | `access.physical.manage` | B | `AccessController` |
| `POST /api/v1/access/physical-integration/clients/{clientId}/synchronize` | `access.physical.manage` | B | `AccessController` |
| `POST /api/v1/access/physical-integration/reconcile` | `access.physical.manage` | B | `AccessController` |
| `PUT /api/v1/access/physical-integration/devices/{deviceId}/link` | `access.physical.manage` | B | `AccessController` |
| `DELETE /api/v1/access/physical-integration/devices/{deviceId}/link` | `access.physical.manage` | B | `AccessController` |
| `POST /api/v1/access/physical-integration/connector/activation` | `access.physical.manage` | B | `AccessController` |
| `POST /api/v1/access/physical-integration/connector/revoke` | `access.physical.manage` | B | `AccessController` |
| `GET /api/v1/access/physical-integration/candidates` | `access.physical.manage` | B | `AccessController` |
| `PUT /api/v1/access/physical-integration/candidates/{candidateId}/match` | `access.physical.manage` | B | `AccessController` |
| `POST /api/v1/access/physical-integration/candidates/{candidateId}/reject` | `access.physical.manage` | B | `AccessController` |
| `PUT /api/v1/access/devices/{deviceId}/benefits` | `access.physical.manage` | B | `AccessController` |
| `POST /api/v1/access/requests` | `access.manage` | B | `AccessController` |
| `POST /api/v1/access/commands/open` | `access.manual.open` | B | `AccessController` |
| `POST /api/v1/access/commands/temporary` | `access.manage` | B | `AccessController` |
| `POST /api/v1/access/visit-passes` | `visit.create` | B | `AccessController` |
| `POST /api/v1/access/visit-passes/{visitPassId}/consume` | `visit.consume` | B | `AccessController` |
| `GET /api/v1/access/manual-check-ins/clients` | `access.check-in` | B+T | `ManualCheckInController` |
| `GET /api/v1/access/manual-check-ins/clients/{clientId}` | `access.check-in` | B+T | `ManualCheckInController` |
| `GET /api/v1/access/manual-check-ins` | `access.check-in` | B+T | `ManualCheckInController` |
| `POST /api/v1/access/manual-check-ins` | `access.check-in` | B+T | `ManualCheckInController` |
| `POST /api/v1/access/manual-check-ins/{id}/reversal` | `access.check-in.reverse` | B+T | `ManualCheckInController` |
| `POST /api/v1/physical-connectors/activate` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/heartbeat` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/jobs/claim` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/jobs/{jobId}/complete` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/jobs/{jobId}/fail` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/jobs/{jobId}/lease` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/events` | Credencial del protocolo | C | `PhysicalConnectorController` |
| `POST /api/v1/physical-connectors/person-candidates` | Credencial del protocolo | C | `PhysicalConnectorController` |

## automations

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/automations` | `automation.read` | O/B | `AutomationController` |
| `POST /api/v1/automations` | `automation.manage` | O/B | `AutomationController` |
| `POST /api/v1/automations/{ruleId}/enable` | `automation.manage` | O/B | `AutomationController` |
| `POST /api/v1/automations/{ruleId}/disable` | `automation.manage` | O/B | `AutomationController` |
| `POST /api/v1/automations/{ruleId}/execute` | `automation.execute` o `automation.manage` | O/B | `AutomationController` |
| `GET /api/v1/automations/{ruleId}/executions` | `automation.read` | O/B | `AutomationController` |
| `GET /api/v1/automations/dead-letters` | `automation.read` | O/B | `AutomationController` |
| `GET /api/v1/automations/scheduler` | `automation.read` | O/B | `AutomationController` |
| `POST /api/v1/automations/scheduler/pause` | `automation.scheduler.manage` | O/B | `AutomationController` |
| `POST /api/v1/automations/scheduler/resume` | `automation.scheduler.manage` | O/B | `AutomationController` |
| `GET /api/v1/automations/operations` | `automation.read` | O/B | `AutomationController` |
| `POST /api/v1/automations/dead-letters/{deadLetterId}/retry` | `automation.manage` | O/B | `AutomationController` |
| `POST /api/v1/automations/dead-letters/{deadLetterId}/discard` | `automation.manage` | O/B | `AutomationController` |
| `POST /api/v1/automations/{ruleId}/test-email` | `automation.manage` | O/B | `AutomationRuleTestController` |

## benefits

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/benefits` | `benefit.read` o `client.read` o `membership.read` o `sale.create` | B | `BenefitController` |
| `GET /api/v1/catalog/access-benefits` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O | `BenefitController` |
| `GET /api/v1/clients/{clientId}/benefits` | `client.read` o `client.create` o `client.update` o `membership.read` o `membership.create` o `membership.update` | B | `BenefitController` |
| `POST /api/v1/benefits` | `benefit.manage` | B | `BenefitController` |
| `POST /api/v1/clients/{clientId}/benefits` | `client.benefit.assign` o `client.update` o `client.create` o `membership.update` o `sale.create` | B | `BenefitController` |
| `PUT /api/v1/benefits/{benefitId}` | `benefit.manage` | B | `BenefitController` |
| `POST /api/v1/client-benefits/{clientBenefitId}/verify` | `client.benefit.verify` | B | `BenefitController` |

## bookings

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/resources` | `resource.read` | B | `BookingController` |
| `POST /api/v1/resources` | `resource.manage` | B | `BookingController` |
| `PUT /api/v1/resources/{resourceId}` | `resource.manage` | B | `BookingController` |
| `DELETE /api/v1/resources/{resourceId}` | `resource.manage` | B | `BookingController` |
| `GET /api/v1/resources/{resourceId}/availability` | `resource.read` | B | `BookingController` |
| `GET /api/v1/bookings` | `booking.read` | B | `BookingController` |
| `GET /api/v1/bookings/{bookingId}` | `booking.read` | B | `BookingController` |
| `POST /api/v1/bookings` | `booking.manage` | B+V | `BookingController` |
| `POST /api/v1/bookings/{bookingId}/confirm` | `booking.manage` | B | `BookingController` |
| `POST /api/v1/bookings/{bookingId}/check-in` | `booking.manage` | B | `BookingController` |
| `POST /api/v1/bookings/{bookingId}/complete` | `booking.manage` | B | `BookingController` |
| `POST /api/v1/bookings/{bookingId}/cancel` | `booking.manage` | B | `BookingController` |
| `POST /api/v1/bookings/{bookingId}/payments` | `booking.payment.manage` | B+R | `BookingController` |

## catalog

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/catalog/categories` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O | `CatalogCategoryController` |
| `POST /api/v1/catalog/categories` | `catalog.category.manage` | O | `CatalogCategoryController` |
| `PATCH /api/v1/catalog/categories/{categoryId}` | `catalog.category.manage` | O | `CatalogCategoryController` |
| `DELETE /api/v1/catalog/categories/{categoryId}` | `catalog.category.manage` | O | `CatalogCategoryController` |
| `GET /api/v1/catalog/plans` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogPlanController` |
| `POST /api/v1/catalog/plans` | `catalog.create` | O/B | `CatalogPlanController` |
| `POST /api/v1/catalog/membership-plans` | `catalog.create` | O/B | `CatalogPlanController` |
| `PATCH /api/v1/catalog/plans/{planId}` | `catalog.update` | O/B | `CatalogPlanController` |
| `GET /api/v1/catalog/products` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductController` |
| `GET /api/v1/catalog/products/summary` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductController` |
| `GET /api/v1/catalog/products/by-barcode/{barcode}` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductController` |
| `GET /api/v1/catalog/products/{productId}` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductController` |
| `GET /api/v1/catalog/products/{productId}/detail` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductController` |
| `GET /api/v1/catalog/products/search` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductController` |
| `POST /api/v1/catalog/products` | `catalog.create` | O/B | `CatalogProductController` |
| `PUT /api/v1/catalog/products/{productId}` | `catalog.update` | O/B | `CatalogProductController` |
| `PATCH /api/v1/catalog/products/{productId}/status` | `catalog.status.manage` | O/B | `CatalogProductController` |
| `POST /api/v1/catalog/products/{productId}/activate` | `catalog.status.manage` | O/B | `CatalogProductController` |
| `POST /api/v1/catalog/products/{productId}/deactivate` | `catalog.status.manage` | O/B | `CatalogProductController` |
| `POST /api/v1/catalog/products/{productId}/restore` | `catalog.archive` | O/B | `CatalogProductController` |
| `DELETE /api/v1/catalog/products/{productId}` | `catalog.archive` | O/B | `CatalogProductController` |
| `GET /api/v1/catalog/product-families` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `CatalogProductFamilyController` |
| `POST /api/v1/catalog/product-families/variants` | `catalog.create` | O/B | `CatalogProductFamilyController` |
| `POST /api/v1/catalog/products/{productId}/image` | `catalog.image.manage` | B/F | `CatalogProductImageController` |
| `DELETE /api/v1/catalog/products/{productId}/image` | `catalog.image.manage` | B/F | `CatalogProductImageController` |
| `GET /api/v1/catalog/visit-package-plans` | `catalog.read` o `membership.read` o `sale.create` o `client.read` | O/B | `VisitPackagePlanController` |
| `POST /api/v1/catalog/visit-package-plans` | `catalog.create` | O/B | `VisitPackagePlanController` |
| `PATCH /api/v1/catalog/visit-package-plans/{planId}` | `catalog.update` | O/B | `VisitPackagePlanController` |

## clients

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/clients` | `client.read` o `client.create` o `client.update` o `membership.read` o `membership.create` o `membership.update` | B+V | `ClientController` |
| `POST /api/v1/clients` | `client.create` | B+V | `ClientController` |
| `GET /api/v1/clients/search` | `client.read` o `client.create` o `client.update` o `membership.read` o `membership.create` o `membership.update` | B+V | `ClientController` |
| `GET /api/v1/clients/{clientId}` | `client.read` o `client.create` o `client.update` o `membership.read` o `membership.create` o `membership.update` | B+V | `ClientController` |
| `PUT /api/v1/clients/{clientId}` | `client.update` | B+V | `ClientController` |
| `POST /api/v1/clients/imports` | `client.import` | B+V | `ClientController` |
| `GET /api/v1/client-lifecycle-settings` | `organization.update` o `sale.create` | B | `ClientLifecycleController` |
| `PUT /api/v1/client-lifecycle-settings` | `organization.update` | B | `ClientLifecycleController` |

## coaching

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/exercises` | `exercise.read` | B/R | `CoachingCatalogController` |
| `GET /api/v1/exercises/equipment-options` | `exercise.read` | B/R | `CoachingCatalogController` |
| `POST /api/v1/exercises` | `exercise.create` o `exercise.manage` | B/R | `CoachingCatalogController` |
| `GET /api/v1/exercises/{exerciseId}` | `exercise.read` | B/R | `CoachingCatalogController` |
| `PUT /api/v1/exercises/{exerciseId}` | `exercise.create` o `exercise.manage` | B/R | `CoachingCatalogController` |
| `POST /api/v1/exercises/{exerciseId}/aliases` | `exercise.create` o `exercise.manage` | B/R | `CoachingCatalogController` |
| `GET /api/v1/exercises/{exerciseId}/aliases` | `exercise.read` | B/R | `CoachingCatalogController` |
| `DELETE /api/v1/exercises/{exerciseId}/aliases` | `exercise.create` o `exercise.manage` | B/R | `CoachingCatalogController` |
| `POST /api/v1/exercises/{exerciseId}/media` | `exercise.create` o `exercise.manage` | B/R | `CoachingCatalogController` |
| `GET /api/v1/exercises/{exerciseId}/media` | `exercise.read` | B/R | `CoachingCatalogController` |
| `DELETE /api/v1/exercises/{exerciseId}/media` | `exercise.create` o `exercise.manage` | B/R | `CoachingCatalogController` |
| `PATCH /api/v1/exercises/{exerciseId}/status` | `exercise.manage` | B/R | `CoachingCatalogController` |
| `GET /api/v1/workout-assignments` | `workout.read.own` o `workout.read.all` | B/R | `CoachingCatalogController` |
| `GET /api/v1/workout-assignments/history` | `workout.read.own` o `workout.read.all` | B/R | `CoachingCatalogController` |
| `GET /api/v1/reports/coaches/workout-assignments/summary` | `coach.metrics.read` | B/R | `CoachingCatalogController` |
| `GET /api/v1/reports/coaches/workout-assignments/export` | `coach.metrics.read` | B/R | `CoachingCatalogController` |
| `GET /api/v1/reports/coaches/workout-assignments/trend` | `coach.metrics.read` | B/R | `CoachingCatalogController` |
| `GET /api/v1/workout-assignments/{assignmentId}/pdf` | `workout.pdf.download` | B/R | `CoachingCatalogController` |
| `GET /api/v1/coaches` | `coach.read` | B/R | `CoachingController` |
| `POST /api/v1/coaches` | `coach.manage` | B/R | `CoachingController` |
| `GET /api/v1/coaches/profiles` | `coach.profile.manage` | B/R | `CoachingController` |
| `PUT /api/v1/coaches/profiles/{coachUserId}` | `coach.profile.manage` | B/R | `CoachingController` |
| `GET /api/v1/workouts` | `workout.read.own` o `workout.read.all` | B/R | `CoachingController` |
| `POST /api/v1/workouts` | `workout.manage.own` o `workout.manage.all` | B/R | `CoachingController` |
| `GET /api/v1/workouts/{workoutId}` | `workout.read.own` o `workout.read.all` | B/R | `CoachingController` |
| `PUT /api/v1/workouts/{workoutId}` | `workout.manage.own` o `workout.manage.all` | B/R | `CoachingController` |
| `GET /api/v1/workouts/{workoutId}/versions` | `workout.read.own` o `workout.read.all` | B/R | `CoachingController` |
| `GET /api/v1/workout-versions/{versionId}` | `workout.read.own` o `workout.read.all` | B/R | `CoachingController` |
| `GET /api/v1/workout-assignments/{assignmentId}` | `workout.read.own` o `workout.read.all` | B/R | `CoachingController` |
| `POST /api/v1/workouts/{workoutId}/versions` | `workout.manage.own` o `workout.manage.all` | B/R | `CoachingController` |
| `POST /api/v1/workouts/{workoutId}/assign` | `workout.manage.own` o `workout.manage.all` | B/R | `CoachingController` |
| `POST /api/v1/workout-assignments` | `workout.assign` | B/R | `CoachingController` |
| `POST /api/v1/workout-assignments/{assignmentId}/replace` | `workout.assign` | B/R | `CoachingController` |
| `POST /api/v1/workouts/{workoutId}/duplicate` | `workout.manage.own` o `workout.manage.all` | B/R | `CoachingController` |
| `POST /api/v1/workouts/{workoutId}/publish` | `workout.manage.own` o `workout.manage.all` | B/R | `CoachingController` |
| `PATCH /api/v1/workout-assignments/{assignmentId}/status` | `workout.assign` | B/R | `CoachingController` |
| `GET /api/v1/clients/{clientId}/workouts` | `workout.read.own` o `workout.read.all` | B/R | `CoachingController` |
| `GET /api/v1/reports/coaches` | `report.coaches.read` | B/R | `CoachingController` |

## common

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/system/info` | Público | P | `SystemController` |

## identity

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `POST /api/v1/users/internal/{userId}/password-recovery` | `user.manage` | O/R | `PasswordRecoveryController` |
| `POST /api/v1/auth/password-recovery/accept` | Credencial del protocolo | K | `PasswordRecoveryController` |
| `GET /api/v1/users/roles` | `user.read` | O/R | `UserAdministrationController` |
| `GET /api/v1/users/internal` | `user.read` | O/R | `UserAdministrationController` |
| `POST /api/v1/users/internal` | `user.manage` | O/R | `UserAdministrationController` |
| `PATCH /api/v1/users/internal/{userId}/status` | `user.manage` | O/R | `UserAdministrationController` |
| `PATCH /api/v1/users/internal/{userId}/role` | `user.manage` | O/R | `UserAdministrationController` |
| `POST /api/v1/auth/invitations/accept` | Credencial del protocolo | K | `UserInvitationController` |

## inventory

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/inventory` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/summary` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/branch-settings` | `inventory.read` | B | `InventoryController` |
| `PUT /api/v1/inventory/branch-settings` | `inventory.manage` | B | `InventoryController` |
| `GET /api/v1/inventory/search` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/{productId}` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/low-stock` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/movements` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/movements/search` | `inventory.read` | B | `InventoryController` |
| `POST /api/v1/inventory/movements` | `inventory.manage` | B | `InventoryController` |
| `PUT /api/v1/inventory/products/{productId}/settings` | `inventory.manage` | B | `InventoryController` |
| `GET /api/v1/inventory/physical-counts` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/physical-counts/{countId}` | `inventory.read` | B | `InventoryController` |
| `GET /api/v1/inventory/physical-counts/search` | `inventory.read` | B | `InventoryController` |
| `POST /api/v1/inventory/physical-counts` | `inventory.manage` | B | `InventoryController` |
| `PUT /api/v1/inventory/physical-counts/{countId}/items/{productId}` | `inventory.manage` | B | `InventoryController` |
| `POST /api/v1/inventory/physical-counts/{countId}/approve` | `inventory.manage` | B | `InventoryController` |
| `POST /api/v1/inventory/physical-counts/{countId}/cancel` | `inventory.manage` | B | `InventoryController` |
| `GET /api/v1/inventory/transfers` | `inventory.read` | B+M | `InventoryController` |
| `GET /api/v1/inventory/transfers/{transferId}` | `inventory.read` | B+M | `InventoryController` |
| `GET /api/v1/inventory/transfers/search` | `inventory.read` | B+M | `InventoryController` |
| `POST /api/v1/inventory/transfers` | `inventory.manage` | B+M | `InventoryController` |
| `POST /api/v1/inventory/transfers/{transferId}/ship` | `inventory.manage` | B+M | `InventoryController` |
| `POST /api/v1/inventory/transfers/{transferId}/receive` | `inventory.manage` | B+M | `InventoryController` |
| `POST /api/v1/inventory/transfers/{transferId}/cancel` | `inventory.manage` | B+M | `InventoryController` |

## memberships

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `POST /api/v1/membership-quotes` | `membership.read` o `membership.update` o `sale.create` | B+V | `MembershipController` |
| `POST /api/v1/membership-enrollments` | `membership.create` o `membership.update` o `sale.create` | B+V | `MembershipController` |
| `GET /api/v1/clients/{clientId}/memberships` | `client.read` o `client.create` o `client.update` o `membership.read` o `membership.create` o `membership.update` | B+V | `MembershipController` |
| `GET /api/v1/memberships/{membershipId}` | `membership.read` | B+V | `MembershipController` |
| `GET /api/v1/memberships/{membershipId}/periods` | `membership.read` | B+V | `MembershipController` |
| `POST /api/v1/memberships/{membershipId}/renew` | `membership.renew` o `membership.update` o `sale.create` | B+V | `MembershipController` |
| `POST /api/v1/memberships/{membershipId}/change-plan` | `membership.change-plan` o `membership.update` o `sale.create` | B+V | `MembershipController` |

## notifications

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/notifications` | `notification.read` | O/B | `NotificationController` |
| `POST /api/v1/notifications` | `notification.manage` | O/B | `NotificationController` |
| `GET /api/v1/notifications/email-configuration` | `notification.read` o `notification.settings.manage` | O/B | `NotificationController` |
| `PUT /api/v1/notifications/email-configuration` | `notification.settings.manage` | O/B | `NotificationController` |
| `POST /api/v1/notifications/email-configuration/test` | `notification.settings.manage` | O/B | `NotificationController` |
| `PUT /api/v1/notifications/email-preferences` | `client.update` o `notification.manage` o `notification.settings.manage` | O/B+V | `NotificationController` |
| `GET /api/v1/notifications/email-preferences` | `client.update` o `notification.manage` o `notification.settings.manage` | O/B+V | `NotificationController` |
| `POST /api/v1/notifications/{notificationId}/retry` | `notification.manage` | O/B | `NotificationController` |

## onboarding

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/onboarding/checklist` | `onboarding.manage` | O | `OnboardingController` |
| `POST /api/v1/onboarding/pilot-setup` | `onboarding.manage` | O | `OnboardingController` |

## organizations

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/organizations/current` | `organization.read` o `organization.update` | O | `OrganizationController` |
| `GET /api/v1/organizations` | `organization.read` | O | `OrganizationController` |
| `POST /api/v1/organizations` | `platform.admin` | A | `OrganizationController` |
| `GET /api/v1/organizations/current/branches` | `branch.read` o `organization.read` o `inventory.manage` o `inventory.transfer` o `catalog.read` | O/R | `OrganizationController` |
| `GET /api/v1/organizations/current/branches/all` | `branch.read` o `organization.read` o `inventory.manage` o `inventory.transfer` o `catalog.read` | O | `OrganizationController` |
| `GET /api/v1/organizations/{organizationId}/branches` | JWT autenticado | A/S | `OrganizationController` |
| `PUT /api/v1/organizations/current` | `organization.update` | O | `OrganizationController` |
| `POST /api/v1/organizations/current/branches` | `branch.create` | O | `OrganizationController` |
| `PUT /api/v1/organizations/current/branches/{branchId}` | `branch.update` | O/R | `OrganizationController` |
| `PATCH /api/v1/organizations/{organizationId}/status` | `platform.admin` | A | `OrganizationController` |
| `POST /api/v1/organizations/current/logo` | `organization.update` | O/F | `OrganizationLogoController` |
| `POST /api/v1/organizations/{organizationId}/logo` | `platform.admin` | A/F | `OrganizationLogoController` |
| `GET /api/v1/organizations/{organizationId}/logo` | Público | P/F | `OrganizationLogoController` |

## payments

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/payment-settings/history/mandates` | `payment.settings.read` o `payment.settings.manage` | O | `PaymentConfigurationController` |
| `GET /api/v1/payment-settings/history/recurring-charges` | `payment.settings.read` o `payment.settings.manage` | O | `PaymentConfigurationController` |
| `GET /api/v1/payment-settings/history/webhooks` | `payment.settings.read` o `payment.settings.manage` | O | `PaymentConfigurationController` |
| `GET /api/v1/payment-settings/operations` | `payment.settings.read` o `payment.settings.manage` | O | `PaymentConfigurationController` |
| `POST /api/v1/payment-settings/stripe/connect` | `payment.provider.stripe.manage` | O | `PaymentConfigurationController` |
| `POST /api/v1/payment-settings/stripe/refresh` | `payment.provider.stripe.manage` | O | `PaymentConfigurationController` |
| `POST /api/v1/payment-settings/mercado-pago/connect` | `payment.settings.manage` | O | `PaymentConfigurationController` |
| `GET /api/v1/payment-settings/mercado-pago/oauth/callback` | Credencial del protocolo | W | `PaymentConfigurationController` |
| `GET /api/v1/payment-settings/mercado-pago/terminals` | `payment.settings.manage` | O | `PaymentConfigurationController` |
| `PUT /api/v1/payment-settings/mercado-pago/terminal` | `payment.settings.manage` | B | `PaymentConfigurationController` |
| `GET /api/v1/payment-settings` | `payment.settings.read` o `payment.settings.manage` | B | `PaymentConfigurationController` |
| `PUT /api/v1/payment-settings` | `payment.settings.manage` | B | `PaymentConfigurationController` |
| `POST /api/v1/payment-webhooks/stripe` | Credencial del protocolo | W | `PaymentWebhookController` |
| `POST /api/v1/payment-webhooks/mercado-pago` | Credencial del protocolo | W | `PaymentWebhookController` |

## platformadmin

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/platform/customers` | `platform.admin` | A | `PlatformCustomerController` |
| `POST /api/v1/platform/customers` | `platform.admin` | A | `PlatformCustomerController` |
| `POST /api/v1/platform/customers/{organizationId}/owner-invitation` | `platform.admin` | A | `PlatformCustomerController` |
| `PUT /api/v1/platform/customers/{organizationId}` | `platform.admin` | A | `PlatformCustomerController` |

## platformbilling

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/platform/billing/overview` | `platform.admin` | A | `PlatformBillingController` |
| `PUT /api/v1/platform/billing/branches/{branchId}` | `platform.admin` | A | `PlatformBillingController` |
| `GET /api/v1/platform/billing/branches/{branchId}/payments` | `platform.admin` | A | `PlatformBillingController` |
| `POST /api/v1/platform/billing/branches/{branchId}/payments` | `platform.admin` | A | `PlatformBillingController` |

## reporting

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/reports/owner-dashboard` | `report.owner.read` | B | `OwnerDashboardController` |

## sales

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `POST /api/v1/sales/payments/{paymentId}/mercado-pago-order` | `sale.create` | B | `SalesController` |
| `GET /api/v1/sales` | `sale.read` | B | `SalesController` |
| `GET /api/v1/sales/reports/summary` | `report.sales.read` | B | `SalesController` |
| `POST /api/v1/sales` | `sale.create` | B | `SalesController` |
| `GET /api/v1/sales/{saleId}` | `sale.read` | B | `SalesController` |
| `POST /api/v1/sales/{saleId}/payments` | `payment.manual.create` | B | `SalesController` |
| `POST /api/v1/sales/payments/{paymentId}/validate` | `payment.manual.validate` | B | `SalesController` |
| `POST /api/v1/sales/payments/{paymentId}/reject` | `payment.manual.reject` | B | `SalesController` |
| `POST /api/v1/sales/payments/{paymentId}/receipt` | `payment.receipt.upload` | B/F | `SalesController` |
| `POST /api/v1/sales/{saleId}/refunds` | `sale.refund` | B | `SalesController` |

## security

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `POST /api/v1/auth/login` | Credencial del protocolo | S | `AuthController` |
| `POST /api/v1/auth/refresh` | Credencial del protocolo | S | `AuthController` |
| `POST /api/v1/auth/logout` | Credencial del protocolo | S | `AuthController` |
| `GET /api/v1/auth/me` | JWT autenticado | S | `AuthController` |
| `POST /api/v1/auth/select-branch` | `branch.read` | S | `AuthController` |
| `POST /api/v1/auth/select-organization` | `platform.admin` | A/S | `AuthController` |
| `POST /api/v1/auth/exit-support` | JWT autenticado | S | `AuthController` |

## selfservice

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/kiosk-settings/branches` | `selfservice.settings.manage` | O/R | `KioskSettingsController` |
| `PUT /api/v1/kiosk-settings/branches/{branchId}` | `selfservice.settings.manage` | O/R | `KioskSettingsController` |
| `POST /api/v1/self-service/kiosk-sessions` | `selfservice.session.launch` | B+T | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/verify` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/registrations` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/summary` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/membership-options` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/membership-quotes/{planId}` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/visit-package-options` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/visit-package-quotes/{packagePlanId}` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/visit-package` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/visit-package-purchases` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/booking-resources` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/booking-availability` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/bookings` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/membership-renewals/{membershipId}` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/membership-enrollments` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/membership-change-requests/{membershipId}` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/autopay/{membershipId}/terms` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/autopay/{membershipId}` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/autopay/{membershipId}/setup` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/autopay/{membershipId}/revoke` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/end` | Credencial del protocolo | K | `SelfServiceController` |
| `POST /api/v1/self-service/sessions/{sessionId}/reset` | Credencial del protocolo | K | `SelfServiceController` |
| `PATCH /api/v1/self-service/sessions/{sessionId}/contact-data` | Credencial del protocolo | K | `SelfServiceController` |
| `GET /api/v1/self-service/sessions/{sessionId}/wellness/professionals` | Credencial del protocolo | K | `SelfServiceWellnessController` |
| `GET /api/v1/self-service/sessions/{sessionId}/wellness/professionals/{professionalId}/services` | Credencial del protocolo | K | `SelfServiceWellnessController` |
| `GET /api/v1/self-service/sessions/{sessionId}/wellness/professionals/{professionalId}/availability` | Credencial del protocolo | K | `SelfServiceWellnessController` |
| `POST /api/v1/self-service/sessions/{sessionId}/wellness/appointments` | Credencial del protocolo | K | `SelfServiceWellnessController` |

## visitpackages

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `POST /api/v1/visit-packages/quotes` | `membership.read` o `sale.create` | B+V | `VisitPackageController` |
| `POST /api/v1/visit-packages/purchases` | `sale.create` | B+V | `VisitPackageController` |
| `GET /api/v1/clients/{clientId}/visit-package` | `client.read` o `membership.read` o `sale.create` | B+V | `VisitPackageController` |
| `GET /api/v1/clients/{clientId}/visit-packages` | `client.read` o `membership.read` o `sale.create` | B+V | `VisitPackageController` |

## wellness

| Operación | Autoridad HTTP | Alcance | Controlador |
| --- | --- | --- | --- |
| `GET /api/v1/professionals` | `professional.read` o `booking.read` | B | `WellnessController` |
| `POST /api/v1/professionals` | `professional.manage` | B | `WellnessController` |
| `PUT /api/v1/professionals/{professionalId}` | `professional.manage` | B | `WellnessController` |
| `DELETE /api/v1/professionals/{professionalId}` | `professional.manage` | B | `WellnessController` |
| `GET /api/v1/professionals/{professionalId}/services` | `professional.read` o `booking.read` | B | `WellnessController` |
| `POST /api/v1/professionals/{professionalId}/services` | `professional.manage` | B | `WellnessController` |
| `PUT /api/v1/professionals/{professionalId}/services/{serviceId}` | `professional.manage` | B | `WellnessController` |
| `GET /api/v1/professionals/{professionalId}/availability` | `professional.read` o `booking.read` | B | `WellnessController` |
| `POST /api/v1/professionals/{professionalId}/availability` | `professional.manage` | B | `WellnessController` |
| `GET /api/v1/professionals/{professionalId}/availability-slots` | `professional.read` o `booking.read` | B | `WellnessController` |
| `POST /api/v1/wellness/appointments` | `professional.manage` o `booking.manage` | B+V | `WellnessController` |
| `GET /api/v1/wellness/appointments` | `professional.read` o `booking.read` | B | `WellnessController` |
| `POST /api/v1/wellness/appointments/{appointmentId}/confirm` | `professional.manage` o `booking.manage` | B | `WellnessController` |
| `POST /api/v1/wellness/appointments/{appointmentId}/complete` | `professional.manage` o `booking.manage` | B | `WellnessController` |
| `POST /api/v1/wellness/appointments/{appointmentId}/cancel` | `professional.manage` o `booking.manage` | B | `WellnessController` |
