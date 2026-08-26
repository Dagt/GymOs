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
  suggestedReorderQuantity: number;
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
  lowStockOnly?: boolean;
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
  | "INTERNAL_CONSUMPTION"
  | "RETURN"
  | "TRANSFER_OUT"
  | "TRANSFER_IN"
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
  q?: string;
  type?: InventoryMovementType | "";
  page?: number;
  size?: number;
};

export type InventoryHistorySearchParams = {
  page?: number;
  size?: number;
  activeOnly?: boolean;
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

export type InventorySummary = {
  totalValuationCents: number;
  totalTrackedProducts: number;
  lowStockCount: number;
  outOfStockCount: number;
  allowNegativeStock: boolean;
};

export type InventoryBranchSettings = {
  branchId: string;
  allowNegativeStock: boolean;
};

export type InventoryPhysicalCountItem = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  systemQuantity: number;
  countedQuantity: number | null;
  varianceQuantity: number | null;
  unitCostCents: number | null;
  notes: string | null;
};

export type InventoryPhysicalCount = {
  id: string;
  organizationId: string;
  branchId: string;
  status: "IN_PROGRESS" | "PENDING_APPROVAL" | "APPROVED" | "CANCELLED";
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  approvedAt: string | null;
  approvedBy: string | null;
  items: InventoryPhysicalCountItem[];
};

export type InventoryTransferItem = {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  requestedQuantity: number;
  shippedQuantity: number | null;
  receivedQuantity: number | null;
  unitCostCents: number | null;
};

export type InventoryTransfer = {
  id: string;
  organizationId: string;
  originBranchId: string;
  originBranchName: string;
  destinationBranchId: string;
  destinationBranchName: string;
  status: "REQUESTED" | "SHIPPED" | "RECEIVED" | "CANCELLED";
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: string | null;
  shippedAt: string | null;
  shippedBy: string | null;
  receivedAt: string | null;
  receivedBy: string | null;
  items: InventoryTransferItem[];
};
