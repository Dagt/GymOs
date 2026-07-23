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
  ProductPayload,
  ProductSummary,
} from "@/lib/api";
import { formatMoney, type PlanDraft } from "./CatalogHelpers";

type CatalogToolbarProps = {
  onNewPlan: () => void;
  onNewProduct: () => void;
};

export function CatalogToolbar({ onNewPlan, onNewProduct }: CatalogToolbarProps) {
  return (
    <section className="catalog-toolbar app-card p-5">
      <div>
        <p className="app-eyebrow">Gestion del catalogo</p>
        <h2 className="app-card-title">Planes separados de productos de venta</h2>
        <p className="app-card-copy">
          Usa productos para mostrador y servicios. Usa planes para membresias con duracion, acceso multi-sucursal y alta de socios.
        </p>
      </div>
      <div>
        <button className="app-button-secondary" onClick={onNewProduct} type="button">
          Nuevo producto
        </button>
        <button className="app-button" onClick={onNewPlan} type="button">
          Nuevo plan
        </button>
      </div>
    </section>
  );
}

type ProductListProps = {
  loading: boolean;
  products: ProductSummary[];
};

export function ProductList({ loading, products }: ProductListProps) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Productos y servicios</h2>
        <span className="text-sm text-[#6f7a73]">{products.length}</span>
      </div>
      <div className="mt-4 grid gap-3">
        {loading ? <p className="text-sm text-[#5d6b63]">Cargando catalogo...</p> : null}
        {!loading && products.length === 0 ? (
          <p className="text-sm text-[#5d6b63]">No hay productos o servicios registrados.</p>
        ) : null}
        {products.map((product) => (
          <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={product.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold">{product.name}</p>
                <p className="mt-1 text-sm text-[#5d6b63]">
                  {product.sku} - {product.productType} - {product.status}
                </p>
              </div>
              <p className="font-semibold">{formatMoney(product.priceCents, product.currency)}</p>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

type PlanListProps = {
  plans: PlanSummary[];
  onEditPlan: (plan: PlanSummary) => void;
};

export function PlanList({ plans, onEditPlan }: PlanListProps) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Planes</h2>
      <div className="mt-4 grid gap-3">
        {plans.length === 0 ? <p className="text-sm text-[#5d6b63]">No hay planes de membresia activos.</p> : null}
        {plans.map((plan) => (
          <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={plan.id}>
            <p className="font-semibold">{plan.name}</p>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {plan.durationDays} dias - gracia {plan.graceDays} - {formatMoney(plan.priceCents, plan.currency)}
            </p>
            <p className="mt-2 text-xs font-semibold text-[var(--brand-primary)]">
              {plan.multiBranchAccess ? "Acceso multi-sucursal" : "Acceso por sucursal de venta"}
            </p>
            <button
              className="mt-3 h-9 rounded-md border border-[#cdd5ca] px-3 text-xs font-semibold text-[var(--brand-primary)]"
              onClick={() => onEditPlan(plan)}
              type="button"
            >
              Editar plan
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}

type ProductDrawerProps = {
  form: ProductPayload;
  open: boolean;
  price: string;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: ProductPayload) => void;
  onPriceChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function ProductDrawer({
  form,
  open,
  price,
  saving,
  onClose,
  onFormChange,
  onPriceChange,
  onSubmit,
}: ProductDrawerProps) {
  return (
    <AppDrawer
      description="Articulos de mostrador o servicios vendibles. Las membresias se crean como planes."
      onClose={onClose}
      open={open}
      title="Nuevo producto de venta"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="SKU" name="productSku" value={form.sku} onChange={(value) => onFormChange({ ...form, sku: value })} />
          <TextField label="Nombre" name="productName" value={form.name} onChange={(value) => onFormChange({ ...form, name: value })} />
          <NumberField label="Precio" name="productPrice" value={price} onChange={onPriceChange} />
          <TextField label="Moneda" name="productCurrency" value={form.currency} onChange={(value) => onFormChange({ ...form, currency: value })} />
        </div>
        <SelectField
          label="Tipo"
          name="productType"
          options={[
            { label: "Producto fisico", value: "PHYSICAL" },
            { label: "Servicio", value: "SERVICE" },
          ]}
          value={form.productType}
          onChange={(value) => onFormChange({ ...form, productType: value as ProductPayload["productType"] })}
        />
        <TextAreaField label="Descripcion" name="productDescription" value={form.description ?? ""} onChange={(value) => onFormChange({ ...form, description: value })} />
        <button className="app-button h-11" disabled={saving} type="submit">
          {saving ? "Guardando..." : "Crear producto"}
        </button>
      </form>
    </AppDrawer>
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
      description="Crea o edita membresias con duracion. Estos planes aparecen en el alta de socios."
      onClose={onClose}
      open={open}
      title={editingPlanId ? "Editar plan de membresia" : "Nuevo plan de membresia"}
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Codigo del plan" name="planSku" value={form.sku} onChange={(value) => onFormChange({ ...form, sku: value })} />
          <TextField label="Nombre del plan" name="planName" value={form.name} onChange={(value) => onFormChange({ ...form, name: value })} />
          <NumberField label="Precio" name="planPrice" value={form.price} onChange={(value) => onFormChange({ ...form, price: value })} />
          <TextField label="Moneda" name="planCurrency" value={form.currency} onChange={(value) => onFormChange({ ...form, currency: value })} />
          <NumberField label="Duracion en dias" name="planDurationDays" value={form.durationDays} onChange={(value) => onFormChange({ ...form, durationDays: value })} />
          <NumberField label="Dias de gracia" name="planGraceDays" value={form.graceDays} onChange={(value) => onFormChange({ ...form, graceDays: value })} />
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
              Disponible para entrar con esta membresia en todas las sucursales activas del gimnasio.
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
          {saving ? "Guardando..." : editingPlanId ? "Guardar cambios del plan" : "Crear plan de membresia"}
        </button>
      </form>
    </AppDrawer>
  );
}
