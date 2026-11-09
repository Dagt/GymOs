import type { KioskAutopayMandate, KioskSessionResponse } from "./api/selfservice-types";

export const KIOSK_BROWSER_SESSION_KEY = "gymos.kioskBrowserSession";
export const KIOSK_AUTOPAY_RETURN_KEY = "gymos.kioskAutopayReturn";
export const KIOSK_PAYMENT_RETURN_KEY = "gymos.kioskPaymentReturn";

const MAX_RETURN_AGE_MS = 60 * 60 * 1000;

export type KioskAutopayReturnContext = {
  sessionId: string;
  membershipId: string;
  mandateId: string;
  providerSessionId: string | null;
  returnPath: string;
  startedAt: string;
};

export type KioskPaymentReturnContext = {
  sessionId: string;
  paymentId: string;
  saleId: string;
  providerSessionId: string | null;
  returnPath: string;
  startedAt: string;
};

export type KioskAutopayResult = "SUCCESS" | "PENDING" | "FAILED";

type BrowserStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function writeKioskAutopayReturn(
  storage: BrowserStorage,
  context: KioskAutopayReturnContext,
) {
  storage.removeItem(KIOSK_PAYMENT_RETURN_KEY);
  storage.setItem(KIOSK_AUTOPAY_RETURN_KEY, JSON.stringify(context));
}

export function writeKioskPaymentReturn(
  storage: BrowserStorage,
  context: KioskPaymentReturnContext,
) {
  storage.removeItem(KIOSK_AUTOPAY_RETURN_KEY);
  storage.setItem(KIOSK_PAYMENT_RETURN_KEY, JSON.stringify(context));
}

export function readKioskAutopayReturn(
  storage: BrowserStorage,
  now = Date.now(),
): KioskAutopayReturnContext | null {
  const value = storage.getItem(KIOSK_AUTOPAY_RETURN_KEY);
  if (!value) return null;
  try {
    const context = JSON.parse(value) as Partial<KioskAutopayReturnContext>;
    const startedAt = new Date(context.startedAt ?? "").getTime();
    if (
      !context.sessionId
      || !context.membershipId
      || !context.mandateId
      || !safeReturnPath(context.returnPath)
      || !Number.isFinite(startedAt)
      || startedAt > now + 60_000
      || now - startedAt > MAX_RETURN_AGE_MS
    ) {
      storage.removeItem(KIOSK_AUTOPAY_RETURN_KEY);
      return null;
    }
    return {
      sessionId: context.sessionId,
      membershipId: context.membershipId,
      mandateId: context.mandateId,
      providerSessionId: context.providerSessionId?.trim() || null,
      returnPath: context.returnPath,
      startedAt: context.startedAt!,
    };
  } catch {
    storage.removeItem(KIOSK_AUTOPAY_RETURN_KEY);
    return null;
  }
}

export function readKioskPaymentReturn(
  storage: BrowserStorage,
  now = Date.now(),
): KioskPaymentReturnContext | null {
  const value = storage.getItem(KIOSK_PAYMENT_RETURN_KEY);
  if (!value) return null;
  try {
    const context = JSON.parse(value) as Partial<KioskPaymentReturnContext>;
    if (
      !context.sessionId
      || !context.paymentId
      || !context.saleId
      || !validCommonContext(context, now)
    ) {
      storage.removeItem(KIOSK_PAYMENT_RETURN_KEY);
      return null;
    }
    return {
      sessionId: context.sessionId,
      paymentId: context.paymentId,
      saleId: context.saleId,
      providerSessionId: context.providerSessionId?.trim() || null,
      returnPath: context.returnPath!,
      startedAt: context.startedAt!,
    };
  } catch {
    storage.removeItem(KIOSK_PAYMENT_RETURN_KEY);
    return null;
  }
}

export function readKioskBrowserSession(
  storage: BrowserStorage,
  now = Date.now(),
): KioskSessionResponse | null {
  const value = storage.getItem(KIOSK_BROWSER_SESSION_KEY);
  if (!value) return null;
  try {
    const session = JSON.parse(value) as KioskSessionResponse;
    const expires = Date.parse(session.expiresAt);
    if (
      !session.sessionId
      || !session.sessionToken
      || !session.expiresAt
      || !Number.isFinite(expires) || expires <= now
    ) {
      clearKioskBrowserState(storage);
      return null;
    }
    return session;
  } catch {
    clearKioskBrowserState(storage);
    return null;
  }
}

export function matchesStripeCheckoutSession(
  context: Pick<KioskAutopayReturnContext, "providerSessionId">,
  returnedSessionId: string | null,
) {
  return Boolean(context.providerSessionId && context.providerSessionId === returnedSessionId);
}

export function classifyKioskAutopayResult(
  mandate: KioskAutopayMandate | null | undefined,
): KioskAutopayResult {
  if (mandate?.status === "ACTIVE") return "SUCCESS";
  if (!mandate || mandate.status === "PENDING_SETUP") return "PENDING";
  return "FAILED";
}

export function clearKioskAutopayReturn(storage: BrowserStorage) {
  storage.removeItem(KIOSK_AUTOPAY_RETURN_KEY);
}

export function clearKioskPaymentReturn(storage: BrowserStorage) {
  storage.removeItem(KIOSK_PAYMENT_RETURN_KEY);
}

export function clearKioskBrowserState(storage: BrowserStorage) {
  storage.removeItem(KIOSK_BROWSER_SESSION_KEY);
  clearKioskAutopayReturn(storage);
  clearKioskPaymentReturn(storage);
}

function validCommonContext(
  context: { returnPath?: string; startedAt?: string },
  now: number,
) {
  const startedAt = new Date(context.startedAt ?? "").getTime();
  return safeReturnPath(context.returnPath)
    && Number.isFinite(startedAt)
    && startedAt <= now + 60_000
    && now - startedAt <= MAX_RETURN_AGE_MS;
}

function safeReturnPath(value: string | undefined): value is string {
  return Boolean(value && /^\/kiosk\/[a-zA-Z0-9_-]+$/.test(value) && value !== "/kiosk/launch");
}
