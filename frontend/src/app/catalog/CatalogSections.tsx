import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { MembershipPlanPayload, PlanSummary } from "@/lib/api";
import { formatMoney, type PlanDraft } from "./CatalogHelpers";

type CatalogToolbarProps = {
  canCreate: boolean;
  onNewPlan: () => void;
  onNewProduct: () => void;
};

export function CatalogToolbar({ canCreate, onNewPlan, onNewProduct }: CatalogToolbarProps) {
  return (
    <section className="app-card flex flex-col gap-4 p-4 lg:flex-row lg:items-center lg:justify-between">
      <div>
        <p className="app-eyebrow">Sucursal activa</p>
        <h2 className="app-card-title">Artículos y planes</h2>
      </div>
      {canCreate ? (
        <div className="grid w-full gap-2 sm:grid-cols-2 lg:w-auto">
          <button className="app-button-secondary h-11 min-w-48" onClick={onNewProduct} type="button">
            Nuevo artículo o cargo
          </button>
          <button className="app-button h-11 min-w-48" onClick={onNewPlan} type="button">
            Nuevo plan
          </button>
        </div>
      ) : null}
    </section>
  );
}

type PlanListProps = {
  canUpdate: boolean;
  plans: PlanSummary[];
  onEditPlan: (plan: PlanSummary) => void;
};

export function PlanList({ canUpdate, plans, onEditPlan }: PlanListProps) {
  return (
    <section className="app-card p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="app-card-title">Planes</h2>
        <span className="text-sm text-[#6f7a73]">{plans.length}</span>
      </div>
      {plans.length === 0 ? (
        <p className="mt-4 rounded-lg border border-dashed border-[#cdd5ca] p-6 text-center text-sm text-[#5d6b63]">
          No hay planes en esta sucursal.
        </p>
      ) : (
        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {plans.map((plan) => (
            <article className="flex min-h-40 flex-col rounded-xl border border-[#d8ddd2] bg-[#fbfcfa] p-4" key={plan.id}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-semibold text-[#111815]">{plan.name}</p>
                  <p className="mt-1 text-lg font-semibold text-[var(--brand-primary)]">
                    {formatMoney(plan.priceCents, plan.currency)}
                  </p>
                </div>
                <span className={plan.status === "ACTIVE"
                  ? "rounded-full bg-[#eaf6ed] px-2 py-1 text-[11px] font-semibold text-[#28733e]"
                  : "rounded-full bg-[#fff3df] px-2 py-1 text-[11px] font-semibold text-[#8a5a22]"}>
                  {plan.status === "ACTIVE" ? "Activo" : "Inactivo"}
                </span>
              </div>
              <div className="mt-3 flex flex-wrap gap-2 text-xs text-[#5d6b63]">
                <span className="rounded-full bg-white px-2 py-1">{plan.durationDays} días</span>
                {plan.graceDays > 0 ? <span className="rounded-full bg-white px-2 py-1">Gracia {plan.graceDays}</span> : null}
                {plan.multiBranchAccess ? <span className="rounded-full bg-white px-2 py-1">Multi-sucursal</span> : null}
              </div>
              {canUpdate ? (
                <button className="app-button-secondary mt-auto h-9 w-full" onClick={() => onEditPlan(plan)} type="button">
                  Editar
                </button>
              ) : null}
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

type PlanDrawerProps = {
  editingPlanId: string | null;
  form: PlanDraft;
  open: boolean;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: PlanDraft) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function PlanDrawer({ editingPlanId, form, open, saving, onClose, onFormChange, onSubmit }: PlanDrawerProps) {
  return (
    <AppDrawer onClose={onClose} open={open} title={editingPlanId ? "Editar plan" : "Nuevo plan"}>
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Código" name="planSku" placeholder="REGULAR-30" value={form.sku} onChange={(value) => onFormChange({ ...form, sku: value })} />
          <TextField label="Nombre" name="planName" placeholder="Plan regular" value={form.name} onChange={(value) => onFormChange({ ...form, name: value })} />
          <NumberField label="Precio" name="planPrice" step="0.01" value={form.price} onChange={(value) => onFormChange({ ...form, price: value })} />
          <NumberField label="Duración en días" name="planDurationDays" value={form.durationDays} onChange={(value) => onFormChange({ ...form, durationDays: value })} />
          <NumberField label="Días de gracia" name="planGraceDays" value={form.graceDays} onChange={(value) => onFormChange({ ...form, graceDays: value })} />
          <SelectField label="Estado" name="planStatus" options={[{ label: "Activo", value: "ACTIVE" }, { label: "Inactivo", value: "INACTIVE" }]} value={form.status} onChange={(value) => onFormChange({ ...form, status: value as MembershipPlanPayload["status"] })} />
        </div>
        <TextAreaField label="Beneficios" name="planBenefits" placeholder="Acceso general, clases grupales" value={form.benefitsJson} onChange={(value) => onFormChange({ ...form, benefitsJson: value })} />
        <label className="flex h-11 items-center gap-3 rounded-md border border-[#cdd5ca] px-3 text-sm font-medium">
          <input checked={form.multiBranchAccess} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => onFormChange({ ...form, multiBranchAccess: event.target.checked })} type="checkbox" />
          Acceso en todas las sucursales
        </label>
        <button className="app-button h-11" disabled={saving} type="submit">
          {saving ? "Guardando..." : editingPlanId ? "Guardar cambios" : "Crear plan"}
        </button>
      </form>
    </AppDrawer>
  );
}
