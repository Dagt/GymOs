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

export function storeSession(session: AuthResponse) {
  window.localStorage.setItem("gymos.accessToken", session.accessToken);
  window.localStorage.setItem("gymos.refreshToken", session.refreshToken);
  window.localStorage.setItem("gymos.currentUser", JSON.stringify(session.user));
}
