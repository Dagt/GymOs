"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  createInventoryMovement,
  getBranches,
  getCurrentOrganization,
  getInventoryMovements,
  getInventoryStock,
  getLowInventoryStock,
  getStoredSession,
  type BranchSummary,
  type InventoryMovement,
  type InventoryMovementPayload,
  type InventoryMovementType,
  type InventoryStock,
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
  const [lowStock, setLowStock] = useState<InventoryStock[]>([]);
  const [movements, setMovements] = useState<InventoryMovement[]>([]);
  const [selectedProductId, setSelectedProductId] = useState("");
  const [movementType, setMovementType] = useState<ManualMovementType>("PURCHASE");
  const [quantity, setQuantity] = useState(1);
  const [unitCostCents, setUnitCostCents] = useState("");
  const [reorderPoint, setReorderPoint] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showMovementDrawer, setShowMovementDrawer] = useState(false);

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!active) return;
      if (!stored) { setLoading(false); return; }
      setSession(stored);
      try {
        const [loadedOrganization, loadedBranches, loadedStock, loadedLowStock, loadedMovements] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken), getInventoryStock(stored.accessToken),
          getLowInventoryStock(stored.accessToken), getInventoryMovements(stored.accessToken),
        ]);
        if (!active) return;
        setOrganization(loadedOrganization); setBranches(loadedBranches); setStock(loadedStock);
        setLowStock(loadedLowStock); setMovements(loadedMovements);
        setSelectedProductId(loadedStock[0]?.productId ?? "");
        setReorderPoint(String(loadedStock[0]?.reorderPoint ?? 0));
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar inventario.");
      } finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, []);

  const selectedStock = useMemo(() => stock.find((item) => item.productId === selectedProductId) ?? null, [selectedProductId, stock]);
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;

  async function refresh(activeSession = session, productId = selectedProductId) {
    if (!activeSession) return;
    const [nextStock, nextLowStock, nextMovements] = await Promise.all([
      getInventoryStock(activeSession.accessToken), getLowInventoryStock(activeSession.accessToken),
      getInventoryMovements(activeSession.accessToken, productId || undefined),
    ]);
    setStock(nextStock); setLowStock(nextLowStock); setMovements(nextMovements);
  }

  function selectProduct(product: InventoryStock) {
    setSelectedProductId(product.productId); setReorderPoint(String(product.reorderPoint));
    void refresh(session, product.productId);
  }

  function selectDrawerProduct(productId: string) {
    setSelectedProductId(productId);
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
      setReason(""); setUnitCostCents(""); setReorderPoint(String(updated.reorderPoint));
      setMessage(`${updated.productName}: existencia actual ${updated.quantityOnHand}.`);
      await refresh(session, selectedProductId); setShowMovementDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar movimiento.");
    } finally { setSaving(false); }
  }

  return {
    activeBranch, error, loading, lowStock, message, movementType, movements, organization,
    quantity, reason, refresh, reorderPoint, saving, selectedProductId, selectedStock, session,
    setMovementType, setQuantity, setReason, setReorderPoint, setShowMovementDrawer, setUnitCostCents,
    showMovementDrawer, stock, submitMovement, selectDrawerProduct, selectProduct, unitCostCents,
  };
}
