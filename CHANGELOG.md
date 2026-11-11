# Changelog

## Gobernanza documental (29/10/2026) — propuesta en rama, sin merge

- MVP y puertas de salida, 7 épicas/32 issues, FR/BR/NFR, flujos UX, máquinas de estados, QA, ADR, preguntas abiertas y workflow Codex según método TrailerSaas.
- Auditoría de main V103 y Local Connector V104 en rama no integrada; distinción pruebas históricas, CI fallido y hardware pendiente.
- Adendas a documentos canónicos sin cambiar comportamiento de código ni migraciones.


Los cambios relevantes de GymFort se documentan aquí. `GymOS` permanece como
nombre técnico interno durante la transición de marca.

## 0.7.0-pilot-readiness - Unreleased

- El tema visual del tenant se sincroniza al restaurar, renovar o modificar la sesión, al guardar la paleta y al salir del contexto. Se descartan perfiles tardíos de otra sesión sin esperar a navegar.

- Catálogo iguala las alturas de resumen y filtros. Transferencias limita resultados con scroll visible. Los botones primarios de las secciones intervenidas recuperan su acento inferior de marca.

- Inventario simplifica acciones del producto; transferencias amplía su selector y navegación por teclado. Catálogo alinea sus filtros. Membresías elimina actividad reciente y usa selección con acento de marca y confirmación visible.

- Recepción centra sus acciones de membresías y paquetes, compacta el socio y saldo, y destaca el total y la confirmación en un resumen de cobro adaptable a escritorio, tablet y móvil.

- «Membresías y paquetes» permite elegir primero la operación o el socio, buscar con debounce y consultar las contrataciones recientes de la sucursal mediante el listado de ventas existente.

- La recuperación de Checkout verifica el estado actual en Stripe antes de
  redirigir o crear otro intento. Conserva el pago original y reserva los
  reemplazos de forma idempotente en PostgreSQL.

