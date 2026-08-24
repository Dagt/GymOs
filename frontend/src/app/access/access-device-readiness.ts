import type { AccessDevice } from "../../lib/api/access-types";

export type AccessDeviceReadiness = {
  state: "READY" | "INCOMPLETE" | "INVALID" | "DIAGNOSTIC";
  label: string;
  countLabel: string;
  detail: string;
  tone: "success" | "warning" | "danger" | "neutral";
};

export function accessDeviceReadiness(
  device: AccessDevice,
  requiredBenefitIds: string[],
): AccessDeviceReadiness {
  const count = new Set(requiredBenefitIds).size;
  if (device.provider.trim().toUpperCase() === "SIMULATED") {
    return {
      state: "DIAGNOSTIC",
      label: "Diagnóstico",
      countLabel: `${count} beneficio${count === 1 ? "" : "s"} ACCESS`,
      detail:
        "SIMULATED sirve para diagnóstico y no se considera listo para operación física.",
      tone: "neutral",
    };
  }
  if (count === 1) {
    return {
      state: "READY",
      label: "Correcto",
      countLabel: "1/1 beneficio ACCESS",
      detail: "El punto exige exactamente un beneficio ACCESS.",
      tone: "success",
    };
  }
  if (count === 0) {
    return {
      state: "INCOMPLETE",
      label: "Configuración incompleta",
      countLabel: "0/1 beneficios ACCESS",
      detail:
        "Selecciona exactamente un beneficio ACCESS antes de considerar este punto listo para producción.",
      tone: "warning",
    };
  }
  return {
    state: "INVALID",
    label: "Configuración inválida",
    countLabel: `${count}/1 beneficios ACCESS`,
    detail:
      "Hay varios beneficios legacy. Elige cuál conservar; GymFort no seleccionará uno automáticamente.",
    tone: "danger",
  };
}

export function initialAccessBenefitSelection(requiredBenefitIds: string[]) {
  const uniqueIds = [...new Set(requiredBenefitIds)];
  return uniqueIds.length === 1 ? uniqueIds[0] : "";
}
