import Link from "next/link";
import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  ColorField,
  EmailField,
  type FieldErrorMap,
  normalizeRfc,
  PhoneField,
  SelectField,
  TextField,
} from "@/components/FormFields";
import { LogoUploadField } from "@/components/LogoUploadField";
import type {
  BranchSummary,
  BranchUpdatePayload,
  CreateBranchPayload,
  OrganizationUpdatePayload,
} from "@/lib/api";

type OrganizationOverviewCardProps = {
  form: OrganizationUpdatePayload;
  onEdit: () => void;
};

export function OrganizationOverviewCard({ form, onEdit }: OrganizationOverviewCardProps) {
  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div>
          <p>Gimnasio</p>
          <h2>Datos y marca</h2>
        </div>
        <button className="app-button" onClick={onEdit} type="button">
          Editar gimnasio
        </button>
      </div>
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <article className="min-w-0 rounded-xl border border-[#dbe4ee] bg-white p-4">
          <span className="text-xs font-semibold uppercase text-[#64748b]">Marca</span>
          <strong className="mt-2 block break-words text-lg">{form.brandName || form.tradeName}</strong>
          <p className="mt-2 break-words text-sm text-[#64748b] [overflow-wrap:anywhere]">{form.contactEmail}</p>
        </article>
        <article className="min-w-0 rounded-xl border border-[#dbe4ee] bg-white p-4">
          <span className="text-xs font-semibold uppercase text-[#64748b]">Moneda</span>
          <strong className="mt-2 block text-lg">{form.defaultCurrency}</strong>
          <p className="mt-2 break-words text-sm text-[#64748b]">{form.timezone}</p>
        </article>
        <article className="min-w-0 rounded-xl border border-[#dbe4ee] bg-white p-4">
          <span className="text-xs font-semibold uppercase text-[#64748b]">RFC</span>
          <strong className="mt-2 block break-words text-lg">{form.taxId || "Sin RFC"}</strong>
          <p className="mt-2 break-words text-sm text-[#64748b]">{form.contactPhone || "Sin telefono"}</p>
        </article>
      </div>
    </section>
  );
}

type BranchesOverviewCardProps = {
  branches: BranchSummary[];
  onEditBranch: (branchId: string) => void;
  onNewBranch: () => void;
};

