"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createSale,
  createMercadoPagoPointOrder,
  getPaymentConfiguration,
  getProductByBarcode,
  getRecentSales,
  getStoredSession,
  searchClients,
  searchProducts,
  type ClientSummary,
  type ProductSummary,
  type SaleSummary,
  type StoredSession,
} from "@/lib/api";
import { calculateSubtotal, isMembershipPlanProduct, resolveScannedProduct } from "./pos-model";
import { playScanBeep, useBarcodeScanner } from "./barcode-scanner";
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
  const [manualCardEnabled, setManualCardEnabled] = useState(false);
  const [mercadoPagoEnabled, setMercadoPagoEnabled] = useState(false);
  const [paymentReference, setPaymentReference] = useState("");
  const [paymentAmount, setPaymentAmount] = useState("");
  const [validateNow, setValidateNow] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [productLoading, setProductLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [highlightedProductId, setHighlightedProductId] = useState<string | null>(null);
  const [scanError, setScanError] = useState<string | null>(null);
  const saleOperationKey = useRef<string | null>(null);

  useBarcodeScanner({
    onScan: (code) => void handleBarcodeScan(code),
    enabled: Boolean(session),
  });

  useEffect(() => {
    let active = true;
    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) return;
      if (!storedSession) { setLoading(false); return; }
      setSession(storedSession);
      try {
        const [recentSales, paymentConfiguration] = await Promise.all([
          getRecentSales(storedSession.accessToken),
          getPaymentConfiguration(storedSession.accessToken),
        ]);
        if (active) {
          setSales(recentSales);
          setManualCardEnabled(paymentConfiguration.receptionManualTerminalEnabled);
          setMercadoPagoEnabled(paymentConfiguration.mercadoPagoReady);
        }
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

  function addProduct(product: ProductSummary): boolean {
    if (isMembershipPlanProduct(product)) {
      setError("Los planes se gestionan desde la contratación o renovación de membresías.");
      return false;
    }
    setCart((current) => {
      const existing = current.find((item) => item.product.id === product.id);
      if (existing) {
        return current.map((item) =>
          item.product.id === product.id ? { ...item, quantity: item.quantity + 1 } : item,
        );
      }
      return [...current, { product, quantity: 1 }];
    });
    return true;
  }

  async function handleBarcodeScan(code: string) {
    if (!session) return;
    try {
      const match = await resolveScannedProduct(
        products,
        code,
        (barcode) => getProductByBarcode(session.accessToken, barcode),
      );
      if (!addProduct(match)) {
        playScanBeep("error");
        setScanError("Los planes se cobran desde el flujo de membresías.");
        window.setTimeout(() => setScanError(null), 4000);
        return;
      }
      playScanBeep("success");
      setHighlightedProductId(match.id);
      setScanError(null);
      window.setTimeout(() => setHighlightedProductId(null), 1500);
    } catch {
      playScanBeep("error");
      setScanError(`Código no encontrado: ${code}`);
      window.setTimeout(() => setScanError(null), 4000);
    }
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
      const idempotencyKey = saleOperationKey.current ?? `pos:${crypto.randomUUID()}`;
      saleOperationKey.current = idempotencyKey;
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
          validateNow: paymentMethod === "CARD_MERCADO_PAGO" ? false : validateNow,
          notes: null,
        } : null,
        idempotencyKey,
      });
      if (paymentMethod === "CARD_MERCADO_PAGO") {
        const payment = sale.payments.find((item) => item.method === "CARD_MERCADO_PAGO" && item.status === "PENDING_VALIDATION");
        if (!payment) throw new Error("La venta se registro, pero no se encontro el pago pendiente para enviar a Point.");
        await createMercadoPagoPointOrder(session.accessToken, payment.id);
        setMessage(`Cobro de la venta ${sale.sale.saleNumber} enviado a la terminal Point. Esperando confirmacion.`);
      } else {
        setMessage(`Venta ${sale.sale.saleNumber} registrada.`);
      }
      saleOperationKey.current = null;
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
      eyebrow="Terminal de Venta"
      session={session}
      subtitle={`Abierta por ${session.user.fullName}. Registra ventas con cobro inmediato.`}
      title="Caja Registradora"
      mode="operation"
    >
      {error ? <InlineError message={error} /> : null}
      
      <div className="flex flex-col gap-8 w-full max-w-[1600px] mx-auto">
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
                highlightedProductId={highlightedProductId}
                  manualCardEnabled={manualCardEnabled}
                  mercadoPagoEnabled={mercadoPagoEnabled}
                onClearCart={() => setCart([])}
                onPaymentAmountChange={setPaymentAmount}
                  onPaymentMethodChange={(method) => {
                    setPaymentMethod(method);
                    if (method === "CARD_MERCADO_PAGO") setValidateNow(false);
                  }}
                onPaymentReferenceChange={setPaymentReference}
                onQuantityChange={setQuantity}
                onSubmit={submitSale}
                onValidateNowChange={setValidateNow}
                paymentAmount={paymentAmount}
                paymentMethod={paymentMethod}
                paymentReference={paymentReference}
                saving={saving}
                scanError={scanError}
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
