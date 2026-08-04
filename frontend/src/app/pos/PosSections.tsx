import Link from "next/link";
import { type FormEvent } from "react";
import {
  type ClientSummary,
  type ProductSummary,
  type SaleSummary,
} from "@/lib/api";
import { formatMoney } from "./pos-model";

export type CartItem = {
  product: ProductSummary;
  quantity: number;
};

export type PaymentMethod = "CASH" | "TRANSFER" | "CARD_MANUAL" | "CARD_MERCADO_PAGO";

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
    <section className="rounded-3xl border border-gray-100 bg-white p-8 shadow-sm overflow-hidden relative">
      {/* Subtle Texture */}
      <div className="absolute top-0 right-0 w-64 h-64 opacity-[0.04] pointer-events-none" style={{ backgroundImage: `radial-gradient(var(--brand-primary) 2px, transparent 2px)`, backgroundSize: '24px 24px' }}></div>
      
      <div className="relative z-10">
        <h2 className="text-2xl font-black text-[color:var(--brand-primary)] flex items-center gap-3">
          <span className="w-3 h-8 bg-[color:var(--brand-accent)] rounded-full shadow-sm shadow-[color:var(--brand-accent)]"></span>
          Catálogo de Productos
        </h2>
        
        <div className="mt-6">
          <label className="block text-xs font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 mb-2 ml-1">Buscar o escanear</label>
          <div className="relative group">
            <input
              autoComplete="off"
              className="h-14 w-full rounded-2xl border-2 border-gray-100 bg-gray-50 px-5 text-base font-bold focus:border-[color:var(--brand-primary)] focus:bg-white focus:ring-4 focus:ring-[color:var(--brand-primary)]/5 transition-all outline-none shadow-inner"
              onChange={(event) => onProductQueryChange(event.target.value)}
              placeholder="Nombre del producto, SKU o código de barras..."
              value={productQuery}
            />
          </div>
        </div>

        <div className="mt-6 overflow-y-auto pr-2 max-h-[520px] custom-scrollbar">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {loading && (
              <div className="col-span-full py-12 flex flex-col items-center gap-4">
                 <div className="w-10 h-10 border-4 border-[color:var(--brand-primary)]/20 border-t-[color:var(--brand-primary)] rounded-full animate-spin"></div>
                 <p className="text-sm font-black text-[color:var(--brand-primary)] opacity-40 uppercase tracking-widest">Sincronizando catálogo...</p>
              </div>
            )}
            {!loading && products.length === 0 && (
              <div className="col-span-full py-16 text-center bg-gray-50 rounded-2xl border-2 border-dashed border-gray-200">
                 <p className="text-gray-400 font-bold italic">No se encontraron coincidencias en el inventario.</p>
              </div>
            )}
            {products.map((product) => (
              <button
                className="group relative flex flex-col p-3.5 rounded-xl border border-gray-200 bg-white text-left transition-all duration-200 hover:bg-[color:var(--brand-surface)] hover:border-[color:var(--brand-primary)] hover:shadow-md hover:-translate-y-0.5"
                key={product.id}
                onClick={() => onAddProduct(product)}
                type="button"
              >
                <div className="flex justify-between items-start mb-2 gap-2">
                  <span className="block text-sm font-bold text-gray-900 leading-tight group-hover:text-[color:var(--brand-primary)] transition-colors line-clamp-2">{product.name}</span>
                  <span className="shrink-0 text-[0.6rem] font-bold uppercase tracking-wider px-1.5 py-0.5 bg-gray-100 text-gray-400 border border-gray-100 rounded-md">{product.sku}</span>
                </div>
                
                <div className="mt-auto flex items-center justify-between pt-2">
                  <div className="flex flex-col">
                    <span className="text-[0.6rem] font-bold uppercase tracking-wider text-gray-400">Precio</span>
                    <span className="text-base font-black text-[color:var(--brand-primary)]">
                      {formatMoney(product.priceCents, product.currency)}
                    </span>
                  </div>
                  <div className="w-8 h-8 rounded-lg bg-white border border-gray-200 shadow-xs flex items-center justify-center text-[color:var(--brand-primary)] text-lg font-bold group-hover:bg-[color:var(--brand-primary)] group-hover:text-white group-hover:border-[color:var(--brand-primary)] transition-all duration-200">
                    +
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export function RecentSalesList({ sales }: { sales: SaleSummary[] }) {
  return (
    <section className="rounded-3xl border border-gray-100 bg-white p-8 shadow-sm overflow-hidden relative">
      <h2 className="text-2xl font-black text-[color:var(--brand-primary)] flex items-center gap-3">
        <span className="w-3 h-8 bg-[color:var(--brand-primary)] opacity-20 rounded-full"></span>
        Actividad de Caja
      </h2>
      <div className="mt-6 grid gap-4">
        {sales.length === 0 && (
           <p className="text-sm text-gray-400 font-bold uppercase tracking-widest py-4 italic">Esperando primera venta del día...</p>
        )}
        {sales.slice(0, 5).map((sale) => (
          <Link
            className="group flex items-center justify-between p-5 rounded-2xl border-2 border-gray-50 bg-gray-50/50 hover:bg-white hover:border-[color:var(--brand-primary)] hover:shadow-xl transition-all duration-300"
            href={`/sales/${sale.id}`}
            key={sale.id}
          >
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-white flex items-center justify-center font-black text-[color:var(--brand-primary)] border border-gray-100 group-hover:scale-110 transition-transform">
                #{sale.saleNumber.slice(-3)}
              </div>
              <div className="flex flex-col">
                <span className="font-black text-gray-900 group-hover:text-[color:var(--brand-primary)] transition-colors leading-none mb-1">{sale.saleNumber}</span>
                <span className="text-[0.65rem] font-black text-gray-400 uppercase tracking-widest flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500"></span>
                  {sale.clientName || "Venta General"} • {businessContextLabel(sale.businessContext)} • {sale.status}
                </span>
              </div>
            </div>
            <div className="text-right">
               <span className="text-lg font-black text-gray-900 block leading-none">{formatMoney(sale.totalCents, sale.currency)}</span>
               <span className="text-[0.6rem] font-black text-[color:var(--brand-primary)] uppercase opacity-60">Ver Detalle →</span>
            </div>
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
  onSelectClient: (client: ClientSummary | null) => void;
}) {
  return (
    <section className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm relative overflow-hidden">
      <h2 className="text-lg font-black text-[color:var(--brand-primary)] flex items-center gap-2">
        <span className="w-2 h-5 bg-[color:var(--brand-accent)] rounded-full"></span>
        Socio / Cliente
      </h2>
      
      <form className="mt-4 flex gap-2" onSubmit={onSearch}>
        <input
          className="h-10 flex-1 rounded-xl border-2 border-gray-100 bg-gray-50 px-4 text-sm font-bold focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none shadow-inner"
          onChange={(event) => onClientQueryChange(event.target.value)}
          placeholder="Nombre, teléfono o email..."
          value={clientQuery}
        />
        <button 
          className="app-button-secondary px-4 text-xs font-black shadow-sm" 
          type="submit"
        >
          Buscar
        </button>
      </form>

      {clients.length > 0 && !selectedClient ? (
        <div className="mt-3 grid gap-2 max-h-40 overflow-y-auto p-2 bg-gray-50 rounded-xl border border-gray-100">
          {clients.map((client) => (
            <button
              key={client.id}
              type="button"
              onClick={() => onSelectClient(client)}
              className="p-2.5 rounded-lg bg-white border border-gray-100 hover:border-[color:var(--brand-primary)] text-left flex items-center justify-between transition-colors group"
            >
              <div className="flex flex-col">
                <strong className="text-sm font-bold text-gray-900 group-hover:text-[color:var(--brand-primary)]">
                  {client.firstName} {client.lastName}
                </strong>
                <span className="text-xs text-gray-500">{client.phone || client.email || "Socio sin teléfono"}</span>
              </div>
              <span className="text-xs font-bold text-[color:var(--brand-primary)] group-hover:text-white transition-all">✔</span>
            </button>
          ))}
        </div>
      ) : null}

      {selectedClient && (
        <div className="mt-4 p-4 rounded-xl border-2 border-[color:color-mix(in_srgb,var(--brand-primary)_20%,transparent)] bg-[color:var(--brand-primary)] text-white flex items-center justify-between shadow-lg animate-in zoom-in-95">
          <div className="flex flex-col">
            <span className="text-[0.6rem] font-black uppercase tracking-widest opacity-70 mb-0.5">Socio Identificado</span>
            <strong className="text-base font-black">
              {selectedClient.firstName} {selectedClient.lastName}
            </strong>
          </div>
          <button 
            type="button" 
            onClick={() => onSelectClient(null)}
            className="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 flex items-center justify-center font-black text-white transition-colors"
          >
            ✕
          </button>
        </div>
      )}
    </section>
  );
}

export function PosCartForm({
  cart,
  currency,
  highlightedProductId,
    manualCardEnabled,
    mercadoPagoEnabled,
  paymentAmount,
  paymentMethod,
  paymentReference,
  saving,
  scanError,
  subtotal,
  validateNow,
  onClearCart,
  onPaymentAmountChange,
  onPaymentMethodChange,
  onPaymentReferenceChange,
  onQuantityChange,
  onSubmit,
  onValidateNowChange,
}: {
  cart: CartItem[];
  currency: string;
  highlightedProductId?: string | null;
    manualCardEnabled: boolean;
    mercadoPagoEnabled: boolean;
  paymentAmount: string;
  paymentMethod: PaymentMethod;
  paymentReference: string;
  saving: boolean;
  scanError?: string | null;
  subtotal: number;
  validateNow: boolean;
  onClearCart?: () => void;
  onPaymentAmountChange: (value: string) => void;
  onPaymentMethodChange: (value: PaymentMethod) => void;
  onPaymentReferenceChange: (value: string) => void;
  onQuantityChange: (productId: string, quantity: number) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onValidateNowChange: (value: boolean) => void;
}) {
  return (
    <form className="rounded-2xl border border-gray-100 bg-white p-5 shadow-2xl overflow-hidden relative" onSubmit={onSubmit}>
      <div className="absolute -top-12 -right-12 w-32 h-32 bg-[color:var(--brand-primary)] opacity-[0.02] rounded-full blur-2xl pointer-events-none"></div>
      
      {scanError ? (
        <div className="mb-4 p-3 rounded-xl border-2 border-red-200 bg-red-50 text-red-700 text-xs font-black flex items-center justify-between shadow-sm animate-in fade-in slide-in-from-top-2">
          <span>⚠️ {scanError}</span>
        </div>
      ) : null}

      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-black text-[color:var(--brand-primary)] flex items-center gap-2">
          <span className="w-2 h-5 bg-[color:var(--brand-primary)] rounded-full opacity-30"></span>
          Carrito {cart.length > 0 ? `(${cart.length})` : ""}
        </h2>
        {cart.length > 0 && onClearCart ? (
          <button
            className="text-[0.65rem] font-black uppercase tracking-wider text-red-600 hover:text-red-800 bg-red-50 hover:bg-red-100 px-2.5 py-1 rounded-lg border border-red-200 transition-all flex items-center gap-1 shadow-xs"
            onClick={onClearCart}
            type="button"
          >
            🗑 Limpiar
          </button>
        ) : null}
      </div>

      <div className="grid gap-2 max-h-[240px] overflow-y-auto pr-1 custom-scrollbar">
        {cart.length === 0 && (
          <div className="py-8 flex flex-col items-center justify-center text-center bg-gray-50/30 rounded-xl border-2 border-dashed border-gray-100">
            <p className="text-[0.65rem] font-black text-gray-400 uppercase tracking-widest">Añade productos</p>
          </div>
        )}
        {cart.map((item) => {
          const isHighlighted = item.product.id === highlightedProductId;
          return (
            <div
              className={`flex flex-col p-3 rounded-xl border-2 transition-all duration-300 ${
                isHighlighted
                  ? "bg-[color:var(--brand-surface)] border-[color:var(--brand-primary)] ring-2 ring-[color:var(--brand-primary)]/30 shadow-md scale-[1.02]"
                  : "border-gray-50 bg-white hover:border-[color:var(--brand-primary)]/10"
              }`}
              key={item.product.id}
            >
              <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <p className="font-black text-gray-900 leading-tight text-sm">{item.product.name}</p>
                <p className="text-xs font-black text-[color:var(--brand-primary)] mt-0.5">
                  {formatMoney(item.product.priceCents, item.product.currency)}
                </p>
              </div>
              <div className="flex items-center bg-gray-50 rounded-lg p-1 border border-gray-100">
                <button 
                  type="button" 
                  onClick={() => onQuantityChange(item.product.id, item.quantity - 1)}
                  className="w-7 h-7 flex items-center justify-center font-black text-gray-400 hover:text-[color:var(--brand-primary)] bg-white rounded shadow-sm transition-colors"
                >
                  -
                </button>
                <input
                  className="w-8 bg-transparent text-center text-xs font-black text-gray-900 border-none outline-none focus:ring-0"
                  min={0}
                  onChange={(event) => onQuantityChange(item.product.id, Number(event.target.value))}
                  type="number"
                  value={item.quantity}
                />
                <button 
                  type="button" 
                  onClick={() => onQuantityChange(item.product.id, item.quantity + 1)}
                  className="w-7 h-7 flex items-center justify-center font-black text-gray-400 hover:text-[color:var(--brand-primary)] bg-white rounded shadow-sm transition-colors"
                >
                  +
                </button>
              </div>
            </div>
          </div>
          );
        })}
      </div>

      <div className="mt-6 pt-4 border-t-2 border-dashed border-gray-100">
        <div className="flex flex-col items-center gap-1 mb-6">
          <span className="text-[0.6rem] font-black uppercase tracking-wider text-gray-400">Total Neto</span>
          <span className="text-3xl font-black text-[color:var(--brand-primary)] tracking-tighter">{formatMoney(subtotal, currency)}</span>
        </div>

        <div className="grid gap-4">
          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <label className="text-[0.6rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 ml-1">Método</label>
              <select
                className="h-10 rounded-xl border-2 border-gray-100 bg-gray-50 px-3 text-xs font-black text-gray-900 focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none"
                onChange={(event) => onPaymentMethodChange(event.target.value as PaymentMethod)}
                value={paymentMethod}
              >
                <option value="CASH">Efectivo</option>
                <option value="TRANSFER">Transf.</option>
                  {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta</option> : null}
                  {mercadoPagoEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
              </select>
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-[0.6rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 ml-1">Recibido</label>
              <input
                className="h-10 rounded-xl border-2 border-gray-100 bg-gray-50 px-3 text-xs font-black text-gray-900 focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none"
                onChange={(event) => onPaymentAmountChange(event.target.value)}
                placeholder={(subtotal / 100).toFixed(2)}
                type="number"
                value={paymentAmount}
              />
            </div>
          </div>
          
          <div className="flex flex-col gap-1.5">
            <label className="text-[0.6rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60 ml-1">Referencia</label>
            <input
              className="h-10 rounded-xl border-2 border-gray-100 bg-gray-50 px-3 text-xs font-bold text-gray-900 focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none"
              onChange={(event) => onPaymentReferenceChange(event.target.value)}
              placeholder="Folio o Ticket"
              value={paymentReference}
            />
          </div>

          <label className="flex items-center gap-3 p-3 rounded-xl bg-gray-50 border-2 border-transparent hover:border-gray-100 transition-all cursor-pointer group">
              <input
                checked={validateNow}
              className="w-4 h-4 rounded border-2 border-gray-300 text-[color:var(--brand-primary)] focus:ring-[color:var(--brand-primary)]/20 cursor-pointer"
                disabled={paymentMethod === "CARD_MERCADO_PAGO"}
                onChange={(event) => onValidateNowChange(event.target.checked)}
              type="checkbox"
            />
              <span className="text-[0.7rem] font-black text-gray-600 group-hover:text-[color:var(--brand-primary)] transition-colors uppercase tracking-tighter">
                {paymentMethod === "CARD_MERCADO_PAGO" ? "Se confirmara automaticamente por webhook" : "Validar pago ahora"}
              </span>
          </label>
        </div>
      </div>

      <button 
        className={`mt-6 h-14 w-full rounded-2xl font-black text-base shadow-md transition-all duration-300 flex items-center justify-center gap-3 ${
          saving || cart.length === 0 
            ? "bg-gray-100 text-gray-400 cursor-not-allowed shadow-none" 
            : "bg-[color:var(--brand-primary)] text-white hover:opacity-95 active:scale-95 shadow-[color:var(--brand-primary)]/20"
        }`}
        disabled={saving || cart.length === 0} 
        type="submit"
      >
        {saving ? (
          <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
        ) : (
          <>
            <span>REGISTRAR VENTA</span>
            <span className="text-lg group-hover:translate-x-1 transition-transform">➜</span>
          </>
        )}
      </button>
    </form>
  );
}

export function businessContextLabel(context: string) {
  const labels: Record<string, string> = {
    RETAIL_SALE: "Venta de productos",
    MEMBERSHIP_ENROLLMENT: "Contratación",
    MEMBERSHIP_RENEWAL: "Renovación",
    MEMBERSHIP_PLAN_CHANGE: "Cambio de plan",
  };
  return labels[context] ?? context;
}
