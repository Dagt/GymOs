const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type CurrentUser = {
  userId: string;
  organizationId: string;
  branchId: string | null;
  username: string;
  fullName: string;
  permissions: string[];
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: CurrentUser;
};

export type StoredSession = {
  accessToken: string;
  refreshToken: string;
  user: CurrentUser;
};

export type OrganizationSummary = {
  id: string;
  legalName: string;
  tradeName: string;
  status: string;
  timezone: string;
  defaultCurrency: string;
};

export type BranchSummary = {
  id: string;
  name: string;
  code: string;
  status: string;
  timezone: string;
  accessScope: string;
};

export async function login(username: string, password: string): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? "No fue posible iniciar sesion.");
  }

  return response.json();
}

export async function selectBranch(accessToken: string, branchId: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/auth/select-branch", accessToken, {
    method: "POST",
    body: JSON.stringify({ branchId }),
  });
}

export async function getCurrentOrganization(accessToken: string): Promise<OrganizationSummary> {
  return apiFetch<OrganizationSummary>("/organizations/current", accessToken);
}

export async function getBranches(accessToken: string): Promise<BranchSummary[]> {
  return apiFetch<BranchSummary[]>("/organizations/current/branches", accessToken);
}

export function storeSession(session: AuthResponse) {
  window.localStorage.setItem("gymos.accessToken", session.accessToken);
  window.localStorage.setItem("gymos.refreshToken", session.refreshToken);
  window.localStorage.setItem("gymos.currentUser", JSON.stringify(session.user));
}

export function getStoredSession(): StoredSession | null {
  const accessToken = window.localStorage.getItem("gymos.accessToken");
  const refreshToken = window.localStorage.getItem("gymos.refreshToken");
  const currentUser = window.localStorage.getItem("gymos.currentUser");

  if (!accessToken || !refreshToken || !currentUser) {
    return null;
  }

  try {
    return {
      accessToken,
      refreshToken,
      user: JSON.parse(currentUser) as CurrentUser,
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

async function apiFetch<T>(
  path: string,
  accessToken: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
      ...init.headers,
    },
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message ?? "No fue posible completar la operacion.");
  }

  return response.json();
}
