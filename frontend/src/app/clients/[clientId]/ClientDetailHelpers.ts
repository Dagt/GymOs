import {
  type FieldErrorMap,
  normalizePhone,
  validateEmail,
  validateGender,
  validateIsoDate,
  validatePhone,
  validateRequired,
} from "@/components/FormFields";
import type { ClientDetail, ClientUpdatePayload } from "@/lib/api";
export { formatDate, formatDay } from "./client-date-helpers";

export function toClientUpdateForm(client: ClientDetail): ClientUpdatePayload {
  return {
    firstName: client.firstName,
    lastName: client.lastName,
    maternalLastName: client.maternalLastName ?? "",
    birthDate: client.birthDate ?? "",
    gender: client.gender ?? "",
    phone: client.phone ?? "",
    email: client.email ?? "",
    photoReference: client.photoReference ?? "",
    status: client.status,
    notes: client.notes ?? "",
  };
}

export function normalizeClientUpdateForm(form: ClientUpdatePayload): ClientUpdatePayload {
  return {
    ...form,
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    maternalLastName: form.maternalLastName?.trim() || null,
    phone: normalizePhone(form.phone ?? ""),
    email: form.email?.trim() || null,
  };
}

export function validateClientUpdate(form: ClientUpdatePayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "firstName", validateRequired(form.firstName, "Nombre"));
  addError(errors, "lastName", validateRequired(form.lastName, "Apellido paterno"));
  addError(errors, "phone", validatePhone(form.phone ?? "", true));
  addError(errors, "email", validateEmail(form.email ?? "", false));
  addError(errors, "birthDate", validateIsoDate(form.birthDate ?? "", "Fecha de nacimiento", true));
  addError(errors, "gender", validateGender(form.gender ?? "", false));
  return errors;
}

export function clientFullName(client: ClientDetail) {
  return `${client.firstName} ${client.lastName} ${client.maternalLastName ?? ""}`.trim();
}

export function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

export function genderLabel(gender: string) {
  const labels: Record<string, string> = {
    FEMALE: "Mujer",
    MALE: "Hombre",
  };
  return labels[gender] ?? gender;
}

export function membershipStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Activa",
    EXPIRED: "Expirada",
    CANCELLED: "Cancelada",
  };
  return labels[status] ?? status;
}

export function statusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Activo",
    INACTIVE: "Inactivo",
    SUSPENDED: "Suspendido",
  };
  return labels[status] ?? status;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}
