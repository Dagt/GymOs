# Specification: Global Barcode Scanner Detection in POS

## Overview
Implement a global barcode scanner detection listener in the Point of Sale (POS) interface (`/pos`) that captures rapid keystroke bursts from physical barcode scanners (USB/Bluetooth), looks up the matching product by `barcode` or `sku` in the active catalog, and automatically adds 1 unit of the item to the cart without requiring manual cursor focus on the search input.

## Functional Requirements
- **Global Listener & Burst Detection:**
  - Attach a global keydown event listener on the POS page.
  - Measure inter-keystroke intervals to distinguish between human typing (>100ms) and barcode scanner bursts (<30ms per character).
  - Accumulate characters into an internal buffer and flush/process upon receiving `Enter`.
- **Smart Input Bypass:**
  - If the user is currently typing in an input field (e.g. client search or payment reference) and a scanner burst is detected (>5 chars in <50ms with `Enter`), clear the field value and process the burst as a barcode scan.
- **Auto-Add to Cart:**
  - Search the active product catalog by exact `barcode` match first, falling back to exact `sku` match.
  - If a matching product is found:
    - Automatically add 1 unit to the cart (or increment quantity if already present).
    - Clear the scanner buffer.
- **Audio & Visual Feedback:**
  - Play a brief success beep via Web Audio API when a product is added.
  - Highlight the added/updated item row in the cart UI briefly.
  - On unmatched barcode: display an error toast/banner ("Código de barras no encontrado: [código]") and play an error tone.

## Non-Functional Requirements
- Response time for scanning and adding to cart must be under 100ms.
- Must work seamlessly across modern web browsers without external plugins.
- Must include unit tests for the scanner hook/utility logic.

## Acceptance Criteria
- [ ] Scanning a valid product barcode adds 1 unit directly to the cart from anywhere on the `/pos` page.
- [ ] Scanning the same product barcode multiple times increments its quantity in the cart.
- [ ] Web Audio beep plays on successful scan.
- [ ] Scanned item row in the cart flashes/highlights temporarily.
- [ ] Scanning an unknown barcode triggers an error toast and warning tone.
- [ ] Smart bypass clears text input if a barcode scan occurs while an input field is focused.

## Out of Scope
- Hardware configuration or driver installation for physical scanners.
- Camera-based barcode scanning (using device webcam).
