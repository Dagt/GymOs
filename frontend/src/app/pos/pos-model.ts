import type { CartItem } from "./PosSections";

export function calculateSubtotal(cart: CartItem[]): number {
  return cart.reduce((sum, item) => sum + item.product.priceCents * item.quantity, 0);
}

export function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
