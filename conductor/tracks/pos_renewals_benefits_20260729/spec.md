# Specification: POS Membership Renewals & Benefits Optimization

## Overview
Optimize the Point of Sale (POS) cashier experience and client membership workflow by allowing direct 1-click membership renewals and plan changes directly from the POS interface when a client is selected, enabling renewal of expired memberships on the client detail page, restricting benefit/agreement creation to managerial roles (Manager, Owner, Admin), and removing the complex "Priority" field from the benefit creation form.

## Functional Requirements
- **POS Client Membership Card & Direct Actions:**
  - When a client is selected in `/pos`, fetch and display their current membership details (plan name, validity dates, status badge: `ACTIVE` / `EXPIRED`).
  - Display active discounts or company agreements (`ClientBenefit`) assigned to the selected client.
  - Provide a **"🔄 Renovar Mismo Plan"** button that automatically loads the client's current plan into the POS cart for immediate checkout.
  - Provide a **"🔀 Cambiar Plan"** dropdown that allows selecting a different plan from the catalog and loading it into the POS cart.
- **Client Detail Membership Renewal Fix:**
  - Update `/clients/[clientId]` membership history section to enable the **"Renovar"** button for memberships in `EXPIRED` status (in addition to `ACTIVE`).
- **Benefits Creation Security & Form Simplification:**
  - Restrict the creation and configuration of benefits (`/benefits`) to `TENANT_MANAGER`, `TENANT_OWNER`, and `PLATFORM_ADMIN` roles.
  - Remove `/benefits` from reception navigation routes and navigation sidebar.
  - Remove the visible "Prioridad" input field from the benefit creation drawer form, defaulting internally to `10`.
  - Add clear helper text clarifying that Benefits represent promotional discounts or company agreements (e.g. Student, Senior, Corporate Agreement).

## Non-Functional Requirements
- POS membership lookup must respond within 200ms upon client selection.
- Must include unit tests for renewal helpers, role access checks, and POS client membership loading logic.

## Acceptance Criteria
- [ ] Selecting a client with an active or expired membership in POS shows their plan details, status, and assigned agreements.
- [ ] Clicking "Renovar Mismo Plan" in POS adds 1 unit of their current plan to the POS cart.
- [ ] Selecting a new plan under "Cambiar Plan" in POS loads the target plan into the POS cart.
- [ ] The "Renovar" button on `/clients/[clientId]` is enabled for expired memberships (`EXPIRED`).
- [ ] Receptionists (`TENANT_RECEPTIONIST`) cannot see or access the `/benefits` page.
- [ ] The "Prioridad" field is hidden from the Benefit creation drawer, and helper text clarifies its purpose as promotional discounts/agreements.

## Out of Scope
- Automatic recurring credit card billing / auto-debit payments.
- Hardware NFC/RFID card reader integration for client identification.
