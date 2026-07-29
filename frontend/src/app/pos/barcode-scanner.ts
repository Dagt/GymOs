import { useEffect, useRef } from "react";

export type ScanBeepType = "success" | "error";

export function playScanBeep(type: ScanBeepType = "success"): void {
  if (typeof window === "undefined") return;

  try {
    const AudioCtx =
      window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;

    const ctx = new AudioCtx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.connect(gain);
    gain.connect(ctx.destination);

    const now = ctx.currentTime;

    if (type === "success") {
      osc.type = "sine";
      osc.frequency.setValueAtTime(1046.5, now); // C6 high beep
      gain.gain.setValueAtTime(0.15, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);

      osc.start(now);
      osc.stop(now + 0.12);
    } else {
      osc.type = "sawtooth";
      osc.frequency.setValueAtTime(220, now); // Low error tone
      gain.gain.setValueAtTime(0.2, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);

      osc.start(now);
      osc.stop(now + 0.25);
    }

    setTimeout(() => {
      ctx.close().catch(() => {});
    }, 300);
  } catch {
    // Audio Context blocked by user gesture or unsupported - ignore silently
  }
}

export type BarcodeDetectorOptions = {
  onScan: (code: string) => void;
  maxIntervalMs?: number;
  minLength?: number;
};

export function createBarcodeDetector({
  onScan,
  maxIntervalMs = 50,
  minLength = 3,
}: BarcodeDetectorOptions) {
  let buffer: string[] = [];
  let lastKeyTime = 0;

  function handleKeyDown(event: KeyboardEvent, nowFn: () => number = Date.now): void {
    if (event.ctrlKey || event.altKey || event.metaKey) return;

    const currentTime = nowFn();
    const timeDiff = currentTime - lastKeyTime;

    if (lastKeyTime > 0 && timeDiff > maxIntervalMs) {
      buffer = [];
    }
    lastKeyTime = currentTime;

    if (event.key.length === 1) {
      buffer.push(event.key);
    } else if (event.key === "Enter") {
      const scannedCode = buffer.join("").trim();
      if (scannedCode.length >= minLength) {
        const target = event.target as { tagName?: string; value?: string } | null;
        if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA")) {
          target.value = "";
          if (typeof event.preventDefault === "function") {
            event.preventDefault();
          }
        }
        onScan(scannedCode);
      }
      buffer = [];
    }
  }

  return {
    handleKeyDown,
  };
}

export function useBarcodeScanner({
  onScan,
  enabled = true,
}: {
  onScan: (code: string) => void;
  enabled?: boolean;
}): void {
  const onScanRef = useRef(onScan);

  useEffect(() => {
    onScanRef.current = onScan;
  }, [onScan]);

  useEffect(() => {
    if (!enabled) return;

    const detector = createBarcodeDetector({
      onScan: (code) => onScanRef.current(code),
    });

    const listener = (event: KeyboardEvent) => {
      detector.handleKeyDown(event);
    };

    window.addEventListener("keydown", listener);
    return () => {
      window.removeEventListener("keydown", listener);
    };
  }, [enabled]);
}
