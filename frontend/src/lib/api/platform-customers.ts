import { apiFetch, normalizePayload } from "./client";
import type {
  PlatformCustomer,
	PlatformCustomerCreatePayload,
  PlatformCustomerPortfolio,
  PlatformCustomerUpdatePayload,
	ProvisionedPlatformCustomer,
} from "./platform-customer-types";

export async function getPlatformCustomers(
  accessToken: string,
): Promise<PlatformCustomerPortfolio> {
  return apiFetch<PlatformCustomerPortfolio>("/platform/customers", accessToken);
}

export async function createPlatformCustomer(
	accessToken: string,
	payload: PlatformCustomerCreatePayload,
): Promise<ProvisionedPlatformCustomer> {
	return apiFetch<ProvisionedPlatformCustomer>("/platform/customers", accessToken, {
		method: "POST",
		body: JSON.stringify(normalizePayload(payload)),
	});
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
