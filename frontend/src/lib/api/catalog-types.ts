export type ProductType = "PHYSICAL" | "SERVICE" | "MEMBERSHIP_PLAN";
export type CommercialSubtype =
  | "GENERAL_PRODUCT"
  | "GENERAL_SERVICE"
  | "VISIT_PASS"
  | "BOOKING"
  | "ROUTINE"
  | "CONSULTATION"
  | "WELLNESS_SERVICE"
  | "MEMBERSHIP";

export type ProductSummary = {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  productType: ProductType;
  commercialSubtype: CommercialSubtype;
  priceCents: number;
  currency: string;
  taxable: boolean;
  status: "ACTIVE" | "INACTIVE";
  barcode: string | null;
  brand: string | null;
  presentation: string | null;
  unitOfMeasure: string | null;
  inventoryTracked: boolean;
  displayOrder: number;
  imageObjectKey: string | null;
  supplierName: string | null;
  referenceCostCents: number | null;
  promotionalPriceCents: number | null;
  promotionStartsAt: string | null;
  promotionEndsAt: string | null;
  categoryId: string | null;
  categoryName: string | null;
  tags: string[];
  archived: boolean;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type CatalogSummary = {
  activeProducts: number;
  activeServices: number;
  activePlans: number;
  inactiveProducts: number;
  archivedProducts: number;
  inventoryTrackedProducts: number;
  uncategorizedProducts: number;
  productsWithoutBarcode: number;
  productsWithoutImage: number;
  lowStockProducts: number;
};

export type ProductDependencies = {
  saleItems: number;
  inventoryMovements: number;
  branchesWithStock: number;
  activeMembershipPlans: number;
  activeProductsInCategory: number;
};

export type ProductHistoryEntry = {
  id: string;
  action: string;
  actorUserId: string | null;
  actorRole: string;
  reason: string | null;
  origin: string;
  correlationId: string;
  occurredAt: string;
};

export type ProductDetail = {
  product: ProductSummary;
  dependencies: ProductDependencies;
  history: ProductHistoryEntry[];
};

export type PlanSummary = {
  id: string;
  productId: string;
  sku: string;
  name: string;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
  benefitsJson: string | null;
  multiBranchAccess: boolean;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  updatedAt: string;
};

export type ProductPayload = {
  sku: string;
  name: string;
  description?: string | null;
  productType: ProductType;
  commercialSubtype: CommercialSubtype;
  priceCents: number;
  currency: string;
  taxable: boolean;
  status: "ACTIVE" | "INACTIVE";
  barcode?: string | null;
  brand?: string | null;
  presentation?: string | null;
  unitOfMeasure?: string | null;
  inventoryTracked: boolean;
  displayOrder: number;
  supplierName?: string | null;
  referenceCostCents?: number | null;
  promotionalPriceCents?: number | null;
  promotionStartsAt?: string | null;
  promotionEndsAt?: string | null;
  categoryId?: string | null;
  tags: string[];
  version?: number | null;
};

export type ProductPage = {
  items: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ProductSearchParams = {
  q?: string;
  type?: ProductSummary["productType"] | "";
  categoryId?: string;
  status?: ProductSummary["status"] | "";
  inventoryTracked?: boolean;
  includeArchived?: boolean;
  page?: number;
  size?: number;
  sort?: string;
};

export type CatalogCategory = {
  id: string;
  parentId: string | null;
  name: string;
  slug: string;
  description: string | null;
  status: "ACTIVE" | "INACTIVE";
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
};

export type CatalogCategoryPayload = {
  parentId?: string | null;
  name: string;
  description?: string | null;
  status: CatalogCategory["status"];
  displayOrder: number;
};

export type PlanPayload = {
  productId: string;
  durationDays: number;
  graceDays: number;
  benefitsJson?: string | null;
  multiBranchAccess?: boolean;
  status: "ACTIVE" | "INACTIVE";
};

export type MembershipPlanPayload = {
  sku: string;
  name: string;
  description?: string | null;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
  benefits?: string | null;
  multiBranchAccess?: boolean;
  status: "ACTIVE" | "INACTIVE";
};

export type UpdateMembershipPlanPayload = Omit<MembershipPlanPayload, "sku">;
