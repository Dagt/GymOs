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

type SettingsSummaryProps = {
  activeBranchName?: string;
  branchCount: number;
  legalName?: string;
  tradeName?: string;
};

export function SettingsSummary({ activeBranchName, branchCount, legalName, tradeName }: SettingsSummaryProps) {
  return (
    <section className="settings-summary">
      <article>
        <span>Gimnasio actual</span>
        <strong>{tradeName}</strong>
        <p>{legalName}</p>
      </article>
      <article>
        <span>Sucursales</span>
        <strong>{branchCount}</strong>
        <p>{branchCount === 1 ? "Una sucursal registrada." : "Sucursales registradas para este gimnasio."}</p>
      </article>
      <article>
        <span>Sucursal operativa</span>
        <strong>{activeBranchName ?? "Sin seleccionar"}</strong>
        <p>Se elige desde Gimnasios para entrar al contexto de trabajo.</p>
      </article>
    </section>
  );
}

type OrganizationOverviewCardProps = {
  form: OrganizationUpdatePayload;
  onEdit: () => void;
};

export function OrganizationOverviewCard({ form, onEdit }: OrganizationOverviewCardProps) {
  return (
    <section className="app-card self-start p-5">
      <div className="admin-panel-header">
        <div>
          <p>Gimnasio</p>
          <h2>Datos y marca</h2>
        </div>
        <button className="app-button" onClick={onEdit} type="button">
          Editar gimnasio
        </button>
      </div>
      <div className="settings-summary mt-5">
        <article className="min-w-0">
          <span>Marca</span>
          <strong className="break-words">{form.brandName || form.tradeName}</strong>
          <p className="break-all">{form.contactEmail}</p>
        </article>
        <article className="min-w-0">
          <span>Moneda</span>
          <strong>{form.defaultCurrency}</strong>
          <p className="break-words">{form.timezone}</p>
        </article>
        <article className="min-w-0">
          <span>RFC</span>
          <strong className="break-words">{form.taxId || "Sin RFC"}</strong>
          <p className="break-words">{form.contactPhone || "Sin telefono"}</p>
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
    <section className="app-card self-start p-5">
      <div className="admin-panel-header">
        <div>
          <p>Sucursales</p>
          <h2>Sedes registradas</h2>
        </div>
        <button className="app-button" onClick={onNewBranch} type="button">
          Crear sucursal
        </button>
      </div>
      <div className="team-role-list">
        {branches.length === 0 ? (
          <section className="admin-empty-card">
            <strong>Aun no hay sucursales</strong>
            <span>Crea la primera para poder operar caja, recepcion y reportes.</span>
          </section>
        ) : null}
        {branches.map((branch) => (
          <article className="min-w-0" key={branch.id}>
            <strong className="break-words">{branch.name}</strong>
            <span className="break-words">{branch.code} - {branch.phone || "Sin telefono"}</span>
            <button className="admin-text-button" onClick={() => onEditBranch(branch.id)} type="button">
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
    <section className="app-card p-5" style={{ gridColumn: "1 / -1" }}>
      <div className="grid items-start gap-5 md:grid-cols-[minmax(0,1fr)_auto]">
        <div className="min-w-0">
          <p className="app-eyebrow">Equipo</p>
          <h2 className="mt-2 text-xl font-semibold">Usuarios y permisos</h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-[#5d6b63]">
            Crea cuentas para recepcion, gerencia, coaches y operacion. Cada usuario vera una experiencia segun sus permisos.
          </p>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-[#5d6b63]">
            Primero crea las sucursales necesarias. Despues asigna a cada persona solo las sucursales que debe usar.
          </p>
        </div>
        <Link className="admin-secondary-link w-full md:w-auto" href="/users">
          Abrir equipo y accesos
        </Link>
      </div>
    </section>
  );
}

type OrganizationDrawerProps = {
  errors: FieldErrorMap;
  form: OrganizationUpdatePayload;
  open: boolean;
  saving: boolean;
  onChange: (form: OrganizationUpdatePayload) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function OrganizationDrawer({ errors, form, open, saving, onChange, onClose, onSubmit }: OrganizationDrawerProps) {
  return (
    <AppDrawer
      description="Actualiza datos comerciales, contacto y marca visual que veran duenos, gerentes, recepcion y kiosco."
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
          disabled={saving}
          logoUrl={form.logoUrl}
          tenantName={form.tradeName || form.brandName || form.legalName}
          onChange={(logoUrl) => onChange({ ...form, logoUrl })}
        />
        <div className="grid gap-4 sm:grid-cols-3">
          <ColorField error={errors.primaryColor} label="Color principal" name="primaryColor" value={form.primaryColor ?? "#164E63"} onChange={(value) => onChange({ ...form, primaryColor: value })} />
          <ColorField error={errors.accentColor} label="Color acento" name="accentColor" value={form.accentColor ?? "#F59E0B"} onChange={(value) => onChange({ ...form, accentColor: value })} />
          <ColorField error={errors.surfaceColor} label="Color fondo" name="surfaceColor" value={form.surfaceColor ?? "#F4F7FB"} onChange={(value) => onChange({ ...form, surfaceColor: value })} />
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
      description="Crea una sede operativa para caja, recepcion, usuarios y reportes."
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
      description="Edita nombre, codigo, telefono y zona horaria de la sucursal seleccionada."
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
