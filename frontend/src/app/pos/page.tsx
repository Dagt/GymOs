"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createSale,
  getRecentSales,
  getStoredSession,
  searchClients,
  searchProducts,
  type ClientSummary,
  type ProductSummary,
  type SaleSummary,
  type StoredSession,
} from "@/lib/api";
import { calculateSubtotal } from "./pos-model";
import {
  type CartItem,
  type PaymentMethod,
  PosCartForm,
  PosClientSearch,
  ProductGrid,
  RecentSalesList,
} from "./PosSections";

export default function PosPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [sales, setSales] = useState<SaleSummary[]>([]);
  const [clientQuery, setClientQuery] = useState("");
  const [productQuery, setProductQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CASH");
  const [paymentReference, setPaymentReference] = useState("");
  const [paymentAmount, setPaymentAmount] = useState("");
  const [validateNow, setValidateNow] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [productLoading, setProductLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) return;
      if (!storedSession) { setLoading(false); return; }
      setSession(storedSession);
      try {
        const recentSales = await getRecentSales(storedSession.accessToken);
        if (active) setSales(recentSales);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar POS.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void loadInitial();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!session) return;
    let active = true;
    const timer = window.setTimeout(() => {
      setProductLoading(true);
      searchProducts(session.accessToken, {
        q: productQuery.trim(),
        status: "ACTIVE",
        page: 0,
        size: 50,
        sort: "name,asc",
      })
        .then((page) => { if (active) setProducts(page.items); })
        .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible buscar productos."); })
        .finally(() => { if (active) setProductLoading(false); });
    }, productQuery.trim() ? 250 : 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [productQuery, session]);

  const subtotal = useMemo(
    () => calculateSubtotal(cart),
    [cart],
  );

  function addProduct(product: ProductSummary) {
    setCart((current) => {
      const existing = current.find((item) => item.product.id === product.id);
      if (existing) {
        return current.map((item) =>
          item.product.id === product.id ? { ...item, quantity: item.quantity + 1 } : item,
        );
      }
      return [...current, { product, quantity: 1 }];
    });
  }

  function setQuantity(productId: string, quantity: number) {
    setCart((current) =>
      current
        .map((item) => (item.product.id === productId ? { ...item, quantity } : item))
        .filter((item) => item.quantity > 0),
    );
  }

  async function submitClientSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setError(null);
    try {
      setClients(await searchClients(session.accessToken, clientQuery));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible buscar clientes.");
    }
  }

  async function submitSale(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || cart.length === 0) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const amountCents = paymentAmount.trim() ? Math.round(Number(paymentAmount) * 100) : subtotal;
      const sale = await createSale(session.accessToken, {
        clientId: selectedClient?.id ?? null,
        items: cart.map((item) => ({
          productId: item.product.id,
          quantity: item.quantity,
          discountCents: 0,
        })),
        discountCents: 0,
        notes: null,
        payment: amountCents > 0 ? {
          method: paymentMethod,
          amountCents,
          reference: paymentReference,
          receiptObjectKey: null,
          validateNow,
          notes: null,
        } : null,
      });
      setMessage(`Venta ${sale.sale.saleNumber} registrada.`);
      setCart([]);
      setPaymentReference("");
      setSelectedClient(null);
      setClients([]);
      setClientQuery("");
      setSales(await getRecentSales(session.accessToken));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar venta.");
    } finally {
      setSaving(false);
    }
  }

  if (loading && !session) return <LoadingScreen text="Cargando caja..." />;
  if (!session) return <AuthRequired />;

  return (
    <AppShell
      eyebrow="Recepción / Punto de Venta"
      session={session}
      subtitle="Registra ventas de productos y servicios con cobro inmediato."
      title="Caja Registradora"
      mode="operation"
    >
      {error ? <InlineError message={error} /> : null}
      
      <div className="flex flex-col gap-8 w-full max-w-[1600px] mx-auto">
        {/* Colorful Identity & Quick Stats Panel */}
        <section 
          className="relative flex flex-col md:flex-row justify-between items-start md:items-center gap-6 p-8 rounded-3xl shadow-xl overflow-hidden text-white"
          style={{ 
            background: `linear-gradient(135deg, var(--brand-primary), color-mix(in srgb, var(--brand-primary) 70%, var(--brand-accent)))`,
          }}
        >
          {/* Texture Overlay */}
          <div className="absolute inset-0 opacity-10 pointer-events-none" style={{ backgroundImage: `repeating-linear-gradient(45deg, transparent, transparent 10px, rgba(255,255,255,0.2) 10px, rgba(255,255,255,0.2) 20px)`}}></div>
          
          <div className="relative z-10 flex items-center gap-5">
            <div className="h-16 w-16 bg-white/20 border-2 border-white/40 text-white rounded-2xl flex items-center justify-center text-3xl font-black shadow-md" aria-hidden="true">
              🛒
            </div>
            <div>
              <p className="text-sm font-semibold uppercase tracking-widest opacity-80">Terminal de Venta</p>
              <h2 className="text-3xl font-black leading-tight drop-shadow-sm">Caja Abierta</h2>
              <span className="text-sm font-medium mt-1 block opacity-90">
                Atendido por: <strong className="text-white">{session.user.fullName}</strong>
              </span>
            </div>
          </div>
          
          <div className="relative z-10 flex flex-wrap gap-4 self-stretch md:self-auto">
            <div className="flex flex-col justify-center bg-white/10 backdrop-blur-md rounded-2xl px-6 py-3 border border-white/20 min-w-[140px] shadow-sm">
              <span className="text-[0.65rem] font-bold uppercase tracking-wider opacity-70 mb-1">Estado de Caja</span>
              <strong className="text-xl font-black leading-none drop-shadow-sm text-green-300">Lista</strong>
            </div>
            <div className="flex flex-col justify-center bg-white/10 backdrop-blur-md rounded-2xl px-6 py-3 border border-white/20 min-w-[140px] shadow-sm">
              <span className="text-[0.65rem] font-bold uppercase tracking-wider opacity-70 mb-1">Ventas Hoy</span>
              <strong className="text-xl font-black leading-none drop-shadow-sm">{sales.length}</strong>
            </div>
          </div>
        </section>

        {message ? (
          <div className="p-4 rounded-2xl border-2 border-green-100 bg-green-50 text-green-700 font-black flex items-center gap-3 animate-in fade-in slide-in-from-top-4 shadow-sm">
            <span className="text-xl">✅</span>
            {message}
          </div>
        ) : null}

        <div className="grid gap-8 lg:grid-cols-[1fr_400px] xl:grid-cols-[1fr_480px]">
          <div className="flex flex-col gap-8">
            <ProductGrid
              loading={productLoading}
              productQuery={productQuery}
              products={products}
              onAddProduct={addProduct}
              onProductQueryChange={setProductQuery}
            />
            <RecentSalesList sales={sales} />
          </div>

          <aside className="flex flex-col gap-8">
            <div className="sticky top-6 flex flex-col gap-8">
              <PosClientSearch
                clientQuery={clientQuery}
                clients={clients}
                onClientQueryChange={setClientQuery}
                onSearch={submitClientSearch}
                onSelectClient={setSelectedClient}
                selectedClient={selectedClient}
              />
              <PosCartForm
                cart={cart}
                currency={products[0]?.currency ?? cart[0]?.product.currency ?? "MXN"}
                onPaymentAmountChange={setPaymentAmount}
                onPaymentMethodChange={setPaymentMethod}
                onPaymentReferenceChange={setPaymentReference}
                onQuantityChange={setQuantity}
                onSubmit={submitSale}
                onValidateNowChange={setValidateNow}
                paymentAmount={paymentAmount}
                paymentMethod={paymentMethod}
                paymentReference={paymentReference}
                saving={saving}
                subtotal={subtotal}
                validateNow={validateNow}
              />
            </div>
          </aside>
        </div>
      </div>
    </AppShell>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="El punto de venta requiere una sesion de recepcion o administracion."
      title="Inicia sesion para cobrar"
    />
  );
}

function Notice({ text }: { text: string }) {
  return <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">{text}</p>;
}
