import type { InventoryMovement, InventoryMovementType, InventoryStock } from "@/lib/api";

export type ManualMovementType = Exclude<InventoryMovementType, "SALE">;

export const MANUAL_MOVEMENT_TYPES: Array<{ value: ManualMovementType; label: string }> = [
  { value: "PURCHASE", label: "Compra" },
  { value: "ADJUSTMENT_IN", label: "Ajuste entrada" },
  { value: "ADJUSTMENT_OUT", label: "Ajuste salida" },
  { value: "WASTE", label: "Merma" },
  { value: "INTERNAL_CONSUMPTION", label: "Consumo interno" },
  { value: "RETURN", label: "Devolución" },
  { value: "INITIAL", label: "Inicial" },
];

export function calculateValuation(quantityOnHand: number, unitCostCents: number | null): number {
  if (quantityOnHand <= 0 || !unitCostCents || unitCostCents < 0) return 0;
  return quantityOnHand * unitCostCents;
}

export function generateStockCsv(stock: InventoryStock[]): string {
  const headers = ["Producto", "SKU", "Existencia", "Mínimo", "Reabastecimiento Sugerido", "Costo Promedio (MXN)", "Valuación Estimada (MXN)"];
  const rows = stock.map((item) => {
    const avgCost = item.averageCostCents ? (item.averageCostCents / 100).toFixed(2) : "0.00";
    const val = item.averageCostCents && item.quantityOnHand > 0 ? ((item.quantityOnHand * item.averageCostCents) / 100).toFixed(2) : "0.00";
    return [
      `"${item.productName.replace(/"/g, '""')}"`,
      `"${item.sku.replace(/"/g, '""')}"`,
      item.quantityOnHand,
      item.reorderPoint,
      item.suggestedReorderQuantity ?? 0,
      avgCost,
      val,
    ].join(",");
  });

  return [headers.join(","), ...rows].join("\n");
}

export function generateMovementsCsv(movements: InventoryMovement[]): string {
  const headers = ["Fecha", "Producto", "SKU", "Tipo Movimiento", "Cantidad", "Existencia Antes", "Existencia Después", "Motivo"];
  const rows = movements.map((m) => [
    `"${m.occurredAt}"`,
    `"${m.productName.replace(/"/g, '""')}"`,
    `"${m.sku.replace(/"/g, '""')}"`,
    m.movementType,
    m.quantityDelta,
    m.quantityBefore,
    m.quantityAfter,
    `"${m.reason.replace(/"/g, '""')}"`,
  ].join(","));

  return [headers.join(","), ...rows].join("\n");
}

export function downloadCsvFile(csvContent: string, filename: string) {
  const blob = new Blob(["\uFEFF" + csvContent], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
