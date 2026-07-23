import { apiFetch } from "./client";
import type { AuthResponse, CurrentUser, StoredSession, UserConsole } from "./auth-types";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export async function login(username: string, password: string): Promise<AuthResponse> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ username, password }),
    });
  } catch {
    throw new Error(`No se pudo conectar con el servidor (${API_BASE_URL}). Revisa que el backend este encendido.`);
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    if (response.status === 401) {
      throw new Error("Usuario o contrasena incorrectos.");
    }
    throw new Error(formatLoginError(response, error));
  }

  return normalizeAuthResponse(await response.json());
}

export async function selectBranch(accessToken: string, branchId: string): Promise<AuthResponse> {
  return normalizeAuthResponse(await apiFetch<AuthResponse>("/auth/select-branch", accessToken, {
    method: "POST",
    body: JSON.stringify({ branchId }),
  }));
}

export async function selectOrganization(accessToken: string, organizationId: string): Promise<AuthResponse> {
  return normalizeAuthResponse(await apiFetch<AuthResponse>("/auth/select-organization", accessToken, {
    method: "POST",
    body: JSON.stringify({ organizationId }),
  }));
}

export async function getCurrentUser(accessToken: string): Promise<CurrentUser> {
  return normalizeCurrentUser(await apiFetch<CurrentUser>("/auth/me", accessToken));
}

export async function logout(accessToken: string, refreshToken: string): Promise<void> {
  await apiFetch<void>("/auth/logout", accessToken, {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  });
}

export function storeSession(session: AuthResponse) {
  const normalized = normalizeAuthResponse(session);
  window.localStorage.setItem("gymos.accessToken", normalized.accessToken);
  window.localStorage.setItem("gymos.refreshToken", normalized.refreshToken);
  window.localStorage.setItem("gymos.currentUser", JSON.stringify(normalized.user));
}

export function storeCurrentUser(user: CurrentUser) {
  window.localStorage.setItem("gymos.currentUser", JSON.stringify(normalizeCurrentUser(user)));
}

export function getStoredSession(): StoredSession | null {
  const accessToken = window.localStorage.getItem("gymos.accessToken");
  const refreshToken = window.localStorage.getItem("gymos.refreshToken");
  const currentUser = window.localStorage.getItem("gymos.currentUser");

  if (!accessToken || !refreshToken || !currentUser) {
    return null;
  }

  try {
    const parsed = JSON.parse(currentUser) as CurrentUser;
    return {
      accessToken,
      refreshToken,
      user: normalizeCurrentUser(parsed),
    };
  } catch {
    clearSession();
    return null;
  }
}

export function clearSession() {
  window.localStorage.removeItem("gymos.accessToken");
  window.localStorage.removeItem("gymos.refreshToken");
  window.localStorage.removeItem("gymos.currentUser");
}

function normalizeCurrentUser(user: CurrentUser): CurrentUser {
  const permissions = user.permissions ?? [];
  const roles = user.roles ?? [];
  const platformAdmin = Boolean(user.platformAdmin || permissions.includes("platform.admin"));
  const supportMode = Boolean(user.supportMode);
  return {
    ...user,
    permissions,
    roles,
    platformAdmin,
    supportMode,
    console: inferConsole(permissions, roles, platformAdmin, supportMode, user.console),
  };
}

function normalizeAuthResponse(session: AuthResponse): AuthResponse {
  return {
    ...session,
    user: normalizeCurrentUser(session.user),
  };
}

function inferConsole(
  permissions: string[],
  roles: string[],
  platformAdmin: boolean,
  supportMode: boolean,
  currentConsole?: UserConsole,
): UserConsole {
  if (platformAdmin && !supportMode) {
    return "PLATFORM_OWNER";
  }
  if (platformAdmin && supportMode) {
    return "TENANT_OWNER";
  }
  if (currentConsole && isKnownConsole(currentConsole)) {
    return currentConsole;
  }
  const normalizedRoles = roles.map((role) => role.toLowerCase());
  if (normalizedRoles.some((role) => role.includes("dueno") || role.includes("owner") || role.includes("administrador"))) {
    return "TENANT_OWNER";
  }
  if (normalizedRoles.some((role) => role.includes("gerente"))) {
    return "TENANT_MANAGER";
  }
  if (permissions.includes("sale.create") || permissions.includes("payment.manual.create") || permissions.includes("client.create")) {
    return "RECEPTION";
  }
  if (permissions.includes("coach.read") || permissions.includes("workout.manage")) {
    return "COACH";
  }
  return "TENANT_MANAGER";
}

function isKnownConsole(value: string): value is UserConsole {
  return ["PLATFORM_OWNER", "TENANT_OWNER", "TENANT_MANAGER", "RECEPTION", "COACH"].includes(value);
}

function formatLoginError(response: Response, error: unknown) {
  const payload = typeof error === "object" && error !== null ? error as { code?: string; message?: string; correlationId?: string } : null;
  const message = payload?.message || "No fue posible iniciar sesion.";
  const code = payload?.code ? ` Codigo: ${payload.code}.` : "";
  const correlationId = payload?.correlationId ? ` CorrelationId: ${payload.correlationId}.` : "";
  return `${message} (${response.status} ${response.statusText || "HTTP"} en /auth/login).${code}${correlationId}`;
}
