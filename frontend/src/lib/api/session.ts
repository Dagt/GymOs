import type { AuthResponse, CurrentUser, StoredSession } from "./auth-types";

export const SESSION_SLOT_KEY = "gymfort.session.slot";
const REFRESH_OPERATION_KEY = "gymfort.session.refresh-operation";
let session: StoredSession | null = null;

export function getStoredSession() {
  return session;
}

export function storeSession(value: AuthResponse) {
  session = { accessToken: value.accessToken, user: value.user };
  window.dispatchEvent(new Event("gymos.session-refreshed"));
}

export function storeCurrentUser(user: CurrentUser) {
  if (session) session = { ...session, user };
}

export function clearSession() {
  session = null;
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(SESSION_SLOT_KEY);
  clearRefreshOperation();
  clearLegacySession();
}

export function clearLegacySession() {
  if (typeof window === "undefined") return;
  for (const key of ["gymos.accessToken", "gymos.refreshToken", "gymos.currentUser"]) {
    window.sessionStorage.removeItem(key);
    window.localStorage.removeItem(key);
  }
}

export function sessionSlot(create = false) {
  if (typeof window === "undefined") return null;
  const current = window.sessionStorage.getItem(SESSION_SLOT_KEY);
  if (current || !create) return current;
  const next = crypto.randomUUID();
  window.sessionStorage.setItem(SESSION_SLOT_KEY, next);
  return next;
}

export function refreshOperation() {
  const stored = window.sessionStorage.getItem(REFRESH_OPERATION_KEY);
  if (stored) return stored;
  const operation = crypto.randomUUID();
  window.sessionStorage.setItem(REFRESH_OPERATION_KEY, operation);
  return operation;
}

export function clearRefreshOperation() {
  window.sessionStorage.removeItem(REFRESH_OPERATION_KEY);
}

export async function withSessionLock<T>(slot: string, action: () => Promise<T>): Promise<T> {
  if (navigator.locks) return await navigator.locks.request("gymfort.session." + slot, action);
  return action();
}

export function accessClaims(token: string) {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const binary = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="));
    return JSON.parse(binary) as { sid?: string; ctx?: number; exp?: number };
  } catch {
    return null;
  }
}

export function sameAccessContext(previous: string, current: string) {
  if (previous === current) return true;
  const a = accessClaims(previous);
  const b = accessClaims(current);
  return Boolean(a?.sid && a.sid === b?.sid && a.ctx === b.ctx);
}
