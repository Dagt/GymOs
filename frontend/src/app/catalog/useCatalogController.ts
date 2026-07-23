"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  createMembershipPlan,
  createProduct,
  getPlans,
  getProducts,
  getStoredSession,
  updateMembershipPlan,
  type PlanSummary,
  type ProductPayload,
  type ProductSummary,
  type StoredSession,
} from "@/lib/api";
import {
  emptyPlan,
  emptyProduct,
  planDraftToPayload,
  planToDraft,
  productDraftToPayload,
  type PlanDraft,
} from "./CatalogHelpers";

export function useCatalogController() {
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
      if (!active) return;
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      try {
        const [loadedProducts, loadedPlans] = await loadCatalog(storedSession.accessToken);
        if (!active) return;
        setProducts(loadedProducts);
        setPlans(loadedPlans);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar catalogo.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadInitial();
    return () => {
      active = false;
    };
  }, []);

  async function reloadCatalog() {
    if (!session) return;
    const [loadedProducts, loadedPlans] = await loadCatalog(session.accessToken);
    setProducts(loadedProducts);
    setPlans(loadedPlans);
  }

  async function submitProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await createProduct(session.accessToken, productDraftToPayload(form, price));
      await reloadCatalog();
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
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = planDraftToPayload(planForm);
      if (editingPlanId) {
        await updateMembershipPlan(session.accessToken, editingPlanId, payload);
      } else {
        await createMembershipPlan(session.accessToken, payload);
      }
      await reloadCatalog();
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

  const sellableProducts = products.filter((product) => product.productType !== "MEMBERSHIP_PLAN");

  return {
    editingPlanId,
    error,
    form,
    loading,
    message,
    openPlanCreation,
    openPlanEdition,
    planForm,
    plans,
    price,
    saving,
    sellableProducts,
    session,
    setForm,
    setPlanForm,
    setPrice,
    setShowProductDrawer,
    showPlanDrawer,
    showProductDrawer,
    submitPlan,
    submitProduct,
    resetPlanDrawer,
  };
}

function loadCatalog(accessToken: string) {
  return Promise.all([
    getProducts(accessToken),
    getPlans(accessToken),
  ]);
}
