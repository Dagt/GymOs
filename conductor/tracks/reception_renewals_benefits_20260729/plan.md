# Implementation Plan: Reception Renewals Module, Initial Plan Onboarding, and Benefits Governance

## Phase 1: Benefits Architecture, Plan Linking & Expiration Governance
- [ ] Task: Link access benefits to membership plans and enforce plan discount rules.
    - [ ] Write Tests: Update benefit tests to verify plan-linked access inheritance and plan-only discount application with mandatory expiration.
    - [ ] Implement Feature: Update `ClientBenefitsSection.tsx`, `benefits.ts`, and `page.tsx` under `/benefits` so Access Benefits are linked to Membership Plans (inherited automatically while plan is active), Discount Benefits apply exclusively to membership plan pricing with mandatory expiration dates (`validUntil`), and fix the dropdown API loading in Client Detail.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Benefits Architecture, Plan Linking & Expiration Governance' (Protocol in workflow.md)

## Phase 2: Initial Plan Onboarding in Client Detail & POS Retail Cleanup
- [ ] Task: Add "Activar Plan Inicial" onboarding drawer in Client Detail and clean POS interface from plan items.
    - [ ] Write Tests: Update client detail controller tests for initial plan onboarding on unassigned clients (including clients created by Managers), and POS model tests for 100% retail product focus.
    - [ ] Implement Feature: Update `MembershipHistory.tsx` and `useClientDetailController.ts` to enable "Activar / Contratar Plan Inicial" for unassigned clients across all roles, and update `/pos` (`PosSections.tsx`, `page.tsx`) to remove plan renewal cards and keep POS 100% retail.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Initial Plan Onboarding in Client Detail & POS Retail Cleanup' (Protocol in workflow.md)

## Phase 3: Dedicated Reception Renewals Module (`/reception/renewals`)
- [ ] Task: Create dedicated `/reception/renewals` page and Reception dashboard tile.
    - [ ] Write Tests: Create `renewals-model.test.ts` testing client lookup, renewal execution, plan change, and sale payload generation.
    - [ ] Implement Feature: Create `frontend/src/app/reception/renewals/page.tsx`, update `reception-model.ts`, `role-model.ts`, and `app-shell-model.ts` to add the "Renovaciones y Planes" card on `/reception` pointing to `/reception/renewals`.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Dedicated Reception Renewals Module' (Protocol in workflow.md)
