# Changelog

Los cambios relevantes de GymFort se documentan aquí. `GymOS` permanece como
nombre técnico interno durante la transición de marca.

## 0.7.0-pilot-readiness - Unreleased

- Adopt the visible GymFort product identity while preserving compatible
  internal `gymos` packages, variables and data identifiers.
- Put the tenant logo, gym name and active branch first in the sidebar; move the
  signed-in user controls below it and keep GymFort attribution in the footer.
- Align Automations and Organization settings with the same light workspace
  surfaces used by Catalog while retaining the shared dark command header, and
  replace page-top feedback boxes with accessible floating notifications.
- Add Stripe Connect, hosted Checkout, signed webhooks, reconciliation,
  receipts, recurring card mandates, retries and tenant-scoped histories.
- Support kiosk membership enrollment, renewal and plan changes only after
  confirmed payment, with idempotent sales, payments and membership periods.
- Support multi-branch plan access in secondary branches, including eligible
  kiosk lookup and reservations while retaining membership origin ownership.
- Restrict kiosk launch strictly to Reception in navigation, direct routes,
  HTTP security and business authorization; owners, administrators, managers
  and platform support cannot open a member session.
- Simplify the Reception home by removing the non-actionable cash/payment/branch
  signal strip and keeping the operational actions in focus.
- Split Stripe account management from operational payment switches: managers
  may enable or disable an existing connection, while owners/support connect
  and refresh it.
- Add durable email delivery with SMTP, leases, retries, preferences,
  suppression, tenant sender settings and operational metrics. Zoho provider
  validation remains upcoming.
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
- Make Automations expose only working manual/scheduled notification rules,
  reject unsupported definitions and explain its operational purpose in the
  administrative interface.

## 0.6.5

- Redesign frontend entry and administration experience.
- Add local brand customization for demos and pilots.

## 0.6.0

- Add onboarding and CSV client import flows.
