"use client";

import { type FormEvent, useEffect, useState } from "react";
import {
  approveInventoryPhysicalCount,
  cancelInventoryPhysicalCount,
  cancelInventoryTransfer,
  createInventoryMovement,
  createInventoryPhysicalCount,
  createInventoryTransfer,
  getAllOrganizationBranches,
  getBranches,
  getCurrentOrganization,
  getInventoryBranchSettings,
  getInventoryStockDetail,
  getInventorySummary,
  getStoredSession,
  receiveInventoryTransfer,
  updateInventoryPhysicalCountItem,
  searchInventoryMovements,
  searchInventoryStock,
  searchInventoryPhysicalCounts,
  searchInventoryTransfers,
  shipInventoryTransfer,
  type BranchSummary,
  type InventoryBranchSettings,
  type InventoryMovementPayload,
  type InventoryMovementType,
  type InventoryMovementSearchParams,
  type InventoryHistorySearchParams,
  type InventoryStock,
  type InventoryStockSearchParams,
  type InventorySummary,
  type InventoryPhysicalCount,
  type InventoryTransfer,
  type InventoryMovement,
  type OrganizationSummary,
  type StoredSession,
  updateInventoryBranchSettings,
  updateInventorySettings,
} from "@/lib/api";
import { downloadCsvFile, generateMovementsCsv, generateStockCsv } from "../inventory-utils";
import { usePaginatedQuery } from "@/lib/usePaginatedQuery";

type ManualMovementType = Exclude<InventoryMovementType, "SALE">;

