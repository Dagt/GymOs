import { apiFetch, normalizePayload } from "./client";
import type {
  PlatformCustomer,
  PlatformCustomerPortfolio,
  PlatformCustomerUpdatePayload,
} from "./platform-customer-types";

export async function getPlatformCustomers(
  accessToken: string,
): Promise<PlatformCustomerPortfolio> {
  return apiFetch<PlatformCustomerPortfolio>("/platform/customers", accessToken);
}

export async function updatePlatformCustomer(
  accessToken: string,
  organizationId: string,
  payload: PlatformCustomerUpdatePayload,
): Promise<PlatformCustomer> {
  return apiFetch<PlatformCustomer>(`/platform/customers/${organizationId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