- PAY-001 y SELF-001 (#34) permiten retomar el pago original del kiosco tras volver a verificar al socio. El retorno de Stripe se vincula a la sesión, el pago o el mandato exactos. El cierre del kiosco rota la credencial y limpia los datos del socio.
- Checkout y la captura de tarjeta distinguen la expiración y los estados pendientes que Stripe informa. Los reintentos conservan la idempotencia. La aceptación conectada en Stripe test y staging permanece pendiente.

- Recepción destaca «Membresías y paquetes» y muestra sus cuatro operaciones antes de buscar un socio. El expediente conserva el paquete vigente o pendiente y organiza los beneficios y accesos en grupos responsivos.

- Recepción concentra las contrataciones en «Membresías y paquetes». La ficha y recepción comparten el formulario de paquetes y envían claves de idempotencia dentro del límite del API.

- SAL-001 (#33) serializa ventas, pagos, devoluciones y cancelaciones, protege las reservas de Point y permite recuperar operaciones con resultado incierto desde recepción.

- Point requiere moneda explícita de la orden e importe efectivamente pagado. Las respuestas insuficientes quedan pendientes de conciliación.

- AUTH-001 (#28) mueve el refresh a cookies HttpOnly y el JWT a memoria. Redis rota y revoca familias, y Spring revalida permisos y contexto antes de autorizar las operaciones.

- GEN-001 y ORG-001 documentan los permisos por endpoint y refuerzan el aislamiento por tenant y sucursal en equipo, socios, ventas, beneficios, kiosco, bienestar e inventario. El backend revalida el contexto de los JWT y registra las mutaciones autorizadas con el contexto operativo.
- La validación de #29 coteja las 298 operaciones con Spring y comprueba con PostgreSQL la pertenencia de roles, ventas asociadas a reservas, preferencias de socios, comprobantes históricos y respuestas ante UUIDs de venta o pago fuera de alcance.

- ACC-006 incorpora el registro presencial de entradas en Recepción. Usa los
  derechos de membresía y paquete, protege el saldo con idempotencia y
  concurrencia en PostgreSQL, y conserva el historial y sus reversiones.
- Recepción muestra los servicios incluidos cuando el paquete puede usarse y
  vuelve a consultar los derechos ante un duplicado o un rechazo inesperado.
- Actualiza Next.js, su configuración de ESLint y Wrangler para corregir los
  hallazgos altos y críticos del gate npm de seguridad.

- Adopt the visible GymFort product identity while preserving compatible
  internal `gymos` packages, variables and data identifiers.
- Put the tenant logo, gym name and active branch first in the sidebar; move the
  signed-in user controls below it and keep GymFort attribution in the footer.
- Clarify sidebar hierarchy with stronger section/child separation, tree cues
  and a more visible current-page/section treatment without changing the GymFort
  visual language.
- Retire the redundant `Operación diaria` page and route; managers now land on
  the first useful authorized workspace, prioritizing executive indicators.
- Align Automations and Organization settings with the same light workspace
  surfaces used by Catalog while retaining the shared dark command header, and
  replace page-top feedback boxes with accessible floating notifications.
- Join connected-service tabs to their active configuration panel and reserve
  tenant gradient/tint for first-level groups while keeping nested controls
  neutral and readable.
- Replace payment and kiosk capability checkboxes with tenant-aware toggles whose
  outline/knob use the primary color and whose active track uses the accent;
  group reception and Stripe settings, make manual terminal/Mercado Pago mutually
  exclusive and enforce Stripe readiness for dependent kiosk capabilities.
- Add Stripe Connect, hosted Checkout, signed webhooks, reconciliation,
  receipts, recurring card mandates, retries and tenant-scoped histories.
- Support kiosk membership enrollment, renewal and plan changes only after
  confirmed payment, with idempotent sales, payments and membership periods.
- Support multi-branch plan access in secondary branches, including eligible
  kiosk lookup and reservations while retaining membership origin ownership.
- Add visit-package products with one organization-wide balance, explicit
  `CONSUMES_VISIT` and `INCLUDED_WITH_VISIT` access modes, immutable
  benefit/branch/mode snapshots and membership-first access decisions.
- Keep premium facilities branch-local in multi-branch packages, require a
  consumable base access in every selected branch and make incomplete catalog
  definitions fail closed without mutating purchases already sold.
- Expose active ACCESS benefits organization-wide to package administration
  while displaying and enforcing effective branch coverage; hide the internal
  `VISIT_PASS` product from generic Catalog and POS workflows.
- Preserve immutable Flyway history for the physical connector and move later
  hardening into V102, including safe upgrade compatibility for historical
  V83–V85 installations.
- Restrict kiosk launch strictly to Reception in navigation, direct routes,
  HTTP security and business authorization; owners, administrators, managers
  and platform support cannot open a member session.
- Simplify the Reception home by removing the non-actionable cash/payment/branch
  signal strip and keeping the operational actions in focus.
- Split Stripe account management from operational payment switches: managers
  may enable or disable an existing connection, while owners/support connect
  and refresh it.
- Complete durable transactional email delivery with ZeptoMail through the
  existing `EmailDeliveryPort`: verified `gymfort.mx` DKIM/return-path DNS,
  real SMTP delivery, tenant sender name/Reply-To, allowlisted From addresses,
  retries/dead letters, preferences and operational metrics.
- Add branded HTML email rendering from organization name, primary/accent/surface
  colors and tenant logo; resolve relative logos with `GYMOS_FRONTEND_BASE_URL`
  and avoid broken localhost images in external mail clients.
- Simplify Automations to three customer-facing views: Automations, Activity and
  Problems; hide scheduler/lease/internal execution detail from the normal gym
  workflow while preserving it in backend observability.
- Remove recommended automation templates; new rules are configured from scratch
  through a guided flow.
- Replace the Automations options menu with a direct `Probar ahora` action.
- Make email-rule tests select a real active member through incremental
  name/email search instead of executing without recipient context.
- Grant managers the operational automation/notification permissions required to
  create, enable, disable and test rules (V80) without granting scheduler control.
- Clarify recurring-charge semantics: `RECURRING_CHARGE_DUE` represents a due
  occurrence, not the initial activation of a recurring payment mandate.
- Reconcile missing historical Coach operational profiles with V79 after a
  real acceptance case showed role/branch access could drift from the `coach`
  entity and break workout creation/metrics.
- Add a pinned Stripe CLI `v1.50.3` Docker profile and CI verification.

- Consolidate documentation into one canonical status file per module.
- Add the live technical-debt register and audited branch chronology.
- Advance Coaches and workouts editing, exercise aliases and assignment lifecycle.
- Redesign Team, workout builder, exercise catalog and personal Coach metrics
  with compact, progressive and role-aware interfaces.
- Add organization-wide reusable exercise equipment, a scrollable catalog and
  compact fixed-height exercise cards whose descriptions do not stretch rows.
- Keep muscle-group and equipment selectors aligned by rendering new-equipment
  details outside the identification grid.
- Restore the completed Wellness directory, agenda, appointments and role
  access from `feature/wellness-services` on top of the current platform.
- Add live member lookup for workout assignments and align assignment eligibility
  with the `PUBLISHED` status produced by the publishing workflow.
- Rework the workout builder into three progressive steps and move exercise
  descriptions to an on-demand preview.
- Add explicit uniform and detailed set prescriptions, including no-rest
  segments for pyramids, progressions and drop sets.
- Keep the everyday exercise prescription in one compact row and move
  per-set variations plus technical options into an on-demand side panel.
- Repair assignment PDF downloads and replace the plain export with a branded,
  paginated layout containing workout metadata and structured set tables.
- Relax the workout PDF visual hierarchy and safely embed tenant logos stored
  under the frontend's internal `/tenants/` path.
- Harden production readiness configuration.
- Add operational runbooks for deployment, backup, restore, rollback, incidents and maintenance.
- Restrict demo seed to the local Spring profile.
- Expand CI with Docker Compose validation and image builds.
- Scope newly created benefits to the active branch, surface evidence
  verification state and let reception inspect inherited member access without
  access-administration privileges.
- Consolidate member renewal, initial enrollment and plan changes in reception;
  keep only the operational current plan on the profile and move membership
  movements to its activity timeline.
- Separate Wellness professional modality from office location so external
  professionals can retain their consultation address and receive appointment
  requests without internal availability schedules.
- Show each member's current plan and effective membership status in the client
  list with muted active, expired and no-membership treatments.
- Start the safe self-service kiosk foundation with canonical terminal
  capabilities, audited contact updates and card-only financial contracts that
  remain closed until a real payment gateway is connected.

## 0.6.5

- Redesign frontend entry and administration experience.
- Add local brand customization for demos and pilots.

## 0.6.0

- Add onboarding and CSV client import flows.