export function BranchesOverviewCard({ branches, onEditBranch, onNewBranch }: BranchesOverviewCardProps) {
  return (
    <section className="app-card flex min-h-0 flex-col overflow-hidden p-5 xl:h-full">
      <div className="admin-panel-header shrink-0">
        <div>
          <p>Sucursales</p>
          <h2>Sedes registradas</h2>
        </div>
        <button className="app-button" onClick={onNewBranch} type="button">
          Crear sucursal
        </button>
      </div>
      <div className="mt-5 grid min-h-0 gap-4 xl:flex-1 xl:grid-cols-1 xl:content-start xl:overflow-y-auto xl:[scrollbar-width:none] xl:[&::-webkit-scrollbar]:hidden">
        {branches.length === 0 ? (
          <section className="admin-empty-card">
            <strong>No hay sucursales.</strong>
          </section>
        ) : null}
        {branches.map((branch) => (
          <article className="min-w-0 rounded-xl border border-[#dbe4ee] bg-white p-4" key={branch.id}>
            <strong className="block break-words">{branch.name}</strong>
            <span className="mt-2 block break-words text-sm text-[#64748b]">{branch.code} - {branch.phone || "Sin telefono"}</span>
            <button className="admin-text-button mt-4" onClick={() => onEditBranch(branch.id)} type="button">
              Editar sucursal
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}

export function TeamAccessCard() {
  return (
    <section className="app-card p-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="min-w-0">
          <p className="app-eyebrow">Equipo</p>
          <h2 className="mt-2 text-xl font-semibold">Usuarios y permisos</h2>
        </div>
        <Link className="admin-secondary-link shrink-0" href="/users">
          Abrir equipo y accesos
        </Link>
      </div>
    </section>
  );
}

type OrganizationDrawerProps = {
  accessToken: string;
  errors: FieldErrorMap;
  form: OrganizationUpdatePayload;
  open: boolean;
  organizationId: string;
  saving: boolean;
  onChange: (form: OrganizationUpdatePayload) => void;
  onClose: () => void;
  onLogoChange: (logoUrl: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function OrganizationDrawer({
  accessToken,
  errors,
  form,
  open,
  organizationId,
  saving,
  onChange,
  onClose,
  onLogoChange,
  onSubmit,
}: OrganizationDrawerProps) {
  return (
    <AppDrawer
      onClose={onClose}
      open={open}
      title="Editar gimnasio"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <TextField error={errors.legalName} label="Nombre legal" name="legalName" value={form.legalName} onChange={(value) => onChange({ ...form, legalName: value })} />
        <TextField error={errors.tradeName} label="Nombre comercial" name="tradeName" value={form.tradeName} onChange={(value) => onChange({ ...form, tradeName: value })} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField error={errors.taxId} label="RFC" name="taxId" value={form.taxId ?? ""} onChange={(value) => onChange({ ...form, taxId: normalizeRfc(value) })} />
          <TextField error={errors.defaultCurrency} label="Moneda" name="defaultCurrency" value={form.defaultCurrency} onChange={(value) => onChange({ ...form, defaultCurrency: value })} />
          <TextField error={errors.timezone} label="Zona horaria" name="timezone" value={form.timezone} onChange={(value) => onChange({ ...form, timezone: value })} />
          <PhoneField error={errors.contactPhone} label="Telefono" name="contactPhone" required value={form.contactPhone ?? ""} onChange={(value) => onChange({ ...form, contactPhone: value })} />
        </div>
        <EmailField error={errors.contactEmail} label="Email de contacto" name="contactEmail" required value={form.contactEmail ?? ""} onChange={(value) => onChange({ ...form, contactEmail: value })} />
        <hr className="border-[#dbe4ee]" />
        <p className="app-eyebrow">Marca del gimnasio</p>
        <TextField label="Nombre visible de marca" name="brandName" required={false} value={form.brandName ?? ""} onChange={(value) => onChange({ ...form, brandName: value })} />
        <LogoUploadField
          accessToken={accessToken}
          disabled={saving}
          logoUrl={form.logoUrl}
          organizationId={organizationId}
          onChange={(logoUrl) => {
            onChange({ ...form, logoUrl });
            onLogoChange(logoUrl);
          }}
        />
        <div className="grid gap-4 sm:grid-cols-3">
          <ColorField error={errors.primaryColor} label="Color principal" name="primaryColor" value={form.primaryColor ?? "#093D3A"} onChange={(value) => onChange({ ...form, primaryColor: value })} />
          <ColorField error={errors.accentColor} label="Color acento" name="accentColor" value={form.accentColor ?? "#18C8B2"} onChange={(value) => onChange({ ...form, accentColor: value })} />
          <ColorField error={errors.surfaceColor} label="Color fondo" name="surfaceColor" value={form.surfaceColor ?? "#F2F8F7"} onChange={(value) => onChange({ ...form, surfaceColor: value })} />
        </div>
        <button className="form-primary-button" disabled={saving} type="submit">
          Guardar gimnasio
        </button>
      </form>
    </AppDrawer>
  );
}

type NewBranchDrawerProps = {
  errors: FieldErrorMap;
  form: CreateBranchPayload;
  open: boolean;
  saving: boolean;
  onChange: (form: CreateBranchPayload) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function NewBranchDrawer({ errors, form, open, saving, onChange, onClose, onSubmit }: NewBranchDrawerProps) {
  return (
    <AppDrawer
      onClose={onClose}
      open={open}
      title="Crear sucursal"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <TextField error={errors.newBranchName} label="Nombre de sucursal" name="newBranchName" value={form.name} onChange={(value) => onChange({ ...form, name: value })} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField error={errors.newBranchCode} label="Codigo corto" name="newBranchCode" value={form.code} onChange={(value) => onChange({ ...form, code: value })} />
          <PhoneField error={errors.newBranchPhone} label="Telefono" name="newBranchPhone" required value={form.phone ?? ""} onChange={(value) => onChange({ ...form, phone: value })} />
        </div>
        <TextField error={errors.newBranchTimezone} label="Zona horaria" name="newBranchTimezone" value={form.timezone} onChange={(value) => onChange({ ...form, timezone: value })} />
        <button className="form-primary-button" disabled={saving} type="submit">
          Crear sucursal
        </button>
      </form>
    </AppDrawer>
  );
}

type EditBranchDrawerProps = {
  branchId: string;
  branches: BranchSummary[];
  errors: FieldErrorMap;
  form: BranchUpdatePayload | null;
  open: boolean;
  saving: boolean;
  onBranchSelect: (branchId: string) => void;
  onChange: (form: BranchUpdatePayload) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function EditBranchDrawer({
  branchId,
  branches,
  errors,
  form,
  open,
  saving,
  onBranchSelect,
  onChange,
  onClose,
  onSubmit,
}: EditBranchDrawerProps) {
  return (
    <AppDrawer
      onClose={onClose}
      open={open}
      title="Editar sucursal"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <SelectField
          label="Sucursal"
          name="editableBranch"
          options={branches.map((branch) => ({ label: `${branch.name} (${branch.code})`, value: branch.id }))}
          value={branchId}
          onChange={onBranchSelect}
        />
        {form ? (
          <>
            <TextField error={errors.branchName} label="Nombre" name="branchName" value={form.name} onChange={(value) => onChange({ ...form, name: value })} />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField error={errors.branchCode} label="Codigo" name="branchCode" value={form.code} onChange={(value) => onChange({ ...form, code: value })} />
              <PhoneField error={errors.branchPhone} label="Telefono" name="branchPhone" required value={form.phone ?? ""} onChange={(value) => onChange({ ...form, phone: value })} />
            </div>
            <TextField error={errors.branchTimezone} label="Zona horaria" name="branchTimezone" value={form.timezone} onChange={(value) => onChange({ ...form, timezone: value })} />
            <button className="form-primary-button" disabled={saving} type="submit">
              Guardar sucursal
            </button>
          </>
        ) : null}
      </form>
    </AppDrawer>
  );
}
