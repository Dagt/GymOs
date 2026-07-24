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

export type InventoryStockPage = {
  items: InventoryStock[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type InventoryStockSearchParams = {
  q?: string;
  page?: number;
  size?: number;
  sort?: "name,asc" | "name,desc" | "quantity,asc" | "quantity,desc" | "updatedAt,desc";
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

export type InventoryMovementPage = {
  items: InventoryMovement[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type InventoryMovementSearchParams = {
  productId?: string;
  page?: number;
  size?: number;
};

export type InventoryMovementPayload = {
  productId: string;
  movementType: Exclude<InventoryMovementType, "SALE">;
  quantity: number;
  unitCostCents?: number | null;
  referenceType?: string | null;
  referenceId?: string | null;
  reason: string;
};

export type InventorySettingsPayload = {
  reorderPoint: number;
};
