"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  createInventoryMovement,
  getBranches,
  getCurrentOrganization,
  getLowInventoryStock,
  getStoredSession,
  searchInventoryMovements,
  searchInventoryStock,
  type BranchSummary,
  type InventoryMovement,
  type InventoryMovementPayload,
  type InventoryMovementType,
  type InventoryStock,
  type InventoryStockSearchParams,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";
import { updateInventorySettings } from "@/lib/api/inventory";

type ManualMovementType = Exclude<InventoryMovementType, "SALE">;

export function useInventoryController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [stock, setStock] = useState<InventoryStock[]>([]);
  const [stockQuery, setStockQuery] = useState("");
  const [debouncedStockQuery, setDebouncedStockQuery] = useState("");
  const [stockPage, setStockPage] = useState(0);
  const [stockTotal, setStockTotal] = useState(0);
  const [stockTotalPages, setStockTotalPages] = useState(0);
  const [stockSort, setStockSort] = useState<NonNullable<InventoryStockSearchParams["sort"]>>("name,asc");
  const [lowStock, setLowStock] = useState<InventoryStock[]>([]);
  const [movements, setMovements] = useState<InventoryMovement[]>([]);
  const [movementPage, setMovementPage] = useState(0);
  const [movementTotalPages, setMovementTotalPages] = useState(0);
  const [selectedProductId, setSelectedProductId] = useState("");
  const [movementType, setMovementType] = useState<ManualMovementType>("PURCHASE");
  const [quantity, setQuantity] = useState(1);
  const [unitCostCents, setUnitCostCents] = useState("");
  const [reorderPoint, setReorderPoint] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [stockLoading, setStockLoading] = useState(false);
  const [movementLoading, setMovementLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showMovementDrawer, setShowMovementDrawer] = useState(false);

  useEffect(() => {
    let active = true;
    async function loadContext() {
      const stored = getStoredSession();
      if (!active) return;
      if (!stored) { setLoading(false); return; }
      setSession(stored);
      try {
        const [loadedOrganization, loadedBranches, loadedLowStock] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getLowInventoryStock(stored.accessToken),
        ]);
        if (!active) return;
        setOrganization(loadedOrganization); setBranches(loadedBranches); setLowStock(loadedLowStock);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar inventario.");
      } finally { if (active) setLoading(false); }
    }
    void loadContext();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedStockQuery(stockQuery.trim());
      setStockPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [stockQuery]);

  useEffect(() => {
    const accessToken = session?.accessToken;
    if (!accessToken) return;
    let active = true;

    async function loadStock(token: string) {
      await Promise.resolve();
      if (!active) return;
      setStockLoading(true);
      try {
        const page = await searchInventoryStock(token, {
          q: debouncedStockQuery,
          page: stockPage,
          size: 25,
          sort: stockSort,
        });
        if (!active) return;
        setStock(page.items);
        setStockTotal(page.totalElements);
        setStockTotalPages(page.totalPages);
        setSelectedProductId((current) => {
          if (current && page.items.some((item) => item.productId === current)) return current;
          const next = page.items[0]?.productId ?? "";
          setReorderPoint(String(page.items[0]?.reorderPoint ?? 0));
          return next;
        });
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar existencias.");
      } finally {
        if (active) setStockLoading(false);
      }
    }

    void loadStock(accessToken);
    return () => { active = false; };
  }, [debouncedStockQuery, session, stockPage, stockSort]);

  useEffect(() => {
    const accessToken = session?.accessToken;
    if (!accessToken) return;
    let active = true;

    async function loadMovements(token: string) {
      await Promise.resolve();
      if (!active) return;
      setMovementLoading(true);
      try {
        const page = await searchInventoryMovements(token, {
          productId: selectedProductId || undefined,
          page: movementPage,
          size: 25,
        });
        if (!active) return;
        setMovements(page.items);
        setMovementTotalPages(page.totalPages);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar movimientos.");
      } finally {
        if (active) setMovementLoading(false);
      }
    }

    void loadMovements(accessToken);
    return () => { active = false; };
  }, [movementPage, selectedProductId, session]);

  const selectedStock = useMemo(() => stock.find((item) => item.productId === selectedProductId) ?? null, [selectedProductId, stock]);
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;

  async function refresh(activeSession = session) {
    if (!activeSession) return;
    const [nextStock, nextLowStock, nextMovements] = await Promise.all([
      searchInventoryStock(activeSession.accessToken, {
        q: debouncedStockQuery, page: stockPage, size: 25, sort: stockSort,
      }),
      getLowInventoryStock(activeSession.accessToken),
      searchInventoryMovements(activeSession.accessToken, {
        productId: selectedProductId || undefined, page: movementPage, size: 25,
      }),
    ]);
    setStock(nextStock.items); setStockTotal(nextStock.totalElements); setStockTotalPages(nextStock.totalPages);
    setLowStock(nextLowStock); setMovements(nextMovements.items); setMovementTotalPages(nextMovements.totalPages);
  }

  function selectProduct(product: InventoryStock) {
    setSelectedProductId(product.productId); setReorderPoint(String(product.reorderPoint)); setMovementPage(0);
  }

  function selectDrawerProduct(productId: string) {
    setSelectedProductId(productId); setMovementPage(0);
    const nextStock = stock.find((item) => item.productId === productId);
    setReorderPoint(String(nextStock?.reorderPoint ?? 0));
  }

  async function submitMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedProductId) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      const desiredReorderPoint = reorderPoint.trim() ? Number(reorderPoint) : 0;
      if (desiredReorderPoint !== (selectedStock?.reorderPoint ?? 0)) {
        await updateInventorySettings(session.accessToken, selectedProductId, { reorderPoint: desiredReorderPoint });
      }
      const payload: InventoryMovementPayload = {
        productId: selectedProductId, movementType, quantity,
        unitCostCents: unitCostCents.trim() ? Math.round(Number(unitCostCents) * 100) : null,
        reason,
      };
      const updated = await createInventoryMovement(session.accessToken, payload);
      setReason(""); setUnitCostCents(""); setReorderPoint(String(updated.reorderPoint)); setMovementPage(0);
      setMessage(`${updated.productName}: existencia actual ${updated.quantityOnHand}.`);
      await refresh(session); setShowMovementDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar movimiento.");
    } finally { setSaving(false); }
  }

  return {
    activeBranch, error, loading, lowStock, message, movementLoading, movementPage, movementTotalPages,
    movementType, movements, organization, quantity, reason, refresh, reorderPoint, saving,
    selectedProductId, selectedStock, session, setMovementPage, setMovementType, setQuantity, setReason,
    setReorderPoint, setShowMovementDrawer, setStockPage, setStockQuery, setStockSort, setUnitCostCents,
    showMovementDrawer, stock, stockLoading, stockPage, stockQuery, stockSort, stockTotal,
    stockTotalPages, submitMovement, selectDrawerProduct, selectProduct, unitCostCents,
  };
}
