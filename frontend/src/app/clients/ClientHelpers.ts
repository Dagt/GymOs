import {
  type FieldErrorMap,
  validateEmail,
  validateGender,
  validateIsoDate,
  validatePhone,
  validateRequired,
} from "@/components/FormFields";
import type { ClientPayload, ClientSummary } from "@/lib/api";

export type ClientRegistrationForm = ClientPayload & {
  planId: string;
  paymentMethod: "CASH" | "TRANSFER" | "CARD_MANUAL";
  paymentReference: string;
  validateNow: boolean;
};

export const emptyClientForm: ClientRegistrationForm = {
  firstName: "",
  lastName: "",
  maternalLastName: "",
  birthDate: "",
  gender: "",
  phone: "",
  email: "",
  photoReference: "",
  notes: "",
  planId: "",
  paymentMethod: "CASH",
  paymentReference: "",
  validateNow: true,
};

export function validateClientForm(form: ClientRegistrationForm, requiresPlan: boolean) {
  const errors: FieldErrorMap = {};
  addError(errors, "firstName", validateRequired(form.firstName, "Nombre"));
  addError(errors, "lastName", validateRequired(form.lastName, "Apellido paterno"));
  addError(errors, "phone", validatePhone(form.phone ?? "", true));
  addError(errors, "email", validateEmail(form.email ?? "", false));
  addError(errors, "birthDate", validateIsoDate(form.birthDate ?? "", "Fecha de nacimiento", false));
  addError(errors, "gender", validateGender(form.gender ?? "", false));
  if (requiresPlan && !form.planId) {
    errors.planId = "Selecciona la membresia inicial.";
  }
  return errors;
}

export function clientFullName(client: ClientSummary) {
  return `${client.firstName} ${client.lastName} ${client.maternalLastName ?? ""}`.trim();
}

export function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}
