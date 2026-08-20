import {
  type FieldErrorMap,
  normalizePhone,
  normalizeRfc,
  validateBranchCode,
  validateEmail,
  validateHexColor,
  validatePhone,
  validateRequired,
  validateRfc,
} from "@/components/FormFields";
import type {
  BranchSummary,
  BranchUpdatePayload,
  CreateBranchPayload,
  OrganizationSummary,
  OrganizationUpdatePayload,
} from "@/lib/api";

export const emptyBranchForm: CreateBranchPayload = {
  name: "",
  code: "",
  timezone: "America/Mexico_City",
  phone: "",
};

export function toOrganizationForm(organization: OrganizationSummary): OrganizationUpdatePayload {
  return {
    legalName: organization.legalName,
    tradeName: organization.tradeName,
    taxId: organization.taxId ?? "",
    timezone: organization.timezone,
    defaultCurrency: organization.defaultCurrency,
    contactEmail: organization.contactEmail ?? "",
    contactPhone: organization.contactPhone ?? "",
    brandName: organization.brandName ?? organization.tradeName,
    logoUrl: organization.logoUrl ?? "",
    primaryColor: organization.primaryColor ?? "#093D3A",
    accentColor: organization.accentColor ?? "#18C8B2",
    surfaceColor: organization.surfaceColor ?? "#F2F8F7",
  };
}

export function toBranchForm(branch: BranchSummary): BranchUpdatePayload {
  return {
    name: branch.name,
    code: branch.code,
    timezone: branch.timezone,
    phone: branch.phone ?? "",
  };
}

export function normalizeOrganizationForm(form: OrganizationUpdatePayload): OrganizationUpdatePayload {
  return {
    ...form,
    taxId: normalizeRfc(form.taxId ?? ""),
    contactPhone: normalizePhone(form.contactPhone ?? ""),
    primaryColor: form.primaryColor?.trim() || null,
    accentColor: form.accentColor?.trim() || null,
    surfaceColor: form.surfaceColor?.trim() || null,
  };
}

export function normalizeBranchForm<T extends BranchUpdatePayload | CreateBranchPayload>(form: T): T {
  return {
    ...form,
    phone: normalizePhone(form.phone ?? ""),
  };
}

export function validateOrganizationForm(form: OrganizationUpdatePayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "legalName", validateRequired(form.legalName, "Nombre legal"));
  addError(errors, "tradeName", validateRequired(form.tradeName, "Nombre comercial"));
  addError(errors, "taxId", validateRfc(form.taxId ?? "", true));
  addError(errors, "defaultCurrency", validateRequired(form.defaultCurrency, "Moneda"));
  addError(errors, "timezone", validateRequired(form.timezone, "Zona horaria"));
  addError(errors, "contactPhone", validatePhone(form.contactPhone ?? "", true));
  addError(errors, "contactEmail", validateEmail(form.contactEmail ?? "", true));
  addError(errors, "primaryColor", validateHexColor(form.primaryColor ?? "", "Color principal", true));
  addError(errors, "accentColor", validateHexColor(form.accentColor ?? "", "Color acento", true));
  addError(errors, "surfaceColor", validateHexColor(form.surfaceColor ?? "", "Color fondo", true));
  return errors;
}

export function validateBranchForm(form: BranchUpdatePayload | CreateBranchPayload, prefix: "branch" | "newBranch") {
  const errors: FieldErrorMap = {};
  addError(errors, `${prefix}Name`, validateRequired(form.name, "Nombre de sucursal"));
  addError(errors, `${prefix}Code`, validateBranchCode(form.code));
  addError(errors, `${prefix}Phone`, validatePhone(form.phone ?? "", true));
  addError(errors, `${prefix}Timezone`, validateRequired(form.timezone, "Zona horaria"));
  return errors;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}
