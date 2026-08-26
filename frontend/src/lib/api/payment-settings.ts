import { apiFetch } from "./client";
import { DEFAULT_PAGE_SIZE } from "../pagination";
import type {
  MercadoPagoTerminal,
  PaymentConfiguration,
  PaymentProviderLink,
  PaymentHistoryPage,
  PaymentWebhookHistory,
  PaymentOperations,
  RecurringChargeHistory,
  RecurringMandateHistory,
  UpdatePaymentConfigurationPayload,
} from "./payment-settings-types";

export function getPaymentConfiguration(accessToken: string): Promise<PaymentConfiguration> {
  return apiFetch<PaymentConfiguration>("/payment-settings", accessToken);
}

export function updatePaymentConfiguration(
  accessToken: string,
  payload: UpdatePaymentConfigurationPayload,
): Promise<PaymentConfiguration> {
  return apiFetch<PaymentConfiguration>("/payment-settings", accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function beginStripeOnboarding(accessToken: string): Promise<PaymentProviderLink> {
  return apiFetch<PaymentProviderLink>("/payment-settings/stripe/connect", accessToken, { method: "POST" });
}

export function refreshStripeConnection(accessToken: string): Promise<PaymentConfiguration> {
  return apiFetch<PaymentConfiguration>("/payment-settings/stripe/refresh", accessToken, { method: "POST" });
}

export function beginMercadoPagoOnboarding(accessToken: string): Promise<PaymentProviderLink> {
  return apiFetch<PaymentProviderLink>("/payment-settings/mercado-pago/connect", accessToken, { method: "POST" });
}

export function getMercadoPagoTerminals(accessToken: string): Promise<MercadoPagoTerminal[]> {
  return apiFetch<MercadoPagoTerminal[]>("/payment-settings/mercado-pago/terminals", accessToken);
}

export function selectMercadoPagoTerminal(
  accessToken: string,
  terminalId: string,
): Promise<PaymentConfiguration> {
  return apiFetch<PaymentConfiguration>("/payment-settings/mercado-pago/terminal", accessToken, {
    method: "PUT",
    body: JSON.stringify({ terminalId }),
  });
}

export function getRecurringMandateHistory(
  accessToken: string,
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PaymentHistoryPage<RecurringMandateHistory>> {
  return apiFetch(`/payment-settings/history/mandates?page=${page}&size=${size}`, accessToken);
}

export function getRecurringChargeHistory(
  accessToken: string,
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PaymentHistoryPage<RecurringChargeHistory>> {
  return apiFetch(`/payment-settings/history/recurring-charges?page=${page}&size=${size}`, accessToken);
}

export function getPaymentWebhookHistory(
  accessToken: string,
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<PaymentHistoryPage<PaymentWebhookHistory>> {
  return apiFetch(`/payment-settings/history/webhooks?page=${page}&size=${size}`, accessToken);
}

export function getPaymentOperations(accessToken: string): Promise<PaymentOperations> {
  return apiFetch<PaymentOperations>("/payment-settings/operations", accessToken);
}
