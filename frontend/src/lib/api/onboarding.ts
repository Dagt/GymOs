import { apiFetch, normalizePayload } from "./client";
import type { OnboardingChecklist, PilotSetupPayload, PilotSetupResult } from "./onboarding-types";

export async function getOnboardingChecklist(accessToken: string): Promise<OnboardingChecklist> {
  return apiFetch<OnboardingChecklist>("/onboarding/checklist", accessToken);
}

export async function applyPilotSetup(
  accessToken: string,
  payload: PilotSetupPayload,
): Promise<PilotSetupResult> {
  return apiFetch<PilotSetupResult>("/onboarding/pilot-setup", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
