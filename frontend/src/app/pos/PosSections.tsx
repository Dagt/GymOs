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

        <div className="mt-8 grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
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
              className="group relative flex flex-col p-6 rounded-2xl border-2 border-transparent bg-gray-50 text-left transition-all duration-300 hover:bg-white hover:border-[color:var(--brand-primary)] hover:shadow-2xl hover:-translate-y-2"
              key={product.id}
              onClick={() => onAddProduct(product)}
              type="button"
            >
              <div className="flex justify-between items-start mb-4">
                <span className="block text-base font-black text-gray-900 leading-tight group-hover:text-[color:var(--brand-primary)] transition-colors">{product.name}</span>
                <span className="text-[0.6rem] font-black uppercase tracking-widest px-2 py-1 bg-white text-gray-400 border border-gray-100 rounded-lg group-hover:border-[color:var(--brand-primary)]/20 transition-colors">{product.sku}</span>
              </div>
              
              <div className="mt-auto flex items-center justify-between">
                <div className="flex flex-col">
                  <span className="text-[0.6rem] font-black uppercase tracking-widest text-gray-400">Precio</span>
                  <span className="text-xl font-black text-[color:var(--brand-primary)]">
                    {formatMoney(product.priceCents, product.currency)}
                  </span>
                </div>
                <div className="w-12 h-12 rounded-xl bg-white border border-gray-200 shadow-sm flex items-center justify-center text-[color:var(--brand-primary)] text-2xl font-black group-hover:bg-[color:var(--brand-primary)] group-hover:text-white group-hover:border-[color:var(--brand-primary)] transition-all duration-300">
                  +
                </div>
              </div>
            </button>
          ))}
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
                  {sale.clientName || 'Venta General'} • {sale.status}
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
  onSelectClient: (client: ClientSummary) => void;
}) {
  return (
    <section className="rounded-3xl border border-gray-100 bg-white p-8 shadow-sm relative overflow-hidden">
      <h2 className="text-2xl font-black text-[color:var(--brand-primary)] flex items-center gap-3">
        <span className="w-3 h-8 bg-[color:var(--brand-accent)] rounded-full shadow-sm shadow-[color:var(--brand-accent)]"></span>
        Socio / Cliente
      </h2>
      
      <form className="mt-6 flex gap-2" onSubmit={onSearch}>
        <input
          className="h-14 flex-1 rounded-2xl border-2 border-gray-100 bg-gray-50 px-5 text-base font-bold focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none shadow-inner"
          onChange={(event) => onClientQueryChange(event.target.value)}
          placeholder="Nombre, teléfono o email..."
          value={clientQuery}
        />
        <button 
          className="px-6 rounded-2xl bg-[color:var(--brand-primary)] text-white font-black text-sm hover:brightness-110 active:scale-95 transition-all shadow-lg hover:shadow-[color:var(--brand-primary)]/20" 
          type="submit"
        >
          Buscar
        </button>
      </form>

      {selectedClient && (
        <div className="mt-6 p-5 rounded-2xl border-2 border-[color:color-mix(in_srgb,var(--brand-primary)_20%,transparent)] bg-[color:var(--brand-primary)] text-white flex items-center justify-between shadow-xl animate-in zoom-in-95">
          <div className="flex flex-col">
            <span className="text-[0.65rem] font-black uppercase tracking-widest opacity-70 mb-1">Socio Identificado</span>
            <strong className="text-xl font-black">
              {selectedClient.firstName} {selectedClient.lastName}
            </strong>
          </div>
          <button 
            type="button" 
            onClick={() => onSelectClient(null as any)}
            className="w-10 h-10 rounded-xl bg-white/10 hover:bg-white/20 flex items-center justify-center font-black text-white transition-colors"
          >
            ✕
          </button>
        </div>
      )}

      {clients.length > 0 && !selectedClient && (
        <div className="mt-5 grid gap-3">
          <p className="text-[0.65rem] font-black text-gray-400 uppercase tracking-widest ml-1">Resultados de búsqueda</p>
          {clients.slice(0, 5).map((client) => (
            <button
              className="flex items-center justify-between p-4 rounded-2xl border-2 border-gray-50 bg-gray-50 hover:bg-white hover:border-[color:var(--brand-primary)] hover:shadow-md transition-all text-left group"
              key={client.id}
              onClick={() => onSelectClient(client)}
              type="button"
            >
              <div className="flex flex-col">
                 <span className="text-base font-black text-gray-900 group-hover:text-[color:var(--brand-primary)] transition-colors">{client.firstName} {client.lastName}</span>
                 <span className="text-[0.65rem] font-bold text-gray-400">{client.email || 'Sin correo registrado'}</span>
              </div>
              <span className="w-8 h-8 rounded-lg bg-white border border-gray-100 flex items-center justify-center text-[color:var(--brand-primary)] font-black group-hover:bg-[color:var(--brand-primary)] group-hover:text-white transition-all opacity-0 group-hover:opacity-100 scale-90 group-hover:scale-100">✔</span>
            </button>
          ))}
        </div>
      )}
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
    <form className="rounded-3xl border border-gray-100 bg-white p-8 shadow-2xl overflow-hidden relative" onSubmit={onSubmit}>
      {/* Dynamic Background Blob */}
      <div className="absolute -top-12 -right-12 w-48 h-48 bg-[color:var(--brand-primary)] opacity-[0.03] rounded-full blur-3xl pointer-events-none"></div>
      
      <h2 className="text-2xl font-black text-[color:var(--brand-primary)] flex items-center gap-3 mb-8">
        <span className="w-3 h-8 bg-[color:var(--brand-primary)] rounded-full shadow-sm shadow-[color:var(--brand-primary)]/20"></span>
        Carrito de Venta
      </h2>

      <div className="grid gap-4 max-h-[350px] overflow-y-auto pr-2 custom-scrollbar">
        {cart.length === 0 && (
          <div className="py-16 flex flex-col items-center justify-center text-center bg-gray-50/50 rounded-2xl border-2 border-dashed border-gray-100">
            <div className="w-20 h-20 bg-white rounded-2xl shadow-sm border border-gray-100 flex items-center justify-center mb-5 text-4xl animate-bounce">🛒</div>
            <p className="text-xs font-black text-gray-400 uppercase tracking-widest">Añade productos para comenzar</p>
          </div>
        )}
        {cart.map((item) => (
          <div className="group flex flex-col p-5 rounded-2xl border-2 border-gray-50 bg-white hover:border-[color:var(--brand-primary)]/20 hover:shadow-lg transition-all duration-300" key={item.product.id}>
            <div className="flex items-start justify-between gap-4">
              <div className="flex-1">
                <p className="font-black text-gray-900 leading-tight mb-1 text-base">{item.product.name}</p>
                <p className="text-sm font-black text-[color:var(--brand-primary)]">
                  {formatMoney(item.product.priceCents, item.product.currency)}
                </p>
              </div>
              <div className="flex items-center bg-[color:var(--brand-surface)] rounded-xl p-1.5 border border-gray-100 shadow-inner">
                <button 
                  type="button" 
                  onClick={() => onQuantityChange(item.product.id, item.quantity - 1)}
                  className="w-10 h-10 flex items-center justify-center font-black text-gray-500 hover:text-[color:var(--brand-primary)] transition-colors text-xl bg-white rounded-lg shadow-sm"
                >
                  -
                </button>
                <input
                  className="w-12 bg-transparent text-center text-base font-black text-gray-900 border-none outline-none focus:ring-0"
                  min={0}
                  onChange={(event) => onQuantityChange(item.product.id, Number(event.target.value))}
                  type="number"
                  value={item.quantity}
                />
                <button 
                  type="button" 
                  onClick={() => onQuantityChange(item.product.id, item.quantity + 1)}
                  className="w-10 h-10 flex items-center justify-center font-black text-gray-500 hover:text-[color:var(--brand-primary)] transition-colors text-xl bg-white rounded-lg shadow-sm"
                >
                  +
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-10 pt-8 border-t-4 border-double border-gray-100">
        <div className="flex flex-col items-center gap-2 mb-10">
          <span className="text-xs font-black uppercase tracking-[0.2em] text-gray-400">Total Neto a Pagar</span>
          <span className="text-5xl font-black text-[color:var(--brand-primary)] drop-shadow-sm tracking-tight">{formatMoney(subtotal, currency)}</span>
        </div>

        <div className="grid gap-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] ml-1">Método de Pago</label>
              <select
                className="h-14 rounded-2xl border-2 border-gray-100 bg-gray-50 px-5 text-base font-black text-gray-900 focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none appearance-none cursor-pointer shadow-sm"
                onChange={(event) => onPaymentMethodChange(event.target.value as PaymentMethod)}
                value={paymentMethod}
              >
                <option value="CASH">Efectivo</option>
                <option value="TRANSFER">Transferencia</option>
                <option value="CARD_MANUAL">Tarjeta Manual</option>
              </select>
            </div>
            <div className="flex flex-col gap-2">
              <label className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] ml-1">Monto Recibido</label>
              <input
                className="h-14 rounded-2xl border-2 border-gray-100 bg-gray-50 px-5 text-base font-black text-gray-900 focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none shadow-sm"
                onChange={(event) => onPaymentAmountChange(event.target.value)}
                placeholder={(subtotal / 100).toFixed(2)}
                type="number"
                value={paymentAmount}
              />
            </div>
          </div>
          
          <div className="flex flex-col gap-2">
            <label className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] ml-1">Referencia / Comprobante</label>
            <input
              className="h-14 rounded-2xl border-2 border-gray-100 bg-gray-50 px-5 text-base font-bold text-gray-900 focus:border-[color:var(--brand-primary)] focus:bg-white transition-all outline-none shadow-sm"
              onChange={(event) => onPaymentReferenceChange(event.target.value)}
              placeholder="Ej. Ticket Terminal #12, Últimos 4 dígitos..."
              value={paymentReference}
            />
          </div>

          <label className="flex items-center gap-4 p-5 rounded-2xl bg-[color:var(--brand-surface)] border-2 border-transparent hover:border-[color:var(--brand-primary)]/20 transition-all cursor-pointer group">
            <input
              checked={validateNow}
              className="w-6 h-6 rounded-lg border-2 border-gray-300 text-[color:var(--brand-primary)] focus:ring-[color:var(--brand-primary)]/20 cursor-pointer"
              onChange={(event) => onValidateNowChange(event.target.checked)}
              type="checkbox"
            />
            <div className="flex flex-col">
               <span className="text-sm font-black text-gray-700 group-hover:text-[color:var(--brand-primary)] transition-colors">Validar pago ahora</span>
               <span className="text-[0.6rem] font-bold text-gray-400 uppercase tracking-tighter">Marca esta casilla para procesar la transacción de inmediato</span>
            </div>
          </label>
        </div>
      </div>

      <button 
        className={`mt-10 h-20 w-full rounded-3xl font-black text-xl shadow-2xl transition-all duration-300 flex items-center justify-center gap-4 relative overflow-hidden group ${
          saving || cart.length === 0 
            ? "bg-gray-100 text-gray-400 cursor-not-allowed shadow-none" 
            : "bg-[color:var(--brand-accent)] text-white hover:scale-[1.02] hover:brightness-110 active:scale-95 shadow-[color:var(--brand-accent)]/20"
        }`}
        disabled={saving || cart.length === 0} 
        type="submit"
      >
        {saving ? (
          <>
            <span className="w-6 h-6 border-4 border-white/30 border-t-white rounded-full animate-spin"></span>
            Registrando Operación...
          </>
        ) : (
          <>
            <span className="relative z-10">COMPLETAR VENTA</span>
            <span className="text-2xl group-hover:translate-x-2 transition-transform relative z-10">➜</span>
            {/* Animated Highlight */}
            <div className="absolute inset-0 bg-white/20 translate-x-[-100%] group-hover:translate-x-[100%] transition-transform duration-1000 ease-in-out"></div>
          </>
        )}
      </button>
    </form>
  );
}

