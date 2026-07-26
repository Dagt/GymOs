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
- GenericService

`WorkoutService` no forma parte del catálogo comercial de V1. Una rutina de coach es
un agregado de `coaching`, no un producto, servicio vendible ni concepto de caja.

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
- Una venta pagada con productos fisicos descuenta inventario mediante movimientos `Sale` idempotentes.

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
- consultorio de fisioterapia/masajes
- cancha
- clase de grupo

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
- La reserva debe estar dentro del horario operativo del recurso.
- Check-in solo aplica a reservas confirmadas.
- Completion solo aplica a reservas con check-in.

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

Invariantes:
- La existencia nunca puede quedar negativa.
- Un movimiento automatico de venta se identifica por organizacion, sucursal, venta y producto.
- Reprocesar el mismo pago completado no puede descontar dos veces el mismo producto.

## 4.14 Coaching, ejercicios y rutinas

- `CoachProfile` extiende la identidad interna y relaciona al usuario Coach con
  organización, sucursales, especialidades y estado.
- `Exercise` representa una opción reutilizable del catálogo global o del gimnasio,
  con aliases, músculos, patrón, tipo, equipo, instrucciones, medios y origen.
- `Workout` es una plantilla reutilizable; no contiene al socio.
- `WorkoutVersion` es una instantánea publicada, numerada e inmutable con sesiones,
  bloques y prescripciones normalizadas. Puede conservar JSON de compatibilidad,
  pero no depender únicamente de él.
- `WorkoutAssignment` vincula coach, socio, sucursal y versión. Registra fecha,
  vigencia, estado, notas, actor e idempotencia y alimenta las métricas semanales.

Invariantes:

- Una asignación siempre apunta a una versión publicada e inmutable.
- Publicar otra versión no cambia asignaciones ni PDF anteriores.
- Solo socios activos de la misma organización y dentro del alcance de sucursal pueden recibir rutinas.
- Un coach inactivo no crea nuevas asignaciones; su histórico permanece.
- Reintentar la misma asignación no duplica registros ni métricas.
- Crear, publicar, asignar o imprimir una rutina no crea ventas, pagos, comisiones, ingresos ni movimientos de caja.

El diseño completo está en `25-plan-accion-coaches-rutinas.md`.

## 4.15 Professional, ProfessionalService & WellnessAppointment

Atributos:
- professionalId
- officeLocation
- serviceType
- duration
- price
- schedule

Implementación:
- `Professional` pertenece a organización y sucursal, tiene tipo profesional (Nutriólogo, Fisioterapeuta, Quiropráctico, Bienestar), datos de contacto, biografía, lugar o consultorio de atención (`officeLocation`) y estado.
- `ProfessionalService` define nombre, tipo, duración en minutos, precio, moneda y estado.
- `ProfessionalAvailability` define disponibilidad semanal por día de la semana y rango horario (`opensAt`, `closesAt`).
- `WellnessAppointment` representa la cita de salud/bienestar entre un socio (`clientId`) y un profesional (`professionalId`) para un servicio (`serviceId`).
  - Estados: `Requested` (Solicitada), `Confirmed` (Confirmada), `Completed` (Completada/Atendida), `Cancelled` (Cancelada), `Rejected` (Rechazada).
  - Campos: `startsAt`, `endsAt`, `notes`, `cancellationReason`, `confirmedByUserId`, `confirmedAt`.

Invariantes:
- Las citas de bienestar son independientes de los recursos físicos del gimnasio (`Resource`).
- Solo profesionales activos pueden ofrecer servicios, recibir disponibilidades o agendar citas.
- La confirmación de una cita en estado `Requested` puede ser ejecutada por el propio profesional o por el usuario de Recepción cuando exista acuerdo telefónico u offline.
- No pueden existir traslapes de citas activas (`Requested` o `Confirmed`) para el mismo profesional en el mismo rango de tiempo.
- La duración del servicio debe estar entre 15 y 480 minutos.
- La disponibilidad debe cerrar después de abrir (`closesAt > opensAt`).

## 4.16 Automation

Atributos:
- triggerEvent
- conditionExpression
- actionType
- actionConfiguration
- active
- retryPolicy

