import type { AuthResponse } from "./auth-types";
import {
  accessClaims, clearLegacySession, clearRefreshOperation, clearSession, getStoredSession,
  refreshOperation, sameAccessContext, sessionSlot, storeSession, withSessionLock,
} from "./session";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
let refreshPromise: Promise<RefreshResult> | null = null;
let contextChanged = false;

type RefreshResult =
  | { status: "refreshed"; accessToken: string }
  | { status: "rejected" | "unavailable" | "changed" };

const expiredMessage = "Tu sesión terminó. Inicia sesión de nuevo.";
const unavailableMessage = "No se pudo renovar la sesión. Revisa tu conexión e intenta de nuevo.";
const contextMessage = "La sesión cambió en otra pestaña. Recarga el panel antes de continuar.";

export async function apiFetch<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  let token = currentTokenFor(accessToken);
  const expiresAt = accessClaims(token)?.exp;
  if (expiresAt && expiresAt * 1000 <= Date.now() + 30_000) {
    const refresh = await refreshAccessToken(token);
    if (refresh.status !== "unavailable") token = requireRefreshed(refresh);
  }
  const response = await authenticatedRequest(path, token, init);
  if (response.status !== 401) return readResponse<T>(response, path);

  const refresh = await refreshAccessToken(token);
  const renewed = requireRefreshed(refresh);
  return readResponse<T>(await authenticatedRequest(path, currentTokenFor(renewed), init), path);
}

export function currentTokenFor(expected: string) {
  if (contextChanged) throw new Error(contextMessage);
  const current = getStoredSession()?.accessToken;
  if (!current) throw new Error(expiredMessage);
  if (!sameAccessContext(expected, current)) throw new Error(contextMessage);
  return current;
}

function requireRefreshed(result: RefreshResult) {
  if (result.status === "refreshed") return result.accessToken;
  throw new Error(result.status === "changed" ? contextMessage
    : result.status === "unavailable" ? unavailableMessage : expiredMessage);
}

export async function restoreSession() {
  clearLegacySession();
  if (getStoredSession()) return "refreshed";
  return (await refreshAccessToken()).status;
}

export async function sessionRequest(path: string, slot: string, init: RequestInit = {}) {
  return fetch(`${API_BASE_URL}/auth/${path}`, {
    ...init,
    method: "POST",
    credentials: "include",
    cache: "no-store",
    headers: { "X-GymFort-Session": slot, ...init.headers },
  });
}

export async function accessForSessionChange(expected: string) {
  const access = currentTokenFor(expected);
  const expiresAt = accessClaims(access)?.exp;
  return expiresAt && expiresAt * 1000 <= Date.now() + 30_000
    ? requireRefreshed(await refreshAccessToken(access))
    : access;
}

export async function publicApiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const isFormData = init.body instanceof FormData;
  return readResponse<T>(await performRequest(path, {
    ...init,
    credentials: "omit",
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      ...init.headers,
    },
  }), path);
}

export function normalizePayload<T extends Record<string, unknown>>(payload: T): T {
  return Object.fromEntries(
    Object.entries(payload).map(([key, value]) => [
      key,
      typeof value === "string" && value.trim() === "" ? null : value,
    ]),
  ) as T;
}

export function withIdempotency<T extends { idempotencyKey?: string | null }>(
  payload: T,
  ...parts: string[]
): T {
  if (payload.idempotencyKey) {
    return payload;
  }
  return {
    ...payload,
    idempotencyKey: operationKey(...parts),
  };
}

async function authenticatedRequest(path: string, accessToken: string, init: RequestInit) {
  const isFormData = init.body instanceof FormData;
  return performRequest(path, {
    ...init,
    credentials: "omit",
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      ...init.headers,
      Authorization: `Bearer ${accessToken}`,
    },
  });
}

async function performRequest(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(`${API_BASE_URL}${path}`, init);
  } catch {
    throw new Error(
      `No se pudo conectar con el servidor (${API_BASE_URL}). Revisa que el backend esté encendido y que la URL de API sea correcta.`,
    );
  }
}

async function readResponse<T>(response: Response, path: string): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(formatApiError(response, path, error));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function refreshAccessToken(expected?: string): Promise<RefreshResult> {
  if (contextChanged) return Promise.resolve({ status: "changed" });
  const slot = sessionSlot();
  if (!slot) return Promise.resolve({ status: "rejected" });
  const current = getStoredSession()?.accessToken;
  if (expected && current && expected !== current) {
    return Promise.resolve(sameAccessContext(expected, current)
      ? { status: "refreshed", accessToken: current }
      : { status: "changed" });
  }

  refreshPromise ??= withSessionLock(slot, () => performRefresh(slot, expected)).finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function performRefresh(slot: string, expected?: string): Promise<RefreshResult> {
  if (sessionSlot() !== slot) return { status: "rejected" };
  const operation = refreshOperation();
  for (let attempt = 0; attempt < 2; attempt++) {
    const response = await sessionRequest("refresh", slot, {
      headers: { "X-GymFort-Refresh-Request": operation },
    }).catch(() => null);
    if (response?.status === 401) {
      clearSession();
      window.dispatchEvent(new Event("gymfort.session-expired"));
      return { status: "rejected" };
    }
    if (response?.ok) {
      const next = await response.json().catch(() => null) as AuthResponse | null;
      if (next?.accessToken && next.user && sessionSlot() === slot) {
        clearRefreshOperation();
        if (expected && !sameAccessContext(expected, next.accessToken)) {
          contextChanged = true;
          window.dispatchEvent(new Event("gymfort.session-context-changed"));
          return { status: "changed" };
        }
        storeSession(next);
        return { status: "refreshed", accessToken: next.accessToken };
      }
    } else if (response && response.status !== 409) {
      return { status: "unavailable" };
    }
    if (attempt === 0) await new Promise((resolve) => setTimeout(resolve, 150));
  }
  return { status: "unavailable" };
}

function operationKey(...parts: string[]) {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}`;
  return [...parts, random].join(":");
}

function formatApiError(response: Response, path: string, error: unknown) {
  const payload = isApiErrorPayload(error) ? error : null;
  const message = payload?.message || fallbackMessage(response.status);
  const code = payload?.code ? ` Código: ${payload.code}.` : "";
  const correlationId = payload?.correlationId ? ` CorrelationId: ${payload.correlationId}.` : "";
  return `${message} (${response.status} ${response.statusText || "HTTP"} en ${path}).${code}${correlationId}`;
}

function fallbackMessage(status: number) {
  if (status === 400) return "La solicitud tiene datos inválidos.";
  if (status === 401) return "La sesión de esta pestaña no es válida. Inicia sesión nuevamente en esta pestaña.";
  if (status === 403) return "Tu usuario no tiene permiso para realizar esta acción.";
  if (status === 404) return "No se encontró la información solicitada.";
  if (status >= 500) return "El servidor tuvo un problema al procesar la operación.";
  return "No fue posible completar la operación.";
}

function isApiErrorPayload(value: unknown): value is {
  code?: string;
  message?: string;
  correlationId?: string;
} {
  return typeof value === "object" && value !== null;
}
