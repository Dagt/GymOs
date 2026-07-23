import type { ProductSummary } from "@/lib/api";

export function isMembershipPlanProduct(product: ProductSummary) {
  return product.productType === "MEMBERSHIP_PLAN";
}

export function sellableCatalogProducts(products: ProductSummary[]) {
  return products.filter((product) => !isMembershipPlanProduct(product));
}
