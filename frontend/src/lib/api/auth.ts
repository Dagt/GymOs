import { accessForSessionChange, apiFetch, currentTokenFor, publicApiFetch, sessionRequest } from "./client";
import { clearLegacySession, clearRefreshOperation, clearSession, sessionSlot, storeSession, withSessionLock } from "./session";
import type { AuthResponse, CurrentUser, UserConsole } from "./auth-types";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
export { clearSession, getStoredSession, storeCurrentUser } from "./session";
export { restoreSession } from "./client";

export async function login(username: string, password: string): Promise<AuthResponse> {
  clearLegacySession();
  const slot = sessionSlot(true);
  if (!slot) throw new Error("No fue posible preparar la sesión.");
  return withSessionLock(slot, async () => {
    const response = await sendSessionRequest("login", slot, {
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: username.trim(), password }),
    });
    if (response.status === 401) throw new Error("Usuario o contraseña incorrectos.");
    return installSession(response);
  });
}

export async function selectBranch(accessToken: string, branchId: string): Promise<AuthResponse> {
  return changeSession("select-branch", accessToken, { branchId });
}

export async function selectOrganization(accessToken: string, organizationId: string, reason: string): Promise<AuthResponse> {
  return changeSession("select-organization", accessToken, { organizationId, reason });
}

export async function exitSupport(accessToken: string): Promise<AuthResponse> {
  return changeSession("exit-support", accessToken);
}

async function changeSession(path: string, expected: string, body?: Record<string, string>) {
  const slot = sessionSlot();
  if (!slot) throw new Error("Tu sesión terminó. Inicia sesión de nuevo.");
  const token = await accessForSessionChange(expected);
  return withSessionLock(slot, async () => {
    const response = await sendSessionRequest(path, slot, {
      headers: { Authorization: `Bearer ${currentTokenFor(token)}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    return installSession(response);
  });
}

async function installSession(response: Response): Promise<AuthResponse> {
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? "No fue posible completar la operación de sesión.");
  }
  const session = normalizeAuthResponse(await response.json());
  clearRefreshOperation();
  storeSession(session);
  return session;
}

function sendSessionRequest(path: string, slot: string, init: RequestInit = {}) {
  return sessionRequest(path, slot, init).catch(() => {
    throw new Error("No se pudo conectar con GymFort. Revisa tu conexión e intenta de nuevo.");
  });
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

export async function acceptPasswordRecovery(token: string, password: string): Promise<void> {
  await publicApiFetch("/auth/password-recovery/accept", {
    method: "POST", cache: "no-store", body: JSON.stringify({ token, password }),
  });
}

export async function getCurrentUser(accessToken: string): Promise<CurrentUser> {
  return normalizeCurrentUser(await apiFetch<CurrentUser>("/auth/me", accessToken));
}

export async function logout(): Promise<void> {
  const slot = sessionSlot();
  if (!slot) return clearSession();
  await withSessionLock(slot, async () => {
    const response = await sendSessionRequest("logout", slot);
    if (!response.ok) throw new Error("No se pudo cerrar la sesión. Intenta de nuevo.");
    clearSession();
    window.dispatchEvent(new Event("gymfort.session-expired"));
  });
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
