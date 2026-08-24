import type { FormEvent } from "react";
import {
  EmailField,
  DateField,
  type FieldErrorMap,
  PhoneField,
  NumberField,
  SelectField,
  TextAreaField,
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
import type { PlatformCustomerUpdatePayload } from "@/lib/api/platform-customer-types";

const lifecycleOptions = [
  { value: "ONBOARDING", label: "Implementación" },
  { value: "ACTIVE", label: "Cliente activo" },
  { value: "AT_RISK", label: "En riesgo" },
  { value: "OFFBOARDING", label: "En proceso de salida" },
  { value: "FORMER", label: "Cliente anterior" },
];

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
  primaryColor: "#093D3A",
  accentColor: "#18C8B2",
  surfaceColor: "#F2F8F7",
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
  onLogoFileChange,
  onChange,
  onSubmit,
}: {
  errors: FieldErrorMap;
  form: CreateOrganizationPayload;
  saving: boolean;
  onLogoFileChange: (file: File | null) => void;
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
        deferUpload
        disabled={saving}
        logoUrl={form.logoUrl}
        onFileChange={onLogoFileChange}
      />
      <div className="platform-color-grid">
        <TextField error={errors.primaryColor} label="Color principal" name="primaryColor" value={form.primaryColor ?? "#093D3A"} onChange={(value) => onChange({ ...form, primaryColor: value })} />
        <TextField error={errors.accentColor} label="Color acento" name="accentColor" value={form.accentColor ?? "#18C8B2"} onChange={(value) => onChange({ ...form, accentColor: value })} />
        <TextField error={errors.surfaceColor} label="Color fondo" name="surfaceColor" value={form.surfaceColor ?? "#F2F8F7"} onChange={(value) => onChange({ ...form, surfaceColor: value })} />
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

export function CustomerAccountForm({
  form,
  saving,
  onChange,
  onSubmit,
}: {
  form: PlatformCustomerUpdatePayload;
  saving: boolean;
  onChange: (next: PlatformCustomerUpdatePayload) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <section className="platform-form-intro">
        <strong>Relación con el cliente</strong>
        <span>Estos datos son internos de GymFort y no cambian la operación del gimnasio.</span>
      </section>
      <div className="grid gap-4 md:grid-cols-2">
        <SelectField
          label="Etapa comercial"
          name="customerLifecycle"
          onChange={(value) => onChange({ ...form, lifecycleStatus: value as PlatformCustomerUpdatePayload["lifecycleStatus"] })}
          options={lifecycleOptions}
          value={form.lifecycleStatus}
        />
        <TextField
          label="Responsable en GymFort"
          name="accountOwner"
          onChange={(value) => onChange({ ...form, accountOwner: value || null })}
          placeholder="Ej. Daniela · Customer Success"
          required={false}
          value={form.accountOwner ?? ""}
        />
        <EmailField
          label="Correo de cobranza"
          name="billingEmail"
          onChange={(value) => onChange({ ...form, billingEmail: value || null })}
          required={false}
          value={form.billingEmail ?? ""}
        />
        <PhoneField
          label="Teléfono de cobranza"
          name="billingPhone"
          onChange={(value) => onChange({ ...form, billingPhone: value || null })}
          required={false}
          value={form.billingPhone ?? ""}
        />
        <DateField
          label="Inicio de contrato"
          name="contractStartedOn"
          onChange={(value) => onChange({ ...form, contractStartedOn: value || null })}
          required={false}
          value={form.contractStartedOn ?? ""}
        />
        <DateField
          label="Renovación prevista"
          name="renewalOn"
          onChange={(value) => onChange({ ...form, renewalOn: value || null })}
          required={false}
          value={form.renewalOn ?? ""}
        />
        <NumberField
          help="Días acordados después de la fecha de cobro; máximo 90."
          label="Días de crédito"
          max="90"
          min="0"
          name="paymentTermsDays"
          onChange={(value) => onChange({ ...form, paymentTermsDays: Number(value) || 0 })}
          value={String(form.paymentTermsDays)}
        />
        <DateField
          label="Próximo seguimiento"
          name="nextFollowUpOn"
          onChange={(value) => onChange({ ...form, nextFollowUpOn: value || null })}
          required={false}
          value={form.nextFollowUpOn ?? ""}
        />
      </div>
      <TextAreaField
        label="Notas del expediente"
        name="customerNotes"
        onChange={(value) => onChange({ ...form, notes: value || null })}
        placeholder="Acuerdos, riesgos, responsables del cliente y siguiente paso..."
        required={false}
        rows={5}
        value={form.notes ?? ""}
      />
      <button className="form-primary-button" disabled={saving} type="submit">
        {saving ? "Guardando..." : "Guardar expediente"}
      </button>
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
