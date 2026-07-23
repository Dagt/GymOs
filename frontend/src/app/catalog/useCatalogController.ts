"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  archiveProduct,
  changeProductStatus,
  createMembershipPlan,
  createProduct,
  getCatalogCategories,
  getPlans,
  getStoredSession,
  searchProducts,
  updateProduct,
  updateMembershipPlan,
  type CatalogCategory,
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
  productToDraft,
  type PlanDraft,
} from "./CatalogHelpers";
import { useCatalogFilters } from "@/features/catalog/hooks/useCatalogFilters";

export function useCatalogController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [totalProducts, setTotalProducts] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [categories, setCategories] = useState<CatalogCategory[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ProductPayload>(emptyProduct);
  const [planForm, setPlanForm] = useState<PlanDraft>(emptyPlan);
  const [editingPlanId, setEditingPlanId] = useState<string | null>(null);
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [price, setPrice] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showProductDrawer, setShowProductDrawer] = useState(false);
  const [showPlanDrawer, setShowPlanDrawer] = useState(false);
  const catalogFilters = useCatalogFilters();
  const filtersKey = JSON.stringify(catalogFilters.filters);

  useEffect(() => {
    queueMicrotask(() => {
      const storedSession = getStoredSession();
      setSession(storedSession);
      if (!storedSession) setLoading(false);
    });
  }, []);

  useEffect(() => {
    if (!session) return;
    let active = true;
    loadCatalog(session.accessToken, catalogFilters.filters)
      .then(([page, loadedPlans, loadedCategories]) => {
        if (!active) return;
        setProducts(page.items.filter((product) => product.productType !== "MEMBERSHIP_PLAN"));
        setTotalProducts(page.totalElements);
        setTotalPages(page.totalPages);
        setPlans(loadedPlans);
        setCategories(loadedCategories);
      })
      .catch((exception) => {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar catalogo.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // filtersKey is a stable snapshot of URL-backed filters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, filtersKey]);

  async function reloadCatalog() {
    if (!session) return;
    const [page, loadedPlans, loadedCategories] = await loadCatalog(session.accessToken, catalogFilters.filters);
    setProducts(page.items.filter((product) => product.productType !== "MEMBERSHIP_PLAN"));
    setTotalProducts(page.totalElements);
    setTotalPages(page.totalPages);
    setPlans(loadedPlans);
    setCategories(loadedCategories);
  }

  async function submitProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = productDraftToPayload(form, price);
      if (editingProductId) await updateProduct(session.accessToken, editingProductId, payload);
      else await createProduct(session.accessToken, payload);
      await reloadCatalog();
      resetProductDrawer();
      setMessage(editingProductId ? "Producto actualizado." : "Producto creado.");
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

  function openProductCreation() {
    setEditingProductId(null);
    setForm(emptyProduct);
    setPrice("");
    setShowProductDrawer(true);
  }

  function openProductEdition(product: ProductSummary) {
    setEditingProductId(product.id);
    setForm(productToDraft(product));
    setPrice(String(product.priceCents / 100));
    setShowProductDrawer(true);
  }

  function resetProductDrawer() {
    setEditingProductId(null);
    setForm(emptyProduct);
    setPrice("");
    setShowProductDrawer(false);
  }

  async function toggleProductStatus(product: ProductSummary) {
    if (!session) return;
    setSaving(true);
    try {
      await changeProductStatus(
        session.accessToken,
        product.id,
        product.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
      );
      await reloadCatalog();
      setMessage(product.status === "ACTIVE" ? "Producto desactivado." : "Producto activado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar el estado.");
    } finally {
      setSaving(false);
    }
  }

  async function archiveCatalogProduct(product: ProductSummary) {
    if (!session || !window.confirm(`Archivar ${product.name}? Se conservara en el historial.`)) return;
    setSaving(true);
    try {
      await archiveProduct(session.accessToken, product.id);
      await reloadCatalog();
      setMessage("Producto archivado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible archivar el producto.");
    } finally {
      setSaving(false);
    }
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

  return {
    archiveCatalogProduct,
    catalogFilters,
    categories,
    editingProductId,
    editingPlanId,
    error,
    form,
    loading,
    message,
    openPlanCreation,
    openPlanEdition,
    openProductCreation,
    openProductEdition,
    planForm,
    plans,
    price,
    saving,
    products,
    session,
    setForm,
    setPlanForm,
    setPrice,
    showPlanDrawer,
    showProductDrawer,
    submitPlan,
    submitProduct,
    resetPlanDrawer,
    resetProductDrawer,
    toggleProductStatus,
    totalPages,
    totalProducts,
  };
}

function loadCatalog(accessToken: string, filters: import("@/lib/api").ProductSearchParams) {
  return Promise.all([
    searchProducts(accessToken, filters),
    getPlans(accessToken),
    getCatalogCategories(accessToken),
  ]);
}
