import { apiFetch } from "./client";
import type { AuthResponse, CurrentUser, StoredSession, UserConsole } from "./auth-types";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
const ACCESS_TOKEN_KEY = "gymos.accessToken";
const REFRESH_TOKEN_KEY = "gymos.refreshToken";
const CURRENT_USER_KEY = "gymos.currentUser";

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

export async function selectOrganization(accessToken: string, organizationId: string, reason: string): Promise<AuthResponse> {
  return normalizeAuthResponse(await apiFetch<AuthResponse>("/auth/select-organization", accessToken, {
    method: "POST",
    body: JSON.stringify({ organizationId, reason }),
  }));
}

export async function exitSupport(accessToken: string, refreshToken: string): Promise<AuthResponse> {
  return normalizeAuthResponse(await apiFetch<AuthResponse>("/auth/exit-support", accessToken, {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  }));
}

export async function acceptUserInvitation(token: string, password: string): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/auth/invitations/accept`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, password }),
    });
  } catch {
    throw new Error("No se pudo conectar con GymFort. Intenta de nuevo.");
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(formatLoginError(response, error));
  }
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
  const storage = browserSessionStorage();
  if (!storage) return;
  storage.setItem(ACCESS_TOKEN_KEY, normalized.accessToken);
  storage.setItem(REFRESH_TOKEN_KEY, normalized.refreshToken);
  storage.setItem(CURRENT_USER_KEY, JSON.stringify(normalized.user));
  clearLegacySharedSession();
}

export function storeCurrentUser(user: CurrentUser) {
  browserSessionStorage()?.setItem(CURRENT_USER_KEY, JSON.stringify(normalizeCurrentUser(user)));
}

export function getStoredSession(): StoredSession | null {
  const storage = browserSessionStorage();
  if (!storage) return null;
  migrateLegacySession(storage);

  const accessToken = storage.getItem(ACCESS_TOKEN_KEY);
  const refreshToken = storage.getItem(REFRESH_TOKEN_KEY);
  const currentUser = storage.getItem(CURRENT_USER_KEY);

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
  const storage = browserSessionStorage();
  storage?.removeItem(ACCESS_TOKEN_KEY);
  storage?.removeItem(REFRESH_TOKEN_KEY);
  storage?.removeItem(CURRENT_USER_KEY);
  clearLegacySharedSession();
}

function browserSessionStorage() {
  return typeof window === "undefined" ? null : window.sessionStorage;
}

function migrateLegacySession(storage: Storage) {
  if (storage.getItem(ACCESS_TOKEN_KEY) || typeof window === "undefined") return;
  const legacyAccessToken = window.localStorage.getItem(ACCESS_TOKEN_KEY);
  const legacyRefreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
  const legacyCurrentUser = window.localStorage.getItem(CURRENT_USER_KEY);
  if (legacyAccessToken && legacyRefreshToken && legacyCurrentUser) {
    storage.setItem(ACCESS_TOKEN_KEY, legacyAccessToken);
    storage.setItem(REFRESH_TOKEN_KEY, legacyRefreshToken);
    storage.setItem(CURRENT_USER_KEY, legacyCurrentUser);
  }
  clearLegacySharedSession();
}

function clearLegacySharedSession() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_TOKEN_KEY);
  window.localStorage.removeItem(CURRENT_USER_KEY);
}

function normalizeCurrentUser(user: CurrentUser): CurrentUser {
	if (user.accountScope !== "PLATFORM" && user.accountScope !== "TENANT") {
		throw new Error("La sesion no incluye un ambito de identidad valido.");
	}
	if (user.accountScope === "TENANT" && !user.organizationId) {
		throw new Error("La sesion tenant no incluye un gimnasio valido.");
	}
	if (user.accountScope === "TENANT" && user.supportMode) {
		throw new Error("Una sesion tenant no puede declararse como soporte de plataforma.");
	}
	if (user.accountScope === "PLATFORM" && !user.supportMode && (user.organizationId || user.branchId)) {
		throw new Error("La sesion de plataforma contiene un contexto tenant inesperado.");
	}
	if (user.accountScope === "PLATFORM" && user.supportMode
			&& (!user.organizationId || !user.supportExpiresAt)) {
		throw new Error("La sesion de soporte no incluye un contexto temporal valido.");
	}
  const permissions = user.permissions ?? [];
  const roles = user.roles ?? [];
  const platformAdmin = user.accountScope === "PLATFORM";
  const supportMode = Boolean(user.supportMode);
  return {
    ...user,
    permissions,
    roles,
    platformAdmin,
    supportMode,
		supportExpiresAt: user.supportExpiresAt ?? null,
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
  if (
    permissions.includes("coach.read")
    || permissions.includes("workout.manage.own")
    || permissions.includes("workout.manage.all")
  ) {
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
