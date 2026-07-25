"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import {
  approveInventoryPhysicalCount,
  cancelInventoryPhysicalCount,
  cancelInventoryTransfer,
  createInventoryMovement,
  createInventoryPhysicalCount,
  createInventoryTransfer,
  getBranches,
  getCurrentOrganization,
  getInventoryBranchSettings,
  getInventoryPhysicalCounts,
  getInventorySummary,
  getInventoryTransfers,
  getLowInventoryStock,
  getStoredSession,
  receiveInventoryTransfer,
  searchInventoryMovements,
  searchInventoryStock,
  shipInventoryTransfer,
  updateInventoryBranchSettings,
  updateInventoryPhysicalCountItem,
  updateInventorySettings,
  type BranchSummary,
  type InventoryBranchSettings,
  type InventoryMovement,
  type InventoryMovementPayload,
  type InventoryMovementType,
  type InventoryPhysicalCount,
  type InventoryStock,
  type InventoryStockSearchParams,
  type InventorySummary,
  type InventoryTransfer,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";
import { downloadCsvFile, generateMovementsCsv, generateStockCsv } from "@/features/inventory/inventory-utils";

type ManualMovementType = Exclude<InventoryMovementType, "SALE">;

export function useInventoryController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [summary, setSummary] = useState<InventorySummary | null>(null);
  const [branchSettings, setBranchSettings] = useState<InventoryBranchSettings | null>(null);
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
  const [physicalCounts, setPhysicalCounts] = useState<InventoryPhysicalCount[]>([]);
  const [transfers, setTransfers] = useState<InventoryTransfer[]>([]);

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
        const [loadedOrganization, loadedBranches, loadedLowStock, loadedSummary, loadedSettings, loadedCounts, loadedTransfers] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getLowInventoryStock(stored.accessToken),
          getInventorySummary(stored.accessToken).catch(() => null),
          getInventoryBranchSettings(stored.accessToken).catch(() => null),
          getInventoryPhysicalCounts(stored.accessToken).catch(() => []),
          getInventoryTransfers(stored.accessToken).catch(() => []),
        ]);
        if (!active) return;
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setLowStock(loadedLowStock);
        setSummary(loadedSummary);
        setBranchSettings(loadedSettings);
        setPhysicalCounts(loadedCounts);
        setTransfers(loadedTransfers);
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
    const [nextStock, nextLowStock, nextMovements, nextSummary, nextCounts, nextTransfers] = await Promise.all([
      searchInventoryStock(activeSession.accessToken, {
        q: debouncedStockQuery, page: stockPage, size: 25, sort: stockSort,
      }),
      getLowInventoryStock(activeSession.accessToken),
      searchInventoryMovements(activeSession.accessToken, {
        productId: selectedProductId || undefined, page: movementPage, size: 25,
      }),
      getInventorySummary(activeSession.accessToken).catch(() => null),
      getInventoryPhysicalCounts(activeSession.accessToken).catch(() => []),
      getInventoryTransfers(activeSession.accessToken).catch(() => []),
    ]);
    setStock(nextStock.items); setStockTotal(nextStock.totalElements); setStockTotalPages(nextStock.totalPages);
    setLowStock(nextLowStock); setMovements(nextMovements.items); setMovementTotalPages(nextMovements.totalPages);
    setSummary(nextSummary); setPhysicalCounts(nextCounts); setTransfers(nextTransfers);
  }

  function selectProduct(product: InventoryStock) {
    setSelectedProductId(product.productId); setReorderPoint(String(product.reorderPoint)); setMovementPage(0);
  }

  function selectDrawerProduct(productId: string) {
    setSelectedProductId(productId); setMovementPage(0);
    const nextStock = stock.find((item) => item.productId === productId);
    setReorderPoint(String(nextStock?.reorderPoint ?? 0));
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
      setReason(""); setUnitCostCents(""); setReorderPoint(String(updated.reorderPoint)); setMovementPage(0);
      setMessage(`${updated.productName}: existencia actual ${updated.quantityOnHand}.`);
      await refresh(session); setShowMovementDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar movimiento.");
    } finally { setSaving(false); }
  }

  async function startPhysicalCount(notes?: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      const created = await createInventoryPhysicalCount(session.accessToken, notes);
      setMessage(`Conteo físico iniciado con estado ${created.status}.`);
      await refresh(session);
      return created;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar el conteo físico.");
    } finally { setSaving(false); }
  }

  async function savePhysicalCountItem(countId: string, productId: string, countedQty: number | null, notes?: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      const updated = await updateInventoryPhysicalCountItem(session.accessToken, countId, productId, {
        countedQuantity: countedQty, notes,
      });
      setMessage("Ítem de conteo actualizado.");
      await refresh(session);
      return updated;
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar ítem de conteo.");
    } finally { setSaving(false); }
  }

  async function approveCount(countId: string) {
    if (!session) return;
    setSaving(true); setError(null); setMessage(null);
    try {
      await approveInventoryPhysicalCount(session.accessToken, countId);
      setMessage("Conteo físico aprobado y ajustes de inventario aplicados.");
      await refresh(session);
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
    if (stock.length === 0) return;
    const content = generateStockCsv(stock);
    const dateStr = new Date().toISOString().split("T")[0];
    downloadCsvFile(content, `inventario_existencias_${dateStr}.csv`);
  }

  function exportMovementsCsv() {
    if (movements.length === 0) return;
    const content = generateMovementsCsv(movements);
    const dateStr = new Date().toISOString().split("T")[0];
    downloadCsvFile(content, `inventario_movimientos_${dateStr}.csv`);
  }

  return {
    activeBranch, approveCount, branchSettings, branches, cancelCount, error,
    exportMovementsCsv, exportStockCsv,
    handleCancelTransfer, handleCreateTransfer, handleReceiveTransfer, handleShipTransfer,
    loading, lowStock, message, movementLoading, movementPage, movementTotalPages,
    movementType, movements, organization, physicalCounts, quantity, reason, refresh, reorderPoint, saving,
    savePhysicalCountItem, selectDrawerProduct, selectProduct, selectedProductId, selectedStock, session,
    setMovementPage, setMovementType, setQuantity, setReason, setReorderPoint,
    setShowMovementDrawer, setShowPhysicalCountDrawer, setShowTransferDrawer, setStockPage, setStockQuery,
    setStockSort, setUnitCostCents, showMovementDrawer, showPhysicalCountDrawer, showTransferDrawer,
    startPhysicalCount, stock, stockLoading, stockPage, stockQuery, stockSort, stockTotal, stockTotalPages,
    submitMovement, summary, toggleAllowNegativeStock, transfers, unitCostCents,
  };
}
