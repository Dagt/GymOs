import type { PlatformOwnerAccess } from "@/lib/api/platform-customer-types";

export type InvitationDeliveryStatus = PlatformOwnerAccess["emailDeliveryStatus"];

export function invitationDeliveryTitle(status: InvitationDeliveryStatus) {
  if (status === "SENT") return "Invitación enviada por ZeptoMail";
  if (status === "NOT_CONFIGURED") return "Invitación creada; falta configurar ZeptoMail";
  if (status === "FAILED") return "Invitación creada; el correo falló";
  return "Invitación creada y pendiente de confirmación";
}

export function invitationDeliveryDescription(status: InvitationDeliveryStatus, email: string) {
  if (status === "SENT") return `ZeptoMail aceptó el correo dirigido a ${email}.`;
  if (status === "NOT_CONFIGURED") {
    return `No se pudo enviar a ${email} porque el correo no está configurado en este ambiente.`;
  }
  if (status === "FAILED") {
    return `ZeptoMail no aceptó el envío a ${email}; puedes usar el enlace de respaldo o reenviar.`;
  }
  return `La entrega a ${email} aún no está confirmada.`;
}

export function invitationSuccessMessage(
  status: InvitationDeliveryStatus,
  logoFailed = false,
  reissued = false,
) {
  const action = reissued ? "La nueva invitación quedó generada" : "Gimnasio, sucursal y dueño creados";
  if (status === "SENT") {
    return `${action}. ZeptoMail aceptó el correo para entrega${logoFailed ? "; el logo requiere revisión" : ""}.`;
  }
  if (status === "NOT_CONFIGURED") {
    return `${action}, pero ZeptoMail no está configurado en este ambiente. Puedes copiar el enlace y reenviarlo después.`;
  }
  return `${action}, pero el correo no pudo entregarse. El enlace sigue disponible y puedes regenerarlo.`;
}

export function invitationStatusLabel(status: PlatformOwnerAccess["invitationStatus"]) {
  if (status === "PENDING") return "Invitación pendiente";
  if (status === "EXPIRED") return "Invitación vencida";
  if (status === "REVOKED") return "Invitación reemplazada";
  if (status === "ACCEPTED") return "Invitación aceptada";
  return "Sin invitación vigente";
}

export function deliveryStatusLabel(status: InvitationDeliveryStatus) {
  if (status === "SENT") return "Aceptado por ZeptoMail";
  if (status === "FAILED") return "Falló la entrega";
  if (status === "NOT_CONFIGURED") return "ZeptoMail no configurado";
  if (status === "PENDING") return "Envío pendiente";
  return "Sin confirmación histórica";
}

export function invitationActionLabel(status: PlatformOwnerAccess["invitationStatus"]) {
  return status === "EXPIRED" || status === "MISSING"
    ? "Generar y enviar invitación"
    : "Reenviar invitación";
}
