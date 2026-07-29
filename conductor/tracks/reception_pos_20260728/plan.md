# Implementation Plan: Reception Dashboard and Point of Sale (POS) Initialization

## Phase 1: Reception Dashboard Layout and Components [checkpoint: fce7a10]
- [x] Task: Scaffold the basic layout and model for the Reception Dashboard.
    - [x] Write Tests: Create `frontend/src/app/reception/reception-model.test.ts` and write tests for navigation and signal logic.
    - [x] Implement Feature: Create `reception-model.ts` and update `page.tsx` to use it.
- [x] Task: Create a "Quick Actions" component for receptionists (e.g., check-in, new sale).
    - [x] Write Tests: Create a test for the Quick Actions logic.
    - [x] Implement Feature: Build the component and integrate it into the dashboard.
- [x] Task: Completely redesign the Reception Dashboard to act as a unified hub.
    - [x] Write Tests: Update `reception-model.test.ts` to reflect the new structure.
    - [x] Implement Feature: Refactor `page.tsx` and `ReceptionActionRail.tsx` to provide immediate access to POS, Member Management, Bookings, and Memberships.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Reception Dashboard Layout and Components' (Protocol in workflow.md)

## Phase 2: Point of Sale (POS) Initialization
- [ ] Task: Set up the POS shell in `frontend/src/app/pos/page.tsx` utilizing `PosSections.tsx`.
    - [ ] Write Tests: Write failing tests for the POS layout and initial section rendering.
    - [ ] Implement Feature: Implement the layout and structural components to pass the tests.
- [ ] Task: Implement the product catalog grid/list view within the POS.
    - [ ] Write Tests: Write tests verifying that mock products render correctly and can be selected.
    - [ ] Implement Feature: Build the product grid component and connect it to mock/API data.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Point of Sale (POS) Initialization' (Protocol in workflow.md)

## Phase 3: Integration and Polish
- [ ] Task: Ensure styling is consistent with the `Hybrid Contextual Density` product guidelines (Data-dense for these admin views).
    - [ ] Write Tests: Update or add snapshot/structural tests to verify specific Tailwind classes are applied.
    - [ ] Implement Feature: Refine the CSS/Tailwind utility classes.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Integration and Polish' (Protocol in workflow.md)