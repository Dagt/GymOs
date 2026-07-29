# Implementation Plan: POS Global Barcode Scanner Auto-Add

## Phase 1: Barcode Detection Utility & Audio Feedback Helper
- [ ] Task: Create barcode scan detector utility and Web Audio feedback helper.
    - [ ] Write Tests: Create `frontend/src/app/pos/barcode-scanner.test.ts` testing buffer timing, keypress burst detection, and smart input bypass logic.
    - [ ] Implement Feature: Create `frontend/src/app/pos/barcode-scanner.ts` with `useBarcodeScanner` hook and Web Audio API sound generator.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Barcode Detection Utility & Audio Feedback Helper' (Protocol in workflow.md)

## Phase 2: POS Page Integration & UI Feedback
- [ ] Task: Integrate global barcode scanner listener into POS page and cart feedback.
    - [ ] Write Tests: Update `frontend/src/app/pos/pos-model.test.ts` to test barcode lookup, auto-add to cart, and error state handling.
    - [ ] Implement Feature: Integrate `useBarcodeScanner` in `frontend/src/app/pos/page.tsx` and update `PosSections.tsx` for cart row highlight animations and scan error toasts.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: POS Page Integration & UI Feedback' (Protocol in workflow.md)
