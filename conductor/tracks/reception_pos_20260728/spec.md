# Track Specification: Reception Dashboard and Point of Sale (POS) Initialization

## 1. Overview
This track focuses on initializing and developing the core interfaces and functional logic for the Reception Dashboard and the Point of Sale (POS) module within GymOS.

## 2. Goals
- Set up the main entry page for the Reception module (`frontend/src/app/reception/page.tsx`).
- Initialize the POS module layout and sections (`frontend/src/app/pos/page.tsx`, `PosSections.tsx`).
- Implement essential data fetching and state management for daily reception tasks.
- Ensure the UI adheres to the "Hybrid Contextual Density" and "Data-Dense & Admin" guidelines, optimized for power users.

## 3. Scope
**In Scope:**
- Frontend routing and component structure for `/reception` and `/pos`.
- Integration with existing backend endpoints (if applicable) for retrieving initial data (e.g., daily check-ins, recent sales).
- Implementation of basic unit tests for the new components.

**Out of Scope:**
- Advanced payment gateway integration (unless already existing in the backend).
- Hardware integrations (e.g., physical receipt printers, barcode scanners) in this initial phase.

## 4. Technical Constraints
- Must follow the established Next.js 16 (App Router) and React 19 patterns.
- Styling must be exclusively Tailwind CSS.
- Code coverage for new components must be >90%.