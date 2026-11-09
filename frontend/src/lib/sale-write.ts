import { ApiResponseError } from "./api/client";
import type { StoredSession } from "./api/auth-types";

type PendingWrite = { fingerprint: string; key: string };
const pendingWrites = new Map<string, PendingWrite>();
const runningWrites = new Set<string>();

export async function runSaleWrite<T>(
  session: StoredSession,
  operation: string,
  payload: unknown,
  write: (key: string) => Promise<T>,
): Promise<T> {
  const { organizationId, branchId, userId } = session.user;
  const scope = `gymfort:pending-sale:${organizationId}:${branchId}:${userId}:${operation}`;
  if (runningWrites.has(scope)) throw new Error("La operación se está procesando.");
  runningWrites.add(scope);
  try {
    const bytes = new TextEncoder().encode(JSON.stringify(payload));
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    const fingerprint = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
    const pending = pendingWrites.get(scope) ?? readPending(scope);
    if (pending && pending.fingerprint !== fingerprint) {
      throw new Error("Hay una operación sin confirmar. Repite los datos originales y vuelve a enviarla antes de iniciar otra.");
    }

    const intent = pending ?? { fingerprint, key: `sales:${crypto.randomUUID()}` };
    pendingWrites.set(scope, intent);
    writePending(scope, intent);
    try {
      const result = await write(intent.key);
      clearPending(scope);
      return result;
    } catch (error) {
      if (!pending && error instanceof ApiResponseError && error.status === 400) {
        clearPending(scope);
        throw error;
      }
      const detail = error instanceof Error ? error.message : "Error de comunicación.";
      throw new Error(`No se pudo confirmar el resultado. Reintenta con los mismos datos para recuperar la operación. ${detail}`);
    }
  } finally {
    runningWrites.delete(scope);
  }
}

function readPending(scope: string): PendingWrite | undefined {
  try {
    const stored = sessionStorage.getItem(scope);
    return stored ? JSON.parse(stored) as PendingWrite : undefined;
  } catch {
    return undefined;
  }
}

function writePending(scope: string, intent: PendingWrite) {
  try {
    sessionStorage.setItem(scope, JSON.stringify(intent));
  } catch {
    // The in-memory intent protects retries in the current page.
  }
}

function clearPending(scope: string) {
  pendingWrites.delete(scope);
  try {
    sessionStorage.removeItem(scope);
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
}
