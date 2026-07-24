"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  archiveProduct,
  changeProductStatus,
  createProduct,
  getCatalogCategories,
  getPlans,
  getStoredSession,
  removeProductImage,
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
import { getCatalogSummary } from "@/lib/api/catalog";
import type { CatalogSummary } from "@/lib/api/catalog-types";
import { updateInventorySettings } from "@/lib/api/inventory";
import { emptyProduct, productDraftToPayload, productToDraft } from "./CatalogHelpers";
import { useCatalogFilters } from "@/features/catalog/hooks/useCatalogFilters";
import { useCatalogPlanEditor } from "@/features/catalog/hooks/useCatalogPlanEditor";

export function useCatalogController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [summary, setSummary] = useState<CatalogSummary | null>(null);
  const [totalProducts, setTotalProducts] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [categories, setCategories] = useState<CatalogCategory[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [form, setForm] = useState<ProductPayload>(emptyProduct);
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [existingImageObjectKey, setExistingImageObjectKey] = useState<string | null>(null);
  const [price, setPrice] = useState("");
  const [minimumStock, setMinimumStock] = useState("");
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
      .then(([page, loadedPlans, loadedCategories, loadedSummary]) => {
        if (!active) return;
        setProducts(page.items); setTotalProducts(page.totalElements); setTotalPages(page.totalPages);
        setPlans(loadedPlans); setCategories(loadedCategories); setSummary(loadedSummary);
      })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar catálogo."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, filtersKey]);

  async function reloadCatalog() {
    if (!session) return;
    const [page, loadedPlans, loadedCategories, loadedSummary] = await loadCatalog(session.accessToken, catalogFilters.filters);
    setProducts(page.items); setTotalProducts(page.totalElements); setTotalPages(page.totalPages);
    setPlans(loadedPlans); setCategories(loadedCategories); setSummary(loadedSummary);
  }

  async function submitProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    const wasEditing = Boolean(editingProductId);
    setSaving(true); setError(null); setMessage(null);
    try {
      const payload = productDraftToPayload(form, price);
      const saved = editingProductId ? await updateProduct(session.accessToken, editingProductId, payload) : await createProduct(session.accessToken, payload);
      if (payload.productType === "PHYSICAL" && payload.inventoryTracked && minimumStock.trim()) {
        const reorderPoint = Number(minimumStock);
        if (!Number.isFinite(reorderPoint) || reorderPoint < 0) throw new Error("El mínimo de inventario debe ser cero o mayor.");
        await updateInventorySettings(session.accessToken, saved.id, { reorderPoint });
      }
      let imageWarning: string | null = null;
      if (productImage) {
        try {
          const withImage = await uploadProductImage(session.accessToken, saved.id, productImage);
          setExistingImageObjectKey(withImage.imageObjectKey);
        } catch { imageWarning = "El artículo se guardó, pero la imagen no pudo cargarse."; }
      }
      await reloadCatalog();
      resetProductDrawer();
      setMessage(imageWarning ?? (wasEditing ? "Artículo actualizado." : "Artículo creado."));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el artículo.");
    } finally { setSaving(false); }
  }

  async function removeCurrentProductImage() {
    if (!session || !editingProductId || !existingImageObjectKey) return;
    if (!window.confirm("¿Eliminar la imagen actual?")) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await removeProductImage(session.accessToken, editingProductId);
      setExistingImageObjectKey(null); setProductImage(null);
      await reloadCatalog(); setMessage("Imagen eliminada.");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible eliminar la imagen."); }
    finally { setSaving(false); }
  }

  function openProductCreation() {
    setEditingProductId(null); setExistingImageObjectKey(null); setForm(emptyProduct);
    setPrice(""); setMinimumStock(""); setProductImage(null); setShowProductDrawer(true);
  }
  function openProductEdition(product: ProductSummary) {
    if (product.archived) return;
    setEditingProductId(product.id); setExistingImageObjectKey(product.imageObjectKey);
    setForm(productToDraft(product)); setPrice(String(product.priceCents / 100));
    setMinimumStock(""); setProductImage(null); setShowProductDrawer(true);
  }
  function resetProductDrawer() {
    setEditingProductId(null); setExistingImageObjectKey(null); setForm(emptyProduct);
    setPrice(""); setMinimumStock(""); setProductImage(null); setShowProductDrawer(false);
  }

  async function toggleProductStatus(product: ProductSummary) {
    if (!session || product.archived) return;
    await runMutation(() => changeProductStatus(session.accessToken, product.id, product.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"), product.status === "ACTIVE" ? "Artículo desactivado." : "Artículo activado.", "No fue posible cambiar el estado.");
  }
  async function archiveCatalogProduct(product: ProductSummary) {
    if (!session || product.archived || !window.confirm(`¿Archivar ${product.name}?`)) return;
    await runMutation(() => archiveProduct(session.accessToken, product.id), "Artículo archivado.", "No fue posible archivar el artículo.");
  }
  async function restoreCatalogProduct(product: ProductSummary) {
    if (!session || !product.archived || !window.confirm(`¿Restaurar ${product.name}?`)) return;
    await runMutation(() => restoreProduct(session.accessToken, product.id), "Artículo restaurado.", "No fue posible restaurar el artículo.");
  }
  async function runMutation(action: () => Promise<unknown>, successMessage: string, failureMessage: string) {
    setSaving(true); setError(null); setMessage(null);
    try { await action(); await reloadCatalog(); setMessage(successMessage); }
    catch (exception) { setError(exception instanceof Error ? exception.message : failureMessage); }
    finally { setSaving(false); }
  }

  return {
    archiveCatalogProduct, restoreCatalogProduct, catalogFilters, categories, editingProductId,
    editingPlanId: planEditor.editingId, error: error ?? planEditor.error, existingImageObjectKey,
    form, loading, message: message ?? planEditor.message, minimumStock, openPlanCreation: planEditor.create,
    openPlanEdition: planEditor.edit, openProductCreation, openProductEdition,
    planForm: planEditor.form, plans, price, productImage, removeCurrentProductImage,
    saving: saving || planEditor.saving, products, summary, session, setForm,
    setMinimumStock, setPlanForm: planEditor.setForm, setPrice, setProductImage, showPlanDrawer: planEditor.open,
    showProductDrawer, submitPlan: planEditor.submit, submitProduct,
    resetPlanDrawer: planEditor.close, resetProductDrawer, reloadCatalog, toggleProductStatus,
    totalPages, totalProducts, canArchive: hasPermission(session, "catalog.archive"),
    canCreate: hasPermission(session, "catalog.create"), canManageStatus: hasPermission(session, "catalog.status.manage"),
    canManageCategories: hasPermission(session, "catalog.category.manage"), canManageImages: hasPermission(session, "catalog.image.manage"),
    canUpdate: hasPermission(session, "catalog.update"), canReadCosts: hasPermission(session, "catalog.cost.read"),
  };
}
function loadCatalog(accessToken: string, filters: import("@/lib/api").ProductSearchParams) { return Promise.all([searchProducts(accessToken, filters), getPlans(accessToken), getCatalogCategories(accessToken, true), getCatalogSummary(accessToken)]); }
function hasPermission(session: StoredSession | null, permission: string) { return Boolean(session?.user.permissions.includes(permission) || session?.user.permissions.includes("catalog.manage") || session?.user.permissions.includes("platform.admin")); }