export function useInventoryController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [stockQuery, setStockQuery] = useState("");
  const [movementQuery, setMovementQuery] = useState<string>("");
  const [movementProduct, setMovementProduct] = useState<InventoryStock | null>(null);
  const token = session?.accessToken;
  const stockList = usePaginatedQuery<InventoryStock, InventoryStockSearchParams>(token, searchInventoryStock, { sort: "name,asc" });
  const movementList = usePaginatedQuery<InventoryMovement, InventoryMovementSearchParams>(token, searchInventoryMovements, {});
  const lowStockList = usePaginatedQuery<InventoryStock, InventoryStockSearchParams>(token, searchInventoryStock, { lowStockOnly: true, sort: "quantity,asc" });
  const countList = usePaginatedQuery<InventoryPhysicalCount, InventoryHistorySearchParams>(token, searchInventoryPhysicalCounts, {});
  const transferList = usePaginatedQuery<InventoryTransfer, InventoryHistorySearchParams>(token, searchInventoryTransfers, {});
  // Operational indicators must not depend on whichever history page is open.
  const activeCounts = usePaginatedQuery<InventoryPhysicalCount, InventoryHistorySearchParams>(token, searchInventoryPhysicalCounts, { activeOnly: true }, 1);
  const activeTransfers = usePaginatedQuery<InventoryTransfer, InventoryHistorySearchParams>(token, searchInventoryTransfers, { activeOnly: true }, 1);

  const [summary, setSummary] = useState<InventorySummary | null>(null);
  const [branchSettings, setBranchSettings] = useState<InventoryBranchSettings | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<InventoryStock | null>(null);
  const selectedStock = selectedProduct ?? stockList.items[0] ?? null;
  const selectedProductId = selectedStock?.productId ?? "";
  const [movementType, setMovementType] = useState<ManualMovementType>("PURCHASE");
  const [quantity, setQuantity] = useState(1);
  const [unitCostCents, setUnitCostCents] = useState("");
  const [reorderPointDraft, setReorderPoint] = useState<string | null>(null);
  const reorderPoint = reorderPointDraft ?? String(selectedStock?.reorderPoint ?? 0);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [showMovementDrawer, setShowMovementDrawer] = useState(false);
  const [showPhysicalCountDrawer, setShowPhysicalCountDrawer] = useState(false);
  const [showTransferDrawer, setShowTransferDrawer] = useState(false);

  useEffect(() => {
    let active = true;
    async function loadContext() {
      const stored = getStoredSession();
      if (!active) return;
      if (!stored) { setLoading(false); return; }
      setSession(stored);
      try {
        const [loadedOrganization, loadedBranches, loadedSummary, loadedSettings] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getAllOrganizationBranches(stored.accessToken).catch(() => getBranches(stored.accessToken)),
          getInventorySummary(stored.accessToken).catch(() => null),
          getInventoryBranchSettings(stored.accessToken).catch(() => null),
        ]);
        if (!active) return;
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setSummary(loadedSummary);
        setBranchSettings(loadedSettings);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar inventario.");
      } finally { if (active) setLoading(false); }
    }
    void loadContext();
    return () => { active = false; };
  }, []);

  const updateStockFilters = stockList.setFilters;
  const updateMovementFilters = movementList.setFilters;
  useEffect(() => {
    const timer = window.setTimeout(() => updateStockFilters((current) => ({ ...current, q: stockQuery.trim() })), 300);
    return () => window.clearTimeout(timer);
  }, [stockQuery, updateStockFilters]);

  useEffect(() => {
    const timer = window.setTimeout(() => updateMovementFilters((current) => ({ ...current, q: movementQuery.trim() })), 300);
    return () => window.clearTimeout(timer);
  }, [movementQuery, updateMovementFilters]);

  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;

  async function refresh(activeSession = session) {
    if (!activeSession) return;
    stockList.refresh(); movementList.refresh(); lowStockList.refresh(); countList.refresh();
    transferList.refresh(); activeCounts.refresh(); activeTransfers.refresh();
    try {
      const [nextSummary, nextSelected] = await Promise.all([
        getInventorySummary(activeSession.accessToken),
        selectedProductId ? getInventoryStockDetail(activeSession.accessToken, selectedProductId) : Promise.resolve(null),
      ]);
      setSummary(nextSummary);
      setSelectedProduct(nextSelected);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar inventario.");
    }
  }

  function selectProduct(product: InventoryStock) {
    setSelectedProduct(product);
    setReorderPoint(String(product.reorderPoint));
  }

  function selectMovementFilter(product: InventoryStock | null) {
    setMovementProduct(product);
    movementList.setFilters((current) => ({ ...current, productId: product?.productId }));
  }

  function clearMovementFilters() {
    setMovementProduct(null);
    setMovementQuery("");
    movementList.setFilters({});
  }

  async function toggleAllowNegativeStock(allow: boolean) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      const updated = await updateInventoryBranchSettings(session.accessToken, allow);
      setBranchSettings(updated);
      setMessage(`Política de inventario actualizada: Stock negativo ${allow ? "permitido" : "bloqueado"}.`);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la política de inventario.");
    } finally {
      setSaving(false);
    }
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
      setReason(""); setUnitCostCents(""); setReorderPoint(String(updated.reorderPoint)); movementList.setPage(0);
      setSelectedProduct(updated);
      setMessage(`${updated.productName}: existencia actual ${updated.quantityOnHand}.`);
      await refresh(session);
      setShowMovementDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar movimiento.");
    } finally {
      setSaving(false);
    }
  }

  async function startPhysicalCount(notes?: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await createInventoryPhysicalCount(session.accessToken, notes);
      countList.setPage(0);
      setMessage("Sesión de conteo físico iniciada.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar el conteo físico.");
    } finally { setSaving(false); }
  }

  async function savePhysicalCountItem(countId: string, productId: string, countedQty: number | null, notes?: string) {
    if (!session) return;
    try {
      await updateInventoryPhysicalCountItem(session.accessToken, countId, productId, { countedQuantity: countedQty, notes });
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar la partida.");
    }
  }

  async function approveCount(countId: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await approveInventoryPhysicalCount(session.accessToken, countId);
      setMessage("Conteo físico aprobado y diferencias ajustadas en inventario.");
      await refresh(session);
      setShowPhysicalCountDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible aprobar el conteo físico.");
    } finally { setSaving(false); }
  }

  async function cancelCount(countId: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await cancelInventoryPhysicalCount(session.accessToken, countId);
      setMessage("Conteo físico cancelado.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cancelar el conteo físico.");
    } finally { setSaving(false); }
  }

  async function handleCreateTransfer(destinationBranchId: string, notes: string, items: Array<{ productId: string; requestedQuantity: number }>) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      const created = await createInventoryTransfer(session.accessToken, { destinationBranchId, notes, items });
      transferList.setPage(0);
      setMessage(`Solicitud de transferencia a sucursal creada con éxito.`);
      await refresh(session);
      setShowTransferDrawer(false);
      return created;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear transferencia.");
    } finally { setSaving(false); }
  }

  async function handleShipTransfer(transferId: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await shipInventoryTransfer(session.accessToken, transferId);
      setMessage("Transferencia enviada. Inventario descontado de origen.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible enviar transferencia.");
    } finally { setSaving(false); }
  }

  async function handleReceiveTransfer(transferId: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await receiveInventoryTransfer(session.accessToken, transferId);
      setMessage("Transferencia recibida. Inventario ingresado a destino.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible recibir transferencia.");
    } finally { setSaving(false); }
  }

  async function handleCancelTransfer(transferId: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await cancelInventoryTransfer(session.accessToken, transferId);
      setMessage("Transferencia cancelada.");
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cancelar transferencia.");
    } finally { setSaving(false); }
  }

  function exportStockCsv() {
    if (stockList.loading || stockList.items.length === 0) return;
    const content = generateStockCsv(stockList.items);
    const dateStr = new Date().toISOString().split("T")[0];
    downloadCsvFile(content, `inventario_existencias_pagina_${stockList.page + 1}_${dateStr}.csv`);
  }

  function exportMovementsCsv() {
    if (movementList.loading || movementList.items.length === 0) return;
    const content = generateMovementsCsv(movementList.items);
    const dateStr = new Date().toISOString().split("T")[0];
    downloadCsvFile(content, `inventario_movimientos_pagina_${movementList.page + 1}_${dateStr}.csv`);
  }

  return {
    activeBranch, approveCount, branchSettings, branches, cancelCount, clearMovementFilters,
    error: error ?? stockList.error ?? movementList.error ?? lowStockList.error ?? countList.error ?? transferList.error ?? activeCounts.error ?? activeTransfers.error,
    exportMovementsCsv, exportStockCsv,
    handleCancelTransfer, handleCreateTransfer, handleReceiveTransfer, handleShipTransfer,
    loading, message, movementQuery, movementProduct, movementType, organization, quantity, reason, refresh, reorderPoint, saving,
    stockList, movementList, lowStockList, countList, transferList,
    activeCount: activeCounts.items[0] ?? null, activeCountLoading: activeCounts.loading,
    activeTransfersCount: activeTransfers.total,
    savePhysicalCountItem, selectMovementFilter, selectProduct, selectedProductId, selectedStock, session,
    setMovementQuery, setMovementType, setQuantity, setReason, setReorderPoint,
    setMovementTypeFilter: (type: string) => movementList.setFilters((current) => ({ ...current, type: type as InventoryMovementType | "" })),
    setShowMovementDrawer, setShowPhysicalCountDrawer, setShowTransferDrawer, setStockQuery,
    setStockSort: (sort: NonNullable<InventoryStockSearchParams["sort"]>) => stockList.setFilters((current) => ({ ...current, sort })),
    setUnitCostCents, showMovementDrawer, showPhysicalCountDrawer, showTransferDrawer,
    startPhysicalCount, stockQuery, submitMovement, summary, toggleAllowNegativeStock, unitCostCents,
  };
}
