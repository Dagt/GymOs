import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  NumberField,
  SelectField,
  TextAreaField,
  TextField,
} from "@/components/FormFields";
import type {
  MembershipPlanPayload,
  PlanSummary,
} from "@/lib/api";
import { formatMoney, type PlanDraft } from "./CatalogHelpers";

type CatalogToolbarProps = {
  canCreate: boolean;
  onNewPlan: () => void;
  onNewProduct: () => void;
};

export function CatalogToolbar({ canCreate, onNewPlan, onNewProduct }: CatalogToolbarProps) {
  return (
    <section className="catalog-toolbar app-card p-5">
      <div>
        <p className="app-eyebrow">Gestión del catálogo</p>
        <h2 className="app-card-title">Artículos de venta y planes de membresía</h2>
        <p className="app-card-copy">
          Aquí se crean productos físicos y cargos simples que aparecen en caja. Los pases, consultas, rutinas y servicios con agenda se administran en Accesos, Servicios, Coaches o Reservas.
        </p>
      </div>
      <div>
        {canCreate ? (
          <>
            <button className="app-button-secondary" onClick={onNewProduct} type="button">
              Nuevo artículo o cargo
            </button>
            <button className="app-button" onClick={onNewPlan} type="button">
              Nuevo plan
            </button>
          </>
        ) : null}
      </div>
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
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Planes</h2>
      <div className="mt-4 grid gap-3">
        {plans.length === 0 ? <p className="text-sm text-[#5d6b63]">No hay planes de membresía activos.</p> : null}
        {plans.map((plan) => (
          <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={plan.id}>
            <p className="font-semibold">{plan.name}</p>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {plan.durationDays} días - gracia {plan.graceDays} - {formatMoney(plan.priceCents, plan.currency)}
            </p>
            <p className="mt-2 text-xs font-semibold text-[var(--brand-primary)]">
              {plan.multiBranchAccess ? "Acceso multi-sucursal" : "Acceso por sucursal de venta"}
            </p>
            {canUpdate ? (
              <button
                className="mt-3 h-9 rounded-md border border-[#cdd5ca] px-3 text-xs font-semibold text-[var(--brand-primary)]"
                onClick={() => onEditPlan(plan)}
                type="button"
              >
                Editar plan
              </button>
            ) : null}
          </article>
        ))}
      </div>
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

export function PlanDrawer({
  editingPlanId,
  form,
  open,
  saving,
  onClose,
  onFormChange,
  onSubmit,
}: PlanDrawerProps) {
  return (
    <AppDrawer
      description="Crea o edita membresías con duración. Estos planes aparecen como opción en el alta y la ficha de socios."
      onClose={onClose}
      open={open}
      title={editingPlanId ? "Editar plan de membresía" : "Nuevo plan de membresía"}
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Código del plan" name="planSku" value={form.sku} onChange={(value) => onFormChange({ ...form, sku: value })} />
          <TextField label="Nombre del plan" name="planName" value={form.name} onChange={(value) => onFormChange({ ...form, name: value })} />
          <NumberField label="Precio" name="planPrice" value={form.price} onChange={(value) => onFormChange({ ...form, price: value })} />
          <TextField label="Moneda" name="planCurrency" value={form.currency} onChange={(value) => onFormChange({ ...form, currency: value })} />
          <NumberField label="Duración en días" name="planDurationDays" value={form.durationDays} onChange={(value) => onFormChange({ ...form, durationDays: value })} />
          <NumberField label="Días de gracia" name="planGraceDays" value={form.graceDays} onChange={(value) => onFormChange({ ...form, graceDays: value })} />
        </div>
        <TextAreaField
          label="Beneficios incluidos"
          name="planBenefits"
          placeholder="Ej. Acceso general, clases grupales, estacionamiento."
          value={form.benefitsJson}
          onChange={(value) => onFormChange({ ...form, benefitsJson: value })}
        />
        <label className="grid gap-2 text-sm font-medium text-[#42514a]">
          Alcance
          <span className="flex items-start gap-3 rounded-md border border-[#cdd5ca] bg-white px-3 py-3 text-sm text-[#42514a]">
            <input
              className="mt-1 h-4 w-4 accent-[var(--brand-primary)]"
              checked={form.multiBranchAccess}
              onChange={(event) => onFormChange({ ...form, multiBranchAccess: event.target.checked })}
              type="checkbox"
            />
            <span>
              <strong className="block text-[#111815]">Acceso multi-sucursal</strong>
              Disponible para entrar con esta membresía en todas las sucursales activas del gimnasio.
            </span>
          </span>
        </label>
        <SelectField
          label="Estado"
          name="planStatus"
          options={[
            { label: "Activo", value: "ACTIVE" },
            { label: "Inactivo", value: "INACTIVE" },
          ]}
          value={form.status}
          onChange={(value) => onFormChange({ ...form, status: value as MembershipPlanPayload["status"] })}
        />
        <button className="app-button h-11" disabled={saving} type="submit">
          {saving ? "Guardando..." : editingPlanId ? "Guardar cambios del plan" : "Crear plan de membresía"}
        </button>
      </form>
    </AppDrawer>
  );
}