Implementado en Hito 5:
- `AutomationRule` guarda evento, condicion JSON, accion, configuracion JSON, estado y limite de reintentos.
- `AutomationExecution` registra cada ejecucion y su resultado.
- La accion inicial soportada es `CREATE_NOTIFICATION`.
- `NotificationLog` registra canal, destinatario, asunto, cuerpo, estado, referencia y programacion.

Invariantes:
- Solo reglas `ENABLED` pueden ejecutarse.
- La configuracion de condicion y accion debe ser JSON valido.
- Las notificaciones quedan auditadas aunque el envio externo aun no este conectado.

## 4.17 OnboardingChecklist

Responsabilidad: medir si una organizacion/sucursal esta lista para operar un piloto vendible.

Atributos:
- organizationProfileComplete
- branchConfigured
- hasMembershipPlans
- hasRetailProducts
- hasKioskTerminal
- hasClients
- readyForPilot

Invariantes:
- El checklist se calcula por organizacion y sucursal activa.
- La preparacion administrativa externa no forma parte del calculo; se gestiona en Hito 7.

## 4.18 ClientImportBatch

Responsabilidad: representar una carga inicial de clientes desde CSV.

Atributos:
- fileName
- dryRun
- status
- totalRows
- createdCount
- skippedCount
- errorCount

Invariantes:
- Cada fila se clasifica como `VALID`, `CREATED`, `SKIPPED` o `ERROR`.
- `dryRun=true` nunca crea clientes.
- Duplicados por email o telefono se omiten y no detienen el lote.
- La importacion pertenece siempre a una organizacion.

## 4.19 Eventos versionados implementados

Eventos de ventas:
- `SaleCreatedV1`: venta creada por POS o flujo de membresia.
- `PaymentRecordedV1`: pago manual registrado.
- `PaymentCompletedV1`: venta cubierta por pagos completados; dispara efectos de negocio.
- `PaymentRejectedV1`: pago rechazado con razon.
- Paquete propietario: `com.gymos.sales.api.events`.

Eventos de membresias:
- `MembershipActivatedV1`: membresia activada por venta pagada.
- `MembershipRenewedV1`: renovacion acumulativa procesada.
- `MembershipPlanChangedV1`: membresia anterior cancelada y nuevo plan activado.
- Paquete propietario: `com.gymos.memberships.api.events`.

Reglas reforzadas:
- Una membresia solo se activa desde pago completado.
- Reprocesar `PaymentCompletedV1` no duplica efectos porque se registra `processed_domain_event`.
- Un cambio al mismo plan es invalido.
- Una membresia cancelada no puede renovarse.
- El kiosco opera con sesion temporal, token aleatorio, verificacion por contacto + fecha de nacimiento y origen auditado `SELF_SERVICE_KIOSK`.
- `selfservice` no resuelve identidad personal directamente; consume `ClientLookupOperations`.
- Las renovaciones/cambios del kiosco se ejecutan mediante `MembershipOperations`, no por dependencia directa a la capa application de membresias.
- Las llaves de idempotencia en ventas/pagos incluyen hash de solicitud para detectar reutilizacion con contenido distinto.
 
## Hito 7 - Entidades piloto agregadas

### Benefit

Beneficio comercial aplicable a clientes, planes, productos o accesos. Puede representar estudiante, adulto mayor, convenio, promocion, descuento porcentual, descuento fijo, precio especial o beneficio de acceso.

### ClientBenefit

Asignacion de un beneficio a un cliente dentro de una organizacion y, opcionalmente, una sucursal. Estados iniciales: `ASSIGNED`, `VERIFIED`, `EXPIRED`, `REJECTED`.

### BenefitEvidence

Evidencia documental asociada a un beneficio asignado. Guarda referencia a objeto, estado y datos de revision.

### VisitPass

Derecho de acceso por visita unica, paquete de visitas o pase diario. Controla visitas totales, restantes, vigencia y venta origen.

### SaleRefund

Solicitud y procesamiento de reembolso de venta o pago, con idempotencia, motivo y auditoria.

### BookingPayment

Relacion entre reserva y venta. Permite reservas provisionales que se confirman al completar pago.

### BookingPolicy

Politica de anticipacion, cancelacion, penalizacion, buffers y pago requerido para recursos reservables.

### AutoRenewalAttempt

Intento idempotente de renovacion automatica de membresia.

### NotificationTemplate

Plantilla versionable por organizacion para correo u otros canales.
