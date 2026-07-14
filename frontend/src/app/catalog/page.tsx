"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import {
  createProduct,
  getPlans,
  getProducts,
  getStoredSession,
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

export default function CatalogPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ProductPayload>(emptyProduct);
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
          setError(exception instanceof Error ? exception.message : "No fue posible cargar catalogo.");
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

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/pos">
              POS
            </Link>
            <h1 className="mt-1 text-3xl font-semibold">Catalogo</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Productos y planes disponibles para vender desde recepcion.
            </p>
          </div>
          <Link
            className="inline-flex h-11 items-center justify-center rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
            href="/reception"
          >
            Recepcion
          </Link>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[0.9fr_1.1fr]">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Nuevo producto</h2>
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
                <option value="PHYSICAL">Producto fisico</option>
                <option value="SERVICE">Servicio</option>
                <option value="MEMBERSHIP_PLAN">Plan de membresia</option>
              </select>
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Descripcion
              <textarea
                className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => setForm({ ...form, description: event.target.value })}
                value={form.description ?? ""}
              />
            </label>
            {error ? (
              <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
                {error}
              </p>
            ) : null}
            {message ? (
              <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
                {message}
              </p>
            ) : null}
            <button
              className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={saving}
              type="submit"
            >
              {saving ? "Guardando..." : "Crear producto"}
            </button>
          </form>
        </section>

        <section className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-xl font-semibold">Productos</h2>
              <span className="text-sm text-[#6f7a73]">{products.length}</span>
            </div>
            <div className="mt-4 grid gap-3">
              {loading ? <p className="text-sm text-[#5d6b63]">Cargando catalogo...</p> : null}
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

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Planes</h2>
            <div className="mt-4 grid gap-3">
              {plans.map((plan) => (
                <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={plan.id}>
                  <p className="font-semibold">{plan.name}</p>
                  <p className="mt-1 text-sm text-[#5d6b63]">
                    {plan.durationDays} dias - gracia {plan.graceDays} - {formatMoney(plan.priceCents, plan.currency)}
                  </p>
                </article>
              ))}
            </div>
          </section>
        </section>
      </section>
    </main>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
        onChange={(event) => onChange(event.target.value)}
        type={type}
        value={value}
      />
    </label>
  );
}

function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesion</h1>
        <Link
          className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
          href="/login"
        >
          Iniciar sesion
        </Link>
      </section>
    </main>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
