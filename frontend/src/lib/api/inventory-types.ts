export type InventoryStock = {
  stockId: string | null;
  productId: string;
  sku: string;
  productName: string;
  priceCents: number;
  currency: string;
  quantityOnHand: number;
  reorderPoint: number;
  averageCostCents: number | null;
  updatedAt: string | null;
  lowStock: boolean;
};

export type InventoryMovementType =
  | "INITIAL"
  | "PURCHASE"
  | "ADJUSTMENT_IN"
  | "ADJUSTMENT_OUT"
  | "WASTE"
  | "RETURN"
  | "SALE";

export type InventoryMovement = {
  id: string;
  productId: string;
  sku: string;
  productName: string;
  movementType: InventoryMovementType;
  quantityDelta: number;
  quantityBefore: number;
  quantityAfter: number;
  unitCostCents: number | null;
  referenceType: string | null;
  referenceId: string | null;
  reason: string;
  occurredAt: string;
};

export type InventoryMovementPayload = {
  productId: string;
  movementType: InventoryMovementType;
  quantity: number;
  unitCostCents?: number | null;
  reorderPoint?: number | null;
  referenceType?: string | null;
  referenceId?: string | null;
  reason: string;
};
