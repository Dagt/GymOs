import { apiFetch } from "./client";
import type { OwnerDashboard } from "./reporting-types";

export async function getOwnerDashboard(
  accessToken: string,
  fromDate?: string,
  toDate?: string,
): Promise<OwnerDashboard> {
  const params = new URLSearchParams();
  if (fromDate) {
    params.set("fromDate", fromDate);
  }
  if (toDate) {
    params.set("toDate", toDate);
  }
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<OwnerDashboard>(`/reports/owner-dashboard${suffix}`, accessToken);
}
