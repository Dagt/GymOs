"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  archiveProduct,
  changeProductStatus,
  createProduct,
  getCatalogCategories,
  getPlans,
  getStoredSession,
  restoreProduct,
  searchProducts,
  uploadProductImage,
  updateProduct,
  type CatalogCategory,
  type PlanSummary,
  type ProductPayload,
  type ProductSummary,
  type StoredSession,
} from "@/lib/api";
import {
  emptyProduct,
  productDraftToPayload,
  productToDraft,
} from "./CatalogHelpers";
import { useCatalogFilters } from "@/features/catalog/hooks/useCatalogFilters";
import { useCatalogPlanEditor } from "@/features/catalog/hooks/useCatalogPlanEditor";

export function useCatalogController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [totalProducts, setTotalProducts] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [categories, setCategories] = useState<CatalogCategory[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ProductPayload>(emptyProduct);
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [price, setPrice] = useState("");
  const [productImage, setProductImage] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showProductDrawer, setShowProductDrawer] = useState(false);
  const catalogFilters = useCatalogFilters();
  const filtersKey = JSON.stringify(catalogFilters.filters);
  const planEditor = useCatalogPlanEditor(session, reloadCatalog);

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
    setLoading(true);
    loadCatalog(session.accessToken, catalogFilters.filters)
      .then(([page, loadedPlans, loadedCategories]) => {
        if (!active) return;
        setProducts(page.items);
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
    setProducts(page.items);
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
      const saved = editingProductId
        ? await updateProduct(session.accessToken, editingProductId, payload)
        : await createProduct(session.accessToken, payload);
      if (productImage) await uploadProductImage(session.accessToken, saved.id, productImage);
      await reloadCatalog();
      resetProductDrawer();
      setMessage(editingProductId ? "Producto actualizado." : "Producto creado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el producto.");
    } finally {
      setSaving(false);
    }
  }

  function openProductCreation() {
    setEditingProductId(null);
    setForm(emptyProduct);
    setPrice("");
    setProductImage(null);
    setShowProductDrawer(true);
  }

  function openProductEdition(product: ProductSummary) {
    if (product.archived) return;
    setEditingProductId(product.id);
    setForm(productToDraft(product));
    setPrice(String(product.priceCents / 100));
    setProductImage(null);
    setShowProductDrawer(true);
  }

  function resetProductDrawer() {
    setEditingProductId(null);
    setForm(emptyProduct);
    setPrice("");
    setProductImage(null);
    setShowProductDrawer(false);
  }

  async function toggleProductStatus(product: ProductSummary) {
    if (!session || product.archived) return;
    await runMutation(
      () => changeProductStatus(session.accessToken, product.id, product.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"),
      product.status === "ACTIVE" ? "Producto desactivado." : "Producto activado.",
      "No fue posible cambiar el estado.",
    );
  }

  async function archiveCatalogProduct(product: ProductSummary) {
    if (!session || product.archived || !window.confirm(`Archivar ${product.name}? Se conservara en el historial.`)) return;
    await runMutation(
      () => archiveProduct(session.accessToken, product.id),
      "Producto archivado.",
      "No fue posible archivar el producto.",
    );
  }

  async function restoreCatalogProduct(product: ProductSummary) {
    if (!session || !product.archived || !window.confirm(`Restaurar ${product.name}? Quedara inactivo hasta que lo habilites.`)) return;
    await runMutation(
      () => restoreProduct(session.accessToken, product.id),
      "Producto restaurado como inactivo.",
      "No fue posible restaurar el producto.",
    );
  }

  async function runMutation(action: () => Promise<unknown>, successMessage: string, failureMessage: string) {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await action();
      await reloadCatalog();
      setMessage(successMessage);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : failureMessage);
    } finally {
      setSaving(false);
    }
  }

  return {
    archiveCatalogProduct,
    restoreCatalogProduct,
    catalogFilters,
    categories,
    editingProductId,
    editingPlanId: planEditor.editingId,
    error: error ?? planEditor.error,
    form,
    loading,
    message: message ?? planEditor.message,
    openPlanCreation: planEditor.create,
    openPlanEdition: planEditor.edit,
    openProductCreation,
    openProductEdition,
    planForm: planEditor.form,
    plans,
    price,
    productImage,
    saving: saving || planEditor.saving,
    products,
    session,
    setForm,
    setPlanForm: planEditor.setForm,
    setPrice,
    setProductImage,
    showPlanDrawer: planEditor.open,
    showProductDrawer,
    submitPlan: planEditor.submit,
    submitProduct,
    resetPlanDrawer: planEditor.close,
    resetProductDrawer,
    reloadCatalog,
    toggleProductStatus,
    totalPages,
    totalProducts,
    canArchive: hasPermission(session, "catalog.archive"),
    canCreate: hasPermission(session, "catalog.create"),
    canManageStatus: hasPermission(session, "catalog.status.manage"),
    canManageCategories: hasPermission(session, "catalog.category.manage"),
    canUpdate: hasPermission(session, "catalog.update"),
  };
}

function loadCatalog(accessToken: string, filters: import("@/lib/api").ProductSearchParams) {
  return Promise.all([
    searchProducts(accessToken, filters),
    getPlans(accessToken),
    getCatalogCategories(accessToken),
  ]);
}

function hasPermission(session: StoredSession | null, permission: string) {
  return Boolean(
    session?.user.permissions.includes(permission)
      || session?.user.permissions.includes("catalog.manage"),
  );
}
