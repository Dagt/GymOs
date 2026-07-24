const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

let refreshPromise: Promise<string | null> | null = null;

export async function apiFetch<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await authenticatedRequest(path, accessToken, init);
  if (response.status !== 401) {
    return readResponse<T>(response, path);
  }

  const refreshedToken = await refreshAccessToken();
  if (!refreshedToken) {
    clearStoredSession();
    return readResponse<T>(response, path);
  }

  const retry = await authenticatedRequest(path, refreshedToken, init);
  if (retry.status === 401) {
    clearStoredSession();
  }
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

async function refreshAccessToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  if (refreshPromise) return refreshPromise;

  refreshPromise = performRefresh().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function performRefresh(): Promise<string | null> {
  const refreshToken = window.localStorage.getItem("gymos.refreshToken");
  if (!refreshToken) return null;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    return null;
  }

  if (!response.ok) return null;

  const session = await response.json() as {
    accessToken?: string;
    refreshToken?: string;
    user?: unknown;
  };
  if (!session.accessToken) return null;

  window.localStorage.setItem("gymos.accessToken", session.accessToken);
  if (session.refreshToken) {
    window.localStorage.setItem("gymos.refreshToken", session.refreshToken);
  }
  if (session.user) {
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(session.user));
  }
  return session.accessToken;
}

function clearStoredSession() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem("gymos.accessToken");
  window.localStorage.removeItem("gymos.refreshToken");
  window.localStorage.removeItem("gymos.currentUser");
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
  if (status === 401) return "Tu sesión expiró o no es válida. Vuelve a iniciar sesión.";
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
