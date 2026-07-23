import Link from "next/link";
import { type FormEvent } from "react";
import {
  type ClientSummary,
  type ProductSummary,
  type SaleSummary,
} from "@/lib/api";

export type CartItem = {
  product: ProductSummary;
  quantity: number;
};

export type PaymentMethod = "CASH" | "TRANSFER" | "CARD_MANUAL";

export function ProductGrid({
  loading,
  productQuery,
  products,
  onAddProduct,
  onProductQueryChange,
}: {
  loading: boolean;
  productQuery: string;
  products: ProductSummary[];
  onAddProduct: (product: ProductSummary) => void;
  onProductQueryChange: (value: string) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Productos</h2>
      <label className="mt-4 grid gap-1 text-sm font-medium">
        Buscar o escanear
        <input
          autoComplete="off"
          className="h-10 rounded-md border border-[#cdd5ca] px-3"
          onChange={(event) => onProductQueryChange(event.target.value)}
          placeholder="Nombre, SKU o codigo de barras"
          value={productQuery}
        />
      </label>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {loading ? <p className="text-sm text-[#5d6b63]">Cargando catalogo...</p> : null}
        {products.map((product) => (
          <button
            className="rounded-md border border-[#d8ddd2] bg-white p-4 text-left transition hover:border-[#51735f] hover:bg-[#f6f7f2]"
            key={product.id}
            onClick={() => onAddProduct(product)}
            type="button"
          >
            <span className="block text-sm font-semibold">{product.name}</span>
            <span className="mt-1 block text-xs text-[#6f7a73]">{product.sku}</span>
            <span className="mt-3 block text-lg font-semibold">
              {formatMoney(product.priceCents, product.currency)}
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}

export function RecentSalesList({ sales }: { sales: SaleSummary[] }) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Ventas recientes</h2>
      <div className="mt-4 grid gap-3">
        {sales.map((sale) => (
          <Link
            className="rounded-md border border-[#d8ddd2] px-4 py-3 transition hover:border-[#51735f] hover:bg-[#f6f7f2]"
            href={`/sales/${sale.id}`}
            key={sale.id}
          >
            <span className="block font-semibold">{sale.saleNumber}</span>
            <span className="mt-1 block text-sm text-[#5d6b63]">
              {sale.clientName} - {sale.status} - {formatMoney(sale.totalCents, sale.currency)}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}

export function PosClientSearch({
  clientQuery,
  clients,
  selectedClient,
  onClientQueryChange,
  onSearch,
  onSelectClient,
}: {
  clientQuery: string;
  clients: ClientSummary[];
  selectedClient: ClientSummary | null;
  onClientQueryChange: (value: string) => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
  onSelectClient: (client: ClientSummary) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Cliente</h2>
      <form className="mt-4 flex gap-2" onSubmit={onSearch}>
        <input
          className="h-11 min-w-0 flex-1 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
          onChange={(event) => onClientQueryChange(event.target.value)}
          placeholder="Nombre, telefono o email"
          value={clientQuery}
        />
        <button className="app-button" type="submit">
          Buscar
        </button>
      </form>
      {selectedClient ? (
        <p className="mt-3 rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
          {selectedClient.firstName} {selectedClient.lastName}
        </p>
      ) : null}
      <div className="mt-3 grid gap-2">
        {clients.slice(0, 5).map((client) => (
          <button
            className="rounded-md border border-[#d8ddd2] px-3 py-2 text-left text-sm hover:border-[#51735f]"
            key={client.id}
            onClick={() => onSelectClient(client)}
            type="button"
          >
            {client.firstName} {client.lastName}
          </button>
        ))}
      </div>
    </section>
  );
}

export function PosCartForm({
  cart,
  currency,
  paymentAmount,
  paymentMethod,
  paymentReference,
  saving,
  subtotal,
  validateNow,
  onPaymentAmountChange,
  onPaymentMethodChange,
  onPaymentReferenceChange,
  onQuantityChange,
  onSubmit,
  onValidateNowChange,
}: {
  cart: CartItem[];
  currency: string;
  paymentAmount: string;
  paymentMethod: PaymentMethod;
  paymentReference: string;
  saving: boolean;
  subtotal: number;
  validateNow: boolean;
  onPaymentAmountChange: (value: string) => void;
  onPaymentMethodChange: (value: PaymentMethod) => void;
  onPaymentReferenceChange: (value: string) => void;
  onQuantityChange: (productId: string, quantity: number) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onValidateNowChange: (value: boolean) => void;
}) {
  return (
    <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={onSubmit}>
      <h2 className="text-xl font-semibold">Carrito</h2>
      <div className="mt-4 grid gap-3">
        {cart.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin articulos.</p> : null}
        {cart.map((item) => (
          <div className="grid gap-2 rounded-md border border-[#d8ddd2] p-3" key={item.product.id}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold">{item.product.name}</p>
                <p className="text-sm text-[#5d6b63]">
                  {formatMoney(item.product.priceCents, item.product.currency)}
                </p>
              </div>
              <input
                className="h-10 w-20 rounded-md border border-[#cdd5ca] px-2 text-sm"
                min={0}
                onChange={(event) => onQuantityChange(item.product.id, Number(event.target.value))}
                type="number"
                value={item.quantity}
              />
            </div>
          </div>
        ))}
      </div>

      <div className="mt-5 border-t border-[#e4e8df] pt-5">
        <div className="flex items-center justify-between text-lg font-semibold">
          <span>Total</span>
          <span>{formatMoney(subtotal, currency)}</span>
        </div>
        <div className="mt-4 grid gap-3">
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Metodo
            <select
              className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
              onChange={(event) => onPaymentMethodChange(event.target.value as PaymentMethod)}
              value={paymentMethod}
            >
              <option value="CASH">Efectivo</option>
              <option value="TRANSFER">Transferencia</option>
              <option value="CARD_MANUAL">Tarjeta manual</option>
            </select>
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Monto recibido
            <input
              className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm"
              onChange={(event) => onPaymentAmountChange(event.target.value)}
              placeholder={(subtotal / 100).toFixed(2)}
              type="number"
              value={paymentAmount}
            />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Referencia
            <input
              className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm"
              onChange={(event) => onPaymentReferenceChange(event.target.value)}
              value={paymentReference}
            />
          </label>
          <label className="flex items-center gap-2 text-sm font-medium text-[#42514a]">
            <input
              checked={validateNow}
              onChange={(event) => onValidateNowChange(event.target.checked)}
              type="checkbox"
            />
            Validar pago ahora
          </label>
        </div>
      </div>

      <button className="app-button mt-5 h-11 w-full" disabled={saving || cart.length === 0} type="submit">
        {saving ? "Registrando..." : "Registrar venta"}
      </button>
    </form>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
