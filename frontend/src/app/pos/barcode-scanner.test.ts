import assert from "node:assert";
import { describe, it } from "node:test";
import { createBarcodeDetector, playScanBeep } from "./barcode-scanner.ts";

describe("barcode-scanner detector", () => {
  it("detects rapid keystrokes as a barcode scan on Enter", () => {
    let scannedCode = "";
    const detector = createBarcodeDetector({
      onScan: (code) => {
        scannedCode = code;
      },
      maxIntervalMs: 50,
    });

    let now = 1000;
    const nowFn = () => now;

    // Simulate scanning "7501234567890" rapidly (10ms between keys)
    const code = "7501234567890";
    for (const char of code) {
      now += 10;
      detector.handleKeyDown(
        { key: char, target: {} } as unknown as KeyboardEvent,
        nowFn,
      );
    }

    now += 10;
    detector.handleKeyDown(
      { key: "Enter", target: {} } as unknown as KeyboardEvent,
      nowFn,
    );

    assert.strictEqual(scannedCode, "7501234567890");
  });

  it("resets buffer if keypress interval exceeds maxIntervalMs (human typing)", () => {
    let scannedCode = "";
    const detector = createBarcodeDetector({
      onScan: (code) => {
        scannedCode = code;
      },
      maxIntervalMs: 50,
    });

    let now = 1000;
    const nowFn = () => now;

    // First key
    detector.handleKeyDown(
      { key: "7", target: {} } as unknown as KeyboardEvent,
      nowFn,
    );

    // Pause 200ms (human typing)
    now += 200;
    detector.handleKeyDown(
      { key: "5", target: {} } as unknown as KeyboardEvent,
      nowFn,
    );

    now += 10;
    detector.handleKeyDown(
      { key: "Enter", target: {} } as unknown as KeyboardEvent,
      nowFn,
    );

    // Should NOT trigger onScan because slow typing reset the buffer before "5"
    assert.strictEqual(scannedCode, "");
  });

  it("clears focused input element on smart bypass when scanner burst is detected", () => {
    let scannedCode = "";
    const detector = createBarcodeDetector({
      onScan: (code) => {
        scannedCode = code;
      },
      maxIntervalMs: 50,
    });

    const mockInput = {
      tagName: "INPUT",
      value: "7501234567890",
    };

    let now = 1000;
    const nowFn = () => now;

    let defaultPrevented = false;
    const code = "7501234567890";
    for (const char of code) {
      now += 10;
      detector.handleKeyDown(
        { key: char, target: mockInput } as unknown as KeyboardEvent,
        nowFn,
      );
    }

    now += 10;
    detector.handleKeyDown(
      {
        key: "Enter",
        target: mockInput,
        preventDefault: () => {
          defaultPrevented = true;
        },
      } as unknown as KeyboardEvent,
      nowFn,
    );

    assert.strictEqual(scannedCode, "7501234567890");
    assert.strictEqual(mockInput.value, "");
    assert.strictEqual(defaultPrevented, true);
  });

  it("does not throw when playing scan beep with Web Audio API or fallback", () => {
    assert.doesNotThrow(() => playScanBeep("success"));
    assert.doesNotThrow(() => playScanBeep("error"));
  });
});
