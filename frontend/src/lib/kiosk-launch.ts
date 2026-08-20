import type { KioskSessionResponse } from "./api/selfservice-types";

export function encodeKioskLaunch(session: KioskSessionResponse): string {
  const bytes = new TextEncoder().encode(JSON.stringify(session));
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

export function consumeKioskLaunch(transfer: string): KioskSessionResponse | null {
  if (!transfer) return null;
  try {
    const encoded = transfer;
    const padded = encoded.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(encoded.length / 4) * 4, "=");
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const parsed = JSON.parse(new TextDecoder().decode(bytes)) as Partial<KioskSessionResponse>;
    if (typeof parsed.sessionId !== "string" || typeof parsed.sessionToken !== "string" || typeof parsed.expiresAt !== "string") return null;
    if (new Date(parsed.expiresAt).getTime() <= Date.now()) return null;
    return parsed as KioskSessionResponse;
  } catch {
    return null;
  }
}
