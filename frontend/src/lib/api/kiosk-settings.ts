import { apiFetch } from "./client";

export type KioskBranchCapabilities = {
  clientRegistrationAllowed: boolean;
	  membershipEnrollmentAllowed: boolean;
  membershipRenewalAllowed: boolean;
  membershipPlanChangeAllowed: boolean;
  resourceBookingAllowed: boolean;
  wellnessBookingAllowed: boolean;
  profileUpdateAllowed: boolean;
  autopayAllowed: boolean;
  onlineCardPaymentsReady: boolean;
  recurringCardPaymentsReady: boolean;
};

export type KioskBranchSettings = {
  branchId: string;
  branchName: string;
  status: string;
  capabilities: KioskBranchCapabilities;
  updatedAt: string | null;
};

export type UpdateKioskBranchSettings = {
  clientRegistration: boolean;
	  membershipEnrollment: boolean;
  membershipRenewal: boolean;
  membershipPlanChange: boolean;
  resourceBooking: boolean;
  wellnessBooking: boolean;
  profileUpdate: boolean;
  autopay: boolean;
};

export function getKioskBranchSettings(accessToken: string): Promise<KioskBranchSettings[]> {
  return apiFetch<KioskBranchSettings[]>("/kiosk-settings/branches", accessToken);
}

export function updateKioskBranchSettings(
  accessToken: string,
  branchId: string,
  payload: UpdateKioskBranchSettings,
): Promise<KioskBranchSettings> {
  return apiFetch<KioskBranchSettings>(`/kiosk-settings/branches/${branchId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}
