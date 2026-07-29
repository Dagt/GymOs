# Specification: Reception Renewals Module, Initial Plan Onboarding, and Benefits Governance

## Overview
Implement a dedicated **"Renovaciones y Planes"** operational module accessible from the Reception Dashboard, separate membership onboarding for new clients without previous plans, clean the POS interface to focus exclusively on retail products, and establish strict lifecycle governance for plan-linked access features and time-bound promotional discounts/agreements.

## Functional Requirements
- **Benefits Architecture & Plan Linking:**
  - **Access Benefits (`ACCESS`):** Linked directly to Membership Plans (e.g., "Pase a Sauna" assigned to "Plan VIP"). Inherited automatically by clients while their membership plan is `ACTIVE`, and revoked upon plan expiration or change.
  - **Discount Benefits (`DISCOUNT` / `AGREEMENT`):** Assigned manually to a client (e.g., "Discapacidad", "Tercera Edad", "Convenio Empresa") with a mandatory expiration date (`validUntil`). Applied exclusively to membership plan subscription/renewal pricing, not retail products.
  - Fix benefit dropdown loading in `/clients/[clientId]` by isolating `getBenefits` API calls.
- **Initial Plan Onboarding in Client Detail (`/clients/[clientId]`):**
  - When a client has no active membership plan (`memberships.length === 0`), provide a dedicated **"Activar / Contratar Plan Inicial"** onboarding action.
  - Accessible to Reception, Manager, Owner, and Admin roles (specifically resolving the gap for clients created by Managers).
- **POS Retail Cleanliness:**
  - Remove all plan renewal cards and plan product summaries from `/pos`.
  - Keep `/pos` 100% focused on physical products, beverages, supplements, and individual day passes.
- **Dedicated Reception Renewals & Plan Changes Module (`/reception/renewals`):**
  - Add an operational card **"Renovaciones y Planes"** on `/reception` linking to `/reception/renewals`.
  - Search and select clients by name, phone, or email.
  - Display current membership status (`ACTIVE` / `EXPIRED`), plan name, and expiration date.
  - Action **"🔄 Renovar Mismo Plan"**: Process renewal for 1 additional period with payment method selection (Efectivo, Tarjeta, Transferencia).
  - Action **"🔀 Cambiar de Plan"**: Select a new plan from catalog, register payment, terminate previous plan, and activate new plan.
  - Record official sale (`Sale`) transaction and update cash register / financial metrics.

## Non-Functional Requirements
- Module response times under 200ms.
- Unit tests for renewal controller, initial onboarding drawer, role routes, and benefit expiration logic.

## Acceptance Criteria
- [ ] Access Benefits (`ACCESS`) link to Membership Plans and are inherited automatically while active.
- [ ] Assigned client discounts require an expiration date (`validUntil`) and apply to plan pricing.
- [ ] Dropdown list of benefits in Client Detail loads reliably.
- [ ] Unassigned clients (including those created by Managers) can be assigned an initial plan via "Activar Plan Inicial" across all operational roles.
- [ ] `/pos` contains zero renewal cards or plan items, remaining 100% dedicated to retail sales.
- [ ] `/reception` renders the "Renovaciones y Planes" operational card pointing to `/reception/renewals`.
- [ ] Receptionists can search clients, renew existing plans, or change plans at `/reception/renewals` with cash/card/transfer.

## Out of Scope
- Automatic recurring credit card charge via gateway payment webhooks.
- NFC turnstile hardware SDK integration.
