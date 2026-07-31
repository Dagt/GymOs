import type { KioskSessionResponse } from "./api/selfservice-types";

const LAUNCH_PREFIX = "launch=";

export function encodeKioskLaunch(session: KioskSessionResponse): string {
  const json = JSON.stringify(session);
  const bytes = new TextEncoder().encode(json);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

export function consumeKioskLaunch(hash: string): KioskSessionResponse | null {
  const rawHash = hash.startsWith("#") ? hash.slice(1) : hash;
  if (!rawHash.startsWith(LAUNCH_PREFIX)) {
    return null;
  }

  try {
    const encoded = rawHash.slice(LAUNCH_PREFIX.length);
    const padded = encoded.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(encoded.length / 4) * 4, "=");
    const binary = atob(padded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const parsed = JSON.parse(new TextDecoder().decode(bytes)) as Partial<KioskSessionResponse>;

    if (
      typeof parsed.sessionId !== "string"
      || typeof parsed.sessionToken !== "string"
      || typeof parsed.expiresAt !== "string"
    ) {
      return null;
    }

    return parsed as KioskSessionResponse;
  } catch {
    return null;
  }
}
