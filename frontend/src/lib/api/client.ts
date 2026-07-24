const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

let refreshPromise: Promise<RefreshResult> | null = null;

const REFRESH_MARGIN_MILLISECONDS = 30_000;

type RefreshResult =
  | { status: "refreshed"; accessToken: string }
  | { status: "rejected" }
  | { status: "unavailable" };

export async function apiFetch<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  let tokenUsed = currentAccessToken() ?? accessToken;

  // Refresh before a protected request when the JWT is already expired or close
  // to expiring. This is especially important for POST/PUT operations filled out
  // in long forms, such as registering a new member.
  if (tokenNeedsRefresh(tokenUsed)) {
    const proactiveRefresh = await refreshAccessToken(tokenUsed);
    if (proactiveRefresh.status === "refreshed") {
      tokenUsed = proactiveRefresh.accessToken;
    }
  }

  let response = await authenticatedRequest(path, tokenUsed, init);
  if (response.status !== 401) {
    return readResponse<T>(response, path);
  }

  // Another request or browser tab may already have refreshed the session.
  // Retry with the newest stored token before consuming the rotating refresh token.
  const newerToken = currentAccessToken();
  if (newerToken && newerToken !== tokenUsed) {
    tokenUsed = newerToken;
    response = await authenticatedRequest(path, tokenUsed, init);
    if (response.status !== 401) {
      return readResponse<T>(response, path);
    }
  }

  const refresh = await refreshAccessToken(tokenUsed);
  if (refresh.status !== "refreshed") {
    // A failed endpoint must not erase the complete browser session. The caller
    // receives the original API error and other modules remain usable.
    return readResponse<T>(response, path);
  }

  const retry = await authenticatedRequest(path, refresh.accessToken, init);
  return readResponse<T>(retry, path);
}

export async function publicApiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const isFormData = init.body instanceof FormData;
  return readResponse<T>(await performRequest(path, {
    ...init,
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
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      Authorization: `Bearer ${accessToken}`,
      ...init.headers,
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

async function refreshAccessToken(expectedAccessToken: string): Promise<RefreshResult> {
  if (typeof window === "undefined") return { status: "unavailable" };

  const current = currentAccessToken();
  if (current && current !== expectedAccessToken) {
    return { status: "refreshed", accessToken: current };
  }

  if (refreshPromise) return refreshPromise;

  refreshPromise = performRefresh(expectedAccessToken).finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function performRefresh(expectedAccessToken: string): Promise<RefreshResult> {
  const refreshToken = window.localStorage.getItem("gymos.refreshToken");
  if (!refreshToken) return { status: "rejected" };

  const tokenBeforeRequest = currentAccessToken();
  if (tokenBeforeRequest && tokenBeforeRequest !== expectedAccessToken) {
    return { status: "refreshed", accessToken: tokenBeforeRequest };
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    return { status: "unavailable" };
  }

  if (!response.ok) {
    // A different tab can win the refresh-token rotation while this request is
    // in flight. Prefer its newly stored access token over treating the session
    // as invalid.
    const tokenAfterFailure = currentAccessToken();
    if (tokenAfterFailure && tokenAfterFailure !== expectedAccessToken) {
      return { status: "refreshed", accessToken: tokenAfterFailure };
    }
    if ([400, 401, 403].includes(response.status)) return { status: "rejected" };
    return { status: "unavailable" };
  }

  const session = await response.json().catch(() => null) as {
    accessToken?: string;
    refreshToken?: string;
    user?: unknown;
  } | null;
  if (!session?.accessToken || !session.refreshToken) {
    return { status: "unavailable" };
  }

  window.localStorage.setItem("gymos.accessToken", session.accessToken);
  window.localStorage.setItem("gymos.refreshToken", session.refreshToken);
  if (session.user) {
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(session.user));
  }
  window.dispatchEvent(new Event("gymos.session-refreshed"));
  return { status: "refreshed", accessToken: session.accessToken };
}

function currentAccessToken() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem("gymos.accessToken");
}

function tokenNeedsRefresh(accessToken: string) {
  const expiresAt = accessTokenExpiration(accessToken);
  return expiresAt !== null && expiresAt <= Date.now() + REFRESH_MARGIN_MILLISECONDS;
}

function accessTokenExpiration(accessToken: string): number | null {
  if (typeof window === "undefined" || typeof TextDecoder === "undefined") return null;
  const parts = accessToken.split(".");
  if (parts.length !== 3) return null;

  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const binary = window.atob(padded);
    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { exp?: unknown };
    return typeof payload.exp === "number" ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
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
  if (status === 401) return "El servidor no pudo autenticar esta operación después de intentar renovar la sesión.";
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
