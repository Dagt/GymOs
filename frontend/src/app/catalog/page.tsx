"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createMembershipPlan,
  createProduct,
  getPlans,
  getProducts,
  getStoredSession,
  updateMembershipPlan,
  type MembershipPlanPayload,
  type PlanSummary,
  type ProductPayload,
  type ProductSummary,
  type StoredSession,
} from "@/lib/api";
import {
  CatalogToolbar,
  PlanDrawer,
  PlanList,
  ProductDrawer,
  ProductList,
} from "./CatalogSections";
import { emptyPlan, emptyProduct, planToDraft, type PlanDraft } from "./CatalogHelpers";

export default function CatalogPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ProductPayload>(emptyProduct);
  const [planForm, setPlanForm] = useState<PlanDraft>(emptyPlan);
  const [editingPlanId, setEditingPlanId] = useState<string | null>(null);
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showProductDrawer, setShowProductDrawer] = useState(false);
  const [showPlanDrawer, setShowPlanDrawer] = useState(false);

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
        const [loadedProducts, loadedPlans] = await loadCatalog(storedSession.accessToken);
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

  async function submitProduct(event: FormEvent<HTMLFormElement>) {
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
      const [loadedProducts, loadedPlans] = await loadCatalog(session.accessToken);
      setProducts(loadedProducts);
      setPlans(loadedPlans);
      setForm(emptyProduct);
      setPrice("");
      setShowProductDrawer(false);
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
      const payload: MembershipPlanPayload = {
        sku: planForm.sku.trim(),
        name: planForm.name.trim(),
        description: planForm.description.trim() || null,
        priceCents: Math.round(Number(planForm.price || "0") * 100),
        currency: planForm.currency.trim().toUpperCase() || "MXN",
        durationDays: Number(planForm.durationDays || "30"),
        graceDays: Number(planForm.graceDays || "0"),
        benefits: planForm.benefitsJson.trim() || null,
        multiBranchAccess: planForm.multiBranchAccess,
        status: planForm.status,
      };
      if (editingPlanId) {
        await updateMembershipPlan(session.accessToken, editingPlanId, payload);
      } else {
        await createMembershipPlan(session.accessToken, payload);
      }
      const [loadedProducts, loadedPlans] = await loadCatalog(session.accessToken);
      setProducts(loadedProducts);
      setPlans(loadedPlans);
      resetPlanDrawer();
      setMessage(editingPlanId ? "Plan de membresia actualizado." : "Plan de membresia creado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar plan.");
    } finally {
      setSaving(false);
    }
  }

  function openPlanCreation() {
    setEditingPlanId(null);
    setPlanForm(emptyPlan);
    setShowPlanDrawer(true);
  }

  function openPlanEdition(plan: PlanSummary) {
    setEditingPlanId(plan.id);
    setPlanForm(planToDraft(plan));
    setShowPlanDrawer(true);
  }

  function resetPlanDrawer() {
    setPlanForm(emptyPlan);
    setEditingPlanId(null);
    setShowPlanDrawer(false);
  }

  if (loading && !session) {
    return <LoadingScreen text="Cargando catalogo..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  const sellableProducts = products.filter((product) => product.productType !== "MEMBERSHIP_PLAN");

  return (
    <AppShell
      actions={[
        { href: "/pos", label: "Abrir POS" },
        { href: "/reception", label: "Inicio operativo" },
      ]}
      eyebrow="Administracion"
      session={session}
      subtitle="Productos, servicios y planes disponibles para vender desde recepcion."
      title="Catalogo"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? (
        <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
          {message}
        </p>
      ) : null}

      <section className="grid gap-6">
        <CatalogToolbar
          onNewPlan={openPlanCreation}
          onNewProduct={() => setShowProductDrawer(true)}
        />

        <section className="grid gap-6 lg:grid-cols-2">
          <ProductList loading={loading} products={sellableProducts} />
          <PlanList plans={plans} onEditPlan={openPlanEdition} />
        </section>
      </section>

      <ProductDrawer
        form={form}
        open={showProductDrawer}
        price={price}
        saving={saving}
        onClose={() => setShowProductDrawer(false)}
        onFormChange={setForm}
        onPriceChange={setPrice}
        onSubmit={submitProduct}
      />

      <PlanDrawer
        editingPlanId={editingPlanId}
        form={planForm}
        open={showPlanDrawer}
        saving={saving}
        onClose={resetPlanDrawer}
        onFormChange={setPlanForm}
        onSubmit={submitPlan}
      />
    </AppShell>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="El catalogo requiere una cuenta con permisos para consultar o administrar productos y planes."
      title="Inicia sesion para ver el catalogo"
    />
  );
}

function loadCatalog(accessToken: string) {
  return Promise.all([
    getProducts(accessToken),
    getPlans(accessToken),
  ]);
}
