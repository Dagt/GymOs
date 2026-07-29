# Implementation Plan: POS Membership Renewals & Benefits Optimization

## Phase 1: Benefits Security & Form Simplification [checkpoint: 72fe8a5]
- [x] Task: Restrict `/benefits` page access to managerial roles and simplify the benefit creation form.
    - [x] Write Tests: Update role access tests to verify receptionists cannot access `/benefits`.
    - [x] Implement Feature: Update `role-model.ts`, `app-shell-model.ts`, and `frontend/src/app/benefits/page.tsx` to restrict creation to Manager/Owner/Admin, remove `/benefits` from reception navigation, hide the "Priority" input field, and add descriptive helper text.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Benefits Security & Form Simplification' (Protocol in workflow.md)

## Phase 2: Client Detail Expired Membership Renewal Fix
- [ ] Task: Enable renewal action for expired memberships on client detail page.
    - [ ] Write Tests: Update client membership component/model tests to verify the "Renovar" button is enabled for `EXPIRED` status.
    - [ ] Implement Feature: Update `frontend/src/app/clients/[clientId]/MembershipHistory.tsx` to allow renewal on expired memberships.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Client Detail Expired Membership Renewal Fix' (Protocol in workflow.md)

## Phase 3: POS In-App Membership Renewal & Plan Change Card
- [ ] Task: Integrate client membership status card, 1-click renewal, and plan change selector into POS.
    - [ ] Write Tests: Update `frontend/src/app/pos/pos-model.test.ts` to test client membership lookup, renewal cart item generation, and plan change selection.
    - [ ] Implement Feature: Update `PosClientSearch`, `PosSections.tsx`, and `frontend/src/app/pos/page.tsx` to fetch client membership and assigned benefits upon client selection, render the membership status card with "Renovar Mismo Plan" and "Cambiar Plan" buttons, and load selected plans into the POS cart.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: POS In-App Membership Renewal & Plan Change Card' (Protocol in workflow.md)
