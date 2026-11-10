import { ApiResponseError } from "./api/client";

const fieldLabels = {
  method: "Método de pago",
  reference: "Referencia",
  clientId: "Socio",
  packagePlanId: "Paquete",
  planId: "Plan",
  targetPlanId: "Plan",
};

export function contractErrorMessage(exception: unknown, fallback: string) {
  if (!(exception instanceof ApiResponseError)) return exception instanceof Error ? exception.message : fallback;
  const payload = exception.payload;
  const support = payload?.correlationId ? ` Referencia de soporte: ${payload.correlationId}.` : "";
  if (payload?.code === "VALIDATION_ERROR") {
    for (const [field, label] of Object.entries(fieldLabels)) {
      const detail = payload.details?.[field];
      if (typeof detail === "string") return `${label}: ${detail}.${support}`;
    }
    return "No fue posible registrar la operación. Verifica los datos e inténtalo nuevamente." + support;
  }
  return payload?.message ? payload.message + support : exception.message;
}
