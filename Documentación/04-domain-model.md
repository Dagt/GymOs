# 4. Modelo de Dominio

## 4.0 Organization

Responsabilidad: cliente comercial de GymOS y límite principal de aislamiento de datos.

Atributos:
- id
- legalName
- tradeName
- taxId
- timezone
- defaultCurrency
- status
- contactEmail
- contactPhone
- createdAt
- updatedAt

Invariantes:
- Una operación de negocio siempre pertenece a una organización.
- Los datos de una organización no pueden mezclarse con otra sin autorización explícita de plataforma.
- Las configuraciones globales de negocio se resuelven primero por organización.

Eventos:
- OrganizationRegistered
- OrganizationUpdated
- OrganizationActivated
- OrganizationSuspended

## 4.0.1 Branch

Responsabilidad: sucursal física u operativa dentro de una organización.

Atributos:
- id
- organizationId
- name
- code
- address
- timezone
- status
- phone
- createdAt
- updatedAt

Invariantes:
- Una sucursal siempre pertenece a una organización.
- El código de sucursal es único dentro de la organización.
- Las operaciones físicas como ventas, accesos, inventario y reservas deben asociarse a sucursal cuando aplique.

Eventos:
- BranchRegistered
- BranchUpdated
- BranchActivated
- BranchSuspended

## 4.1 Person

Responsabilidad: fuente única de identidad personal.

Atributos:
- id
- organizationId
- firstName
- lastName
- birthDate
- gender
- primaryPhone
- email
- photoReference
- emergencyContact
- status
- createdAt
- updatedAt

Invariantes:
- No se elimina físicamente.
- El identificador es inmutable.
- El correo y teléfono se validan.
- Los datos personales sólo viven aquí.

Eventos:
- PersonRegistered
- PersonUpdated
- PersonPhotoChanged
- PersonDeactivated

## 4.2 User

Responsabilidad: identidad autenticable.

Estados:
- PendingActivation
- Active
- Locked
- Disabled

Relación:
- Un User referencia a una Person.

Eventos:
- UserCreated
- LoginSucceeded
- LoginFailed
- UserLocked
- UserUnlocked
- PasswordChanged
- UserRolesChanged

## 4.3 Client

Responsabilidad: relación comercial entre persona y organización.

Estados:
- Prospect
- Visitor
- Active
- Expired
- Suspended
- Inactive

Invariante:
- Una persona sólo puede tener un Client por organización.

## 4.4 Product

Todo lo vendible es un Product.

Tipos:
- MembershipPlan
- VisitPass
- PhysicalProduct
- ConsumableService
- ReservableService
- ProfessionalService
- WorkoutService
- GenericService

Atributos:
- id
- sku
- name
- description
- type
- basePrice
- currency
- taxCategory
- active
- inventoryTracked
- behaviorKey

## 4.5 Plan

Define la oferta de acceso.

Atributos:
- id
- name
- durationUnit
- durationValue
- basePrice
- accessPolicy
- reservableBenefits
- autoRenewAllowed
- evidenceRequirement
- active

Regla:
- Cambiar un plan no modifica membresías históricas.

## 4.6 Membership

Estados:
- PendingPayment
- Active
- GracePeriod
- Expired
- Cancelled

Invariantes:
- No se activa sin pago completo.
- No hay pagos parciales.
- Una renovación extiende desde la fecha final vigente.
- No se congela.
- La cancelación es auditada.

Eventos:
- MembershipCreated
- MembershipActivated
- MembershipRenewed
- MembershipExpired
- MembershipCancelled

## 4.7 Benefit

Tipos:
- PercentageDiscount
- FixedDiscount
- PriceOverride
- AccessBenefit

Atributos:
- eligibilityRule
- evidenceRequired
- validFrom
- validUntil
- revalidationPeriod
- active

## 4.8 Sale

Estados:
- Draft
- PendingPayment
- Paid
- Cancelled
- Refunded

Atributos:
- clientId
- items
- subtotal
- discounts
- taxes
- total
- payments

Invariantes:
- Una venta pagada no se modifica.
- La suma de pagos debe cubrir el total.
- Los efectos de negocio ocurren después de PaymentCompleted.

## 4.9 Payment

Métodos:
- Cash
- BankTransfer
- Card
- OnlineGateway

Estados:
- Pending
- UnderReview
- Authorized
- Completed
- Rejected
- Refunded

## 4.10 Resource

Representa cualquier recurso reservable.

Ejemplos:
- sauna
- tina fría
- sala
- consultorio
- clase
- profesional

Atributos:
- capacity
- slotDuration
- schedule
- cancellationPolicy
- advanceBookingPolicy

## 4.11 Booking

Estados:
- Reserved
- Confirmed
- CheckedIn
- Completed
- Cancelled
- NoShow

Invariantes:
- No traslapes incompatibles.
- El cliente debe cumplir reglas de acceso o elegibilidad.
- La capacidad no se excede.

## 4.12 AccessAttempt

Resultado:
- Granted
- Denied
- ManualOverride
- TemporaryAccess

Motivos:
- MembershipActive
- MembershipExpired
- MembershipMissing
- ManualOverride
- DeviceFailure
- PolicyRestriction

## 4.13 InventoryItem

Atributos:
- productId
- quantityOnHand
- minimumThreshold
- averageCost
- lastMovementAt

Movimientos:
- Purchase
- Sale
- Adjustment
- Waste
- Return
- TransferFuture

## 4.14 Workout

Atributos:
- coachId
- clientId
- status
- currentVersion

Cada edición crea WorkoutVersion.

## 4.15 ProfessionalService

Atributos:
- professionalId
- serviceType
- duration
- price
- schedule
- reservableResourceId

## 4.16 Automation

Atributos:
- triggerEvent
- conditionExpression
- actionType
- actionConfiguration
- active
- retryPolicy

## 4.17 Eventos versionados implementados

Eventos de ventas:
- `SaleCreatedV1`: venta creada por POS o flujo de membresia.
- `PaymentRecordedV1`: pago manual registrado.
- `PaymentCompletedV1`: venta cubierta por pagos completados; dispara efectos de negocio.
- `PaymentRejectedV1`: pago rechazado con razon.

Eventos de membresias:
- `MembershipActivatedV1`: membresia activada por venta pagada.
- `MembershipRenewedV1`: renovacion acumulativa procesada.
- `MembershipPlanChangedV1`: membresia anterior cancelada y nuevo plan activado.

Reglas reforzadas:
- Una membresia solo se activa desde pago completado.
- Reprocesar `PaymentCompletedV1` no duplica efectos porque se registra `processed_domain_event`.
- Un cambio al mismo plan es invalido.
- Una membresia cancelada no puede renovarse.
- El kiosco opera con sesion temporal, token aleatorio y origen auditado `SELF_SERVICE_KIOSK`.
