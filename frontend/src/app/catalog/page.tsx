"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createPlan,
  createProduct,
  getPlans,
  getProducts,
  getStoredSession,
  type PlanPayload,
  type PlanSummary,
  type ProductPayload,
  type ProductSummary,
  type StoredSession,
} from "@/lib/api";

const emptyProduct: ProductPayload = {
  sku: "",
  name: "",
  description: "",
  productType: "PHYSICAL",
  priceCents: 0,
  currency: "MXN",
  taxable: true,
  status: "ACTIVE",
};

type PlanDraft = {
  sku: string;
  name: string;
  description: string;
  price: string;
  currency: string;
  durationDays: string;
  graceDays: string;
  benefitsJson: string;
  status: PlanPayload["status"];
};

const emptyPlan: PlanDraft = {
  sku: "",
  name: "",
  description: "",
  price: "",
  currency: "MXN",
  durationDays: "30",
  graceDays: "0",
  benefitsJson: "",
  status: "ACTIVE",
};

export default function CatalogPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ProductPayload>(emptyProduct);
  const [planForm, setPlanForm] = useState<PlanDraft>(emptyPlan);
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) {
        return;
      }
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      try {
        const [loadedProducts, loadedPlans] = await Promise.all([
          getProducts(storedSession.accessToken),
          getPlans(storedSession.accessToken),
        ]);
        if (!active) {
          return;
        }
        setProducts(loadedProducts);
        setPlans(loadedPlans);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar catálogo.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadInitial();
    return () => {
      active = false;
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createProduct(session.accessToken, {
        ...form,
        priceCents: Math.round(Number(price || "0") * 100),
      });
      setProducts(await getProducts(session.accessToken));
      setPlans(await getPlans(session.accessToken));
      setForm(emptyProduct);
      setPrice("");
      setMessage("Producto creado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear producto.");
    } finally {
      setSaving(false);
    }
  }

  async function submitPlan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const createdProduct = await createProduct(session.accessToken, {
        sku: planForm.sku.trim(),
        name: planForm.name.trim(),
        description: planForm.description.trim() || null,
        productType: "MEMBERSHIP_PLAN",
        priceCents: Math.round(Number(planForm.price || "0") * 100),
        currency: planForm.currency.trim().toUpperCase() || "MXN",
        taxable: true,
        status: planForm.status,
      });
      await createPlan(session.accessToken, {
        productId: createdProduct.id,
        durationDays: Number(planForm.durationDays || "30"),
        graceDays: Number(planForm.graceDays || "0"),
        benefitsJson: planForm.benefitsJson.trim() || null,
        status: planForm.status,
      });
      const [loadedProducts, loadedPlans] = await Promise.all([
        getProducts(session.accessToken),
        getPlans(session.accessToken),
      ]);
      setProducts(loadedProducts);
      setPlans(loadedPlans);
      setPlanForm(emptyPlan);
      setMessage("Plan de membresia creado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear plan.");
    } finally {
      setSaving(false);
    }
  }

  if (loading && !session) {
    return <LoadingScreen text="Cargando catálogo..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  const sellableProducts = products.filter((product) => product.productType !== "MEMBERSHIP_PLAN");
  const membershipProductsWithoutPlan = products.filter(
    (product) => product.productType === "MEMBERSHIP_PLAN" && !plans.some((plan) => plan.productId === product.id),
  );

  return (
    <AppShell
      actions={[
        { href: "/pos", label: "Abrir POS" },
        { href: "/reception", label: "Inicio operativo" },
      ]}
      eyebrow="Administración"
      session={session}
      subtitle="Productos, servicios y planes disponibles para vender desde recepción."
      title="Catálogo"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? (
        <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
          {message}
        </p>
      ) : null}

      <section className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr]">
        <section className="grid gap-6">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <p className="app-eyebrow">Productos y servicios</p>
          <h2 className="mt-2 text-xl font-semibold">Nuevo producto de venta</h2>
          <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
            Articulos de mostrador y servicios. Las membresias se crean como planes en el formulario siguiente.
          </p>
          <form className="mt-5 grid gap-4" onSubmit={submit}>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="SKU" value={form.sku} onChange={(value) => setForm({ ...form, sku: value })} />
              <Field label="Nombre" value={form.name} onChange={(value) => setForm({ ...form, name: value })} />
              <Field label="Precio" type="number" value={price} onChange={setPrice} />
              <Field label="Moneda" value={form.currency} onChange={(value) => setForm({ ...form, currency: value })} />
            </div>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Tipo
              <select
                className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                onChange={(event) => setForm({ ...form, productType: event.target.value as ProductPayload["productType"] })}
                value={form.productType}
              >
                <option value="PHYSICAL">Producto físico</option>
                <option value="SERVICE">Servicio</option>
              </select>
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Descripción
              <textarea
                className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => setForm({ ...form, description: event.target.value })}
                value={form.description ?? ""}
              />
            </label>
            <button className="app-button h-11" disabled={saving} type="submit">
              {saving ? "Guardando..." : "Crear producto"}
            </button>
          </form>
        </section>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <p className="app-eyebrow">Membresias</p>
          <h2 className="mt-2 text-xl font-semibold">Nuevo plan de membresia</h2>
          <p className="mt-2 text-sm leading-6 text-[#5d6b63]">
            Esto crea el producto interno y el plan con duracion; por eso aparecera en el alta de socios.
          </p>
          <form className="mt-5 grid gap-4" onSubmit={submitPlan}>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Codigo del plan" value={planForm.sku} onChange={(value) => setPlanForm({ ...planForm, sku: value })} />
              <Field label="Nombre del plan" value={planForm.name} onChange={(value) => setPlanForm({ ...planForm, name: value })} />
              <Field label="Precio" type="number" value={planForm.price} onChange={(value) => setPlanForm({ ...planForm, price: value })} />
              <Field label="Moneda" value={planForm.currency} onChange={(value) => setPlanForm({ ...planForm, currency: value })} />
              <Field label="Duracion en dias" type="number" value={planForm.durationDays} onChange={(value) => setPlanForm({ ...planForm, durationDays: value })} />
              <Field label="Dias de gracia" type="number" value={planForm.graceDays} onChange={(value) => setPlanForm({ ...planForm, graceDays: value })} />
            </div>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Beneficios incluidos
              <textarea
                className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => setPlanForm({ ...planForm, benefitsJson: event.target.value })}
                placeholder="Ej. Acceso general, clases grupales, estacionamiento."
                value={planForm.benefitsJson}
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Estado
              <select
                className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                onChange={(event) => setPlanForm({ ...planForm, status: event.target.value as PlanPayload["status"] })}
                value={planForm.status}
              >
                <option value="ACTIVE">Activo</option>
                <option value="INACTIVE">Inactivo</option>
              </select>
            </label>
            <button className="app-button h-11" disabled={saving} type="submit">
              {saving ? "Guardando..." : "Crear plan de membresia"}
            </button>
          </form>
        </section>

        </section>

        <section className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-xl font-semibold">Productos y servicios</h2>
              <span className="text-sm text-[#6f7a73]">{sellableProducts.length}</span>
            </div>
            <div className="mt-4 grid gap-3">
              {loading ? <p className="text-sm text-[#5d6b63]">Cargando catálogo...</p> : null}
              {!loading && sellableProducts.length === 0 ? (
                <p className="text-sm text-[#5d6b63]">No hay productos o servicios registrados.</p>
              ) : null}
              {sellableProducts.map((product) => (
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

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Planes</h2>
            <div className="mt-4 grid gap-3">
              {plans.length === 0 ? <p className="text-sm text-[#5d6b63]">No hay planes de membresia activos.</p> : null}
              {plans.map((plan) => (
                <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={plan.id}>
                  <p className="font-semibold">{plan.name}</p>
                  <p className="mt-1 text-sm text-[#5d6b63]">
                    {plan.durationDays} días - gracia {plan.graceDays} - {formatMoney(plan.priceCents, plan.currency)}
                  </p>
                </article>
              ))}
              {membershipProductsWithoutPlan.length > 0 ? (
                <section className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                  <strong>Hay {membershipProductsWithoutPlan.length} membresia creada como producto sin duracion.</strong>
                  <p className="mt-1">
                    Para que aparezca en el alta de socios, crea el plan desde el formulario de nuevo plan de membresia.
                  </p>
                </section>
              ) : null}
            </div>
          </section>
        </section>
      </section>
    </AppShell>
  );
}

function Field({
  label,
  value,
  onChange,
  required = true,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  type?: string;
}) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
        min={type === "number" ? "0" : undefined}
        onChange={(event) => onChange(event.target.value)}
        required={required}
        type={type}
        value={value}
      />
    </label>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="El catálogo requiere una cuenta con permisos para consultar o administrar productos y planes."
      title="Inicia sesión para ver el catálogo"
    />
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
