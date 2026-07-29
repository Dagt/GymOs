import type { FormEvent } from "react";
import {
  EmailField,
  type FieldErrorMap,
  PhoneField,
  TextField,
  normalizeRfc,
  validateBranchCode,
  validateEmail,
  validateHexColor,
  validatePhone,
  validateRfc,
  validateRequired,
} from "@/components/FormFields";
import { LogoUploadField } from "@/components/LogoUploadField";
import type { CreateBranchPayload, CreateOrganizationPayload } from "@/lib/api";

export const emptyOrganizationForm: CreateOrganizationPayload = {
  legalName: "",
  tradeName: "",
  taxId: "",
  timezone: "America/Mexico_City",
  defaultCurrency: "MXN",
  contactEmail: "",
  contactPhone: "",
  brandName: "",
  logoUrl: "",
  primaryColor: "#164E63",
  accentColor: "#F59E0B",
  surfaceColor: "#F4F7FB",
};

export const emptyBranchForm: CreateBranchPayload = {
  name: "",
  code: "",
  timezone: "America/Mexico_City",
  phone: "",
};

export function OrganizationForm({
  errors,
  form,
  saving,
  onChange,
  onSubmit,
}: {
  errors: FieldErrorMap;
  form: CreateOrganizationPayload;
  saving: boolean;
  onChange: (next: CreateOrganizationPayload) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <TextField error={errors.tradeName} label="Nombre comercial" name="tradeName" value={form.tradeName} onChange={(value) => onChange({ ...form, tradeName: value, brandName: form.brandName || value })} />
      <TextField error={errors.legalName} label="Nombre legal" name="legalName" value={form.legalName} onChange={(value) => onChange({ ...form, legalName: value })} />
      <TextField error={errors.taxId} label="RFC" name="taxId" value={form.taxId ?? ""} onChange={(value) => onChange({ ...form, taxId: normalizeRfc(value) })} />
      <EmailField error={errors.contactEmail} label="Email contacto" name="contactEmail" required value={form.contactEmail ?? ""} onChange={(value) => onChange({ ...form, contactEmail: value })} />
      <PhoneField error={errors.contactPhone} label="Telefono contacto" name="contactPhone" required value={form.contactPhone ?? ""} onChange={(value) => onChange({ ...form, contactPhone: value })} />
      <hr className="border-[#dbe4ee]" />
      <TextField error={errors.brandName} label="Nombre visible de marca" name="brandName" required={false} value={form.brandName ?? ""} onChange={(value) => onChange({ ...form, brandName: value })} />
      <LogoUploadField
        disabled={saving}
        logoUrl={form.logoUrl}
        tenantName={form.tradeName || form.brandName || form.legalName}
        onChange={(logoUrl) => onChange({ ...form, logoUrl })}
      />
      <div className="platform-color-grid">
        <TextField error={errors.primaryColor} label="Color principal" name="primaryColor" value={form.primaryColor ?? "#164E63"} onChange={(value) => onChange({ ...form, primaryColor: value })} />
        <TextField error={errors.accentColor} label="Color acento" name="accentColor" value={form.accentColor ?? "#F59E0B"} onChange={(value) => onChange({ ...form, accentColor: value })} />
        <TextField error={errors.surfaceColor} label="Color fondo" name="surfaceColor" value={form.surfaceColor ?? "#F4F7FB"} onChange={(value) => onChange({ ...form, surfaceColor: value })} />
      </div>
      <button className="form-primary-button" disabled={saving} type="submit">Crear gimnasio</button>
    </form>
  );
}

export function BranchForm({
  errors,
  form,
  saving,
  onChange,
  onSubmit,
}: {
  errors: FieldErrorMap;
  form: CreateBranchPayload;
  saving: boolean;
  onChange: (next: CreateBranchPayload) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <TextField error={errors.name} label="Nombre de sucursal" name="branchName" value={form.name} onChange={(value) => onChange({ ...form, name: value })} />
      <TextField error={errors.code} label="Codigo corto" name="branchCode" value={form.code} onChange={(value) => onChange({ ...form, code: value })} />
      <PhoneField error={errors.phone} label="Telefono sucursal" name="branchPhone" value={form.phone ?? ""} onChange={(value) => onChange({ ...form, phone: value })} />
      <TextField error={errors.timezone} label="Zona horaria" name="branchTimezone" value={form.timezone} onChange={(value) => onChange({ ...form, timezone: value })} />
      <button className="form-primary-button" disabled={saving} type="submit">Crear sucursal</button>
    </form>
  );
}

export function validateOrganization(form: CreateOrganizationPayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "tradeName", validateRequired(form.tradeName, "Nombre comercial"));
  addError(errors, "legalName", validateRequired(form.legalName, "Nombre legal"));
  addError(errors, "taxId", validateRfc(form.taxId ?? "", true));
  addError(errors, "contactEmail", validateEmail(form.contactEmail ?? "", true));
  addError(errors, "contactPhone", validatePhone(form.contactPhone ?? "", true));
  addError(errors, "primaryColor", validateHexColor(form.primaryColor ?? "", "Color principal", true));
  addError(errors, "accentColor", validateHexColor(form.accentColor ?? "", "Color acento", true));
  addError(errors, "surfaceColor", validateHexColor(form.surfaceColor ?? "", "Color fondo", true));
  return errors;
}

export function validateBranch(form: CreateBranchPayload) {
  const errors: FieldErrorMap = {};
  addError(errors, "name", validateRequired(form.name, "Nombre de sucursal"));
  addError(errors, "code", validateBranchCode(form.code));
  addError(errors, "timezone", validateRequired(form.timezone, "Zona horaria"));
  addError(errors, "phone", validatePhone(form.phone ?? "", true));
  return errors;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}

