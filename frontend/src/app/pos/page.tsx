"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createSale,
  getProducts,
  getRecentSales,
  getStoredSession,
  searchClients,
  type ClientSummary,
  type ProductSummary,
  type SaleSummary,
  type StoredSession,
} from "@/lib/api";
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
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) {
        return;
      }
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      try {
        const [loadedProducts, recentSales] = await Promise.all([
          getProducts(storedSession.accessToken),
          getRecentSales(storedSession.accessToken),
        ]);
        if (!active) {
          return;
        }
        setProducts(loadedProducts.filter((product) => product.status === "ACTIVE"));
        setSales(recentSales);
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar POS.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadInitial();
    return () => {
      active = false;
    };
  }, []);

  const subtotal = useMemo(
    () => cart.reduce((sum, item) => sum + item.product.priceCents * item.quantity, 0),
    [cart],
  );
  const visibleProducts = useMemo(() => {
    const query = productQuery.trim().toLowerCase();
    if (!query) return products;
    return products.filter((product) =>
      product.name.toLowerCase().includes(query)
      || product.sku.toLowerCase().includes(query)
      || product.barcode?.toLowerCase() === query,
    );
  }, [productQuery, products]);

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
    if (!session) {
      return;
    }
    setError(null);
    try {
      setClients(await searchClients(session.accessToken, clientQuery));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible buscar clientes.");
    }
  }

  async function submitSale(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || cart.length === 0) {
      return;
    }
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
        payment:
          amountCents > 0
            ? {
                method: paymentMethod,
                amountCents,
                reference: paymentReference,
                receiptObjectKey: null,
                validateNow,
                notes: null,
              }
            : null,
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

  if (loading && !session) {
    return <LoadingScreen text="Cargando caja..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <AppShell
      eyebrow="Recepcion / Caja"
      session={session}
      subtitle="Venta rapida con pago manual y validacion interna."
      title="Caja / POS"
    >
      {error ? <InlineError message={error} /> : null}
      {message ? <Notice text={message} /> : null}

      <section className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="grid gap-6">
          <ProductGrid
            loading={loading}
            productQuery={productQuery}
            products={visibleProducts}
            onAddProduct={addProduct}
            onProductQueryChange={setProductQuery}
          />
          <RecentSalesList sales={sales} />
        </section>

        <aside className="grid gap-6">
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
            currency={products[0]?.currency ?? "MXN"}
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
        </aside>
      </section>
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
  return (
    <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
      {text}
    </p>
  );
}
