import { apiFetch } from "./client";

export type ClientLifecycleSettings = {
  organizationId: string;
  registrationFeeEnabled: boolean;
  registrationFeeCents: number;
  registrationFeeLabel: string;
  currency: string;
  registrationRechargeMonths: number;
  autoInactivationEnabled: boolean;
  inactivityMonths: number;
  updatedAt: string | null;
  version: number;
};

export type UpdateClientLifecycleSettings = Pick<
  ClientLifecycleSettings,
  | "registrationFeeEnabled"
  | "registrationFeeCents"
  | "registrationFeeLabel"
  | "registrationRechargeMonths"
  | "autoInactivationEnabled"
  | "inactivityMonths"
>;

export function getClientLifecycleSettings(accessToken: string): Promise<ClientLifecycleSettings> {
  return apiFetch<ClientLifecycleSettings>("/client-lifecycle-settings", accessToken);
}

export function updateClientLifecycleSettings(
  accessToken: string,
  payload: UpdateClientLifecycleSettings,
): Promise<ClientLifecycleSettings> {
  return apiFetch<ClientLifecycleSettings>("/client-lifecycle-settings", accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}
