"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  archiveProduct,
  changeProductStatus,
  createProduct,
  getCatalogCategories,
  getInventoryStockDetail,
  getPlans,
  getStoredSession,
  removeProductImage,
  restoreProduct,
  searchProducts,
  updateInventorySettings,
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
import { emptyProduct, productDraftToPayload, productToDraft } from "./CatalogHelpers";
import { useCatalogFilters } from "@/features/catalog/hooks/useCatalogFilters";
import { useCatalogPlanEditor } from "@/features/catalog/hooks/useCatalogPlanEditor";
import { lastPage } from "@/lib/pagination";

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
  const [reorderPoint, setReorderPoint] = useState("0");
  const [tagsText, setTagsText] = useState("");
  const [productImage, setProductImage] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showProductDrawer, setShowProductDrawer] = useState(false);
  const catalogFilters = useCatalogFilters();
  const filtersKey = JSON.stringify(catalogFilters.filters);
  const planEditor = useCatalogPlanEditor(session, reloadCatalog);
  const canManageInventory = hasDirectPermission(session, "inventory.manage");

  useEffect(() => {
    queueMicrotask(() => {
      const storedSession = getStoredSession();
      setSession(storedSession);
      if (!storedSession) setLoading(false);
    });
  }, []);

  useEffect(() => {
    const accessToken = session?.accessToken;
    if (!accessToken) return;
    let active = true;

    async function refreshCatalog(token: string) {
      await Promise.resolve();
      if (!active) return;
      setLoading(true);
      try {
        const [page, loadedPlans, loadedCategories, loadedSummary] = await loadCatalog(
          token,
          catalogFilters.filters,
        );
        if (!active) return;
        if (page.page > lastPage(page.totalElements, page.size)) {
          catalogFilters.update("page", String(lastPage(page.totalElements, page.size)));
          return;
        }
        setProducts(page.items);
        setTotalProducts(page.totalElements);
        setTotalPages(page.totalPages);
        setPlans(loadedPlans);
        setCategories(loadedCategories);
        setSummary(loadedSummary);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar catálogo.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void refreshCatalog(accessToken);
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, filtersKey]);

  async function reloadCatalog() {
    const activeSession = getStoredSession() ?? session;
    if (!activeSession) return;
    const [page, loadedPlans, loadedCategories, loadedSummary] = await loadCatalog(
      activeSession.accessToken,
      catalogFilters.filters,
    );
    setSession(activeSession);
    if (page.page > lastPage(page.totalElements, page.size)) {
      catalogFilters.update("page", String(lastPage(page.totalElements, page.size)));
      return;
    }
    setProducts(page.items);
    setTotalProducts(page.totalElements);
    setTotalPages(page.totalPages);
    setPlans(loadedPlans);
    setCategories(loadedCategories);
    setSummary(loadedSummary);
  }

  async function submitProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const activeSession = getStoredSession() ?? session;
    if (!activeSession) return;
    const wasEditing = Boolean(editingProductId);
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload = {
        ...productDraftToPayload(form, price),
        tags: parseTags(tagsText),
      };
      const saved = editingProductId
        ? await updateProduct(activeSession.accessToken, editingProductId, payload)
        : await createProduct(activeSession.accessToken, payload);
      const warnings: string[] = [];

      if (canManageInventory && payload.productType === "PHYSICAL" && payload.inventoryTracked) {
        const parsedReorderPoint = Number(reorderPoint || "0");
        if (!Number.isFinite(parsedReorderPoint) || parsedReorderPoint < 0) {
          throw new Error("El stock mínimo debe ser cero o mayor.");
        }
        try {
          await updateInventorySettings(activeSession.accessToken, saved.id, {
            reorderPoint: parsedReorderPoint,
          });
        } catch {
          warnings.push("no se guardó el stock mínimo");
        }
      }

      if (productImage) {
        try {
          const withImage = await uploadProductImage(activeSession.accessToken, saved.id, productImage);
          setExistingImageObjectKey(withImage.imageObjectKey);
        } catch {
          warnings.push("no se cargó la imagen");
        }
      }

      await reloadCatalog();
      resetProductDrawer();
      const baseMessage = wasEditing ? "Artículo actualizado." : "Artículo creado.";
      setMessage(warnings.length ? `${baseMessage} ${warnings.join(" y ")}.` : baseMessage);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar el artículo.");
    } finally {
      setSaving(false);
    }
  }

  async function removeCurrentProductImage() {
    const activeSession = getStoredSession() ?? session;
    if (!activeSession || !editingProductId || !existingImageObjectKey) return;
    if (!window.confirm("¿Eliminar la imagen actual?")) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await removeProductImage(activeSession.accessToken, editingProductId);
      setExistingImageObjectKey(null);
      setProductImage(null);
      await reloadCatalog();
      setMessage("Imagen eliminada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible eliminar la imagen.");
    } finally {
      setSaving(false);
    }
  }

  function openProductCreation() {
    setEditingProductId(null);
    setExistingImageObjectKey(null);
    setForm(emptyProduct);
    setPrice("");
    setReorderPoint("0");
    setTagsText("");
    setProductImage(null);
    setShowProductDrawer(true);
  }

  async function openProductEdition(product: ProductSummary) {
    if (product.archived) return;
    setEditingProductId(product.id);
    setExistingImageObjectKey(product.imageObjectKey);
    setForm(productToDraft(product));
    setPrice(String(product.priceCents / 100));
    setReorderPoint("0");
    setTagsText(product.tags.join(", "));
    setProductImage(null);
    setShowProductDrawer(true);

    const activeSession = getStoredSession() ?? session;
    if (!activeSession || !canManageInventory || product.productType !== "PHYSICAL" || !product.inventoryTracked) return;
    try {
      const stock = await getInventoryStockDetail(activeSession.accessToken, product.id);
      setReorderPoint(String(stock.reorderPoint));
    } catch {
      setReorderPoint("0");
    }
  }

  function resetProductDrawer() {
    setEditingProductId(null);
    setExistingImageObjectKey(null);
    setForm(emptyProduct);
    setPrice("");
    setReorderPoint("0");
    setTagsText("");
    setProductImage(null);
    setShowProductDrawer(false);
  }

  async function toggleProductStatus(product: ProductSummary) {
    const activeSession = getStoredSession() ?? session;
    if (!activeSession || product.archived) return;
    await runMutation(
      () => changeProductStatus(activeSession.accessToken, product.id, product.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"),
      product.status === "ACTIVE" ? "Artículo desactivado." : "Artículo activado.",
      "No fue posible cambiar el estado.",
    );
  }

  async function archiveCatalogProduct(product: ProductSummary) {
    const activeSession = getStoredSession() ?? session;
    if (!activeSession || product.archived || !window.confirm(`¿Archivar ${product.name}?`)) return;
    await runMutation(
      () => archiveProduct(activeSession.accessToken, product.id),
      "Artículo archivado.",
      "No fue posible archivar el artículo.",
    );
  }

  async function restoreCatalogProduct(product: ProductSummary) {
    const activeSession = getStoredSession() ?? session;
    if (!activeSession || !product.archived || !window.confirm(`¿Restaurar ${product.name}?`)) return;
    await runMutation(
      () => restoreProduct(activeSession.accessToken, product.id),
      "Artículo restaurado.",
      "No fue posible restaurar el artículo.",
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
    existingImageObjectKey,
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
    reorderPoint,
    removeCurrentProductImage,
    saving: saving || planEditor.saving,
    products,
    summary,
    session,
    setForm,
    setPlanForm: planEditor.setForm,
    setPrice,
    setProductImage,
    setReorderPoint,
    setTagsText,
    showPlanDrawer: planEditor.open,
    showProductDrawer,
    submitPlan: planEditor.submit,
    submitProduct,
    resetPlanDrawer: planEditor.close,
    resetProductDrawer,
    reloadCatalog,
    tagsText,
    toggleProductStatus,
    totalPages,
    totalProducts,
    canArchive: hasPermission(session, "catalog.archive"),
    canCreate: hasPermission(session, "catalog.create"),
    canManageStatus: hasPermission(session, "catalog.status.manage"),
    canManageCategories: hasPermission(session, "catalog.category.manage"),
    canManageImages: hasPermission(session, "catalog.image.manage"),
    canManageInventory,
    canUpdate: hasPermission(session, "catalog.update"),
    canReadCosts: hasPermission(session, "catalog.cost.read"),
  };
}

function loadCatalog(accessToken: string, filters: import("@/lib/api").ProductSearchParams) {
  return Promise.all([
    searchProducts(accessToken, filters),
    getPlans(accessToken),
    getCatalogCategories(accessToken, true),
    getCatalogSummary(accessToken),
  ]);
}

function parseTags(value: string) {
  return [...new Set(value.split(",").map((tag) => tag.trim()).filter(Boolean))];
}

function hasPermission(session: StoredSession | null, permission: string) {
  return Boolean(
    session?.user.permissions.includes(permission)
      || session?.user.permissions.includes("catalog.manage")
      || session?.user.permissions.includes("platform.admin"),
  );
}

function hasDirectPermission(session: StoredSession | null, permission: string) {
  return Boolean(
    session?.user.permissions.includes(permission)
      || session?.user.permissions.includes("platform.admin"),
  );
}
