"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
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

type CartItem = {
  product: ProductSummary;
  quantity: number;
};

export default function PosPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [sales, setSales] = useState<SaleSummary[]>([]);
  const [clientQuery, setClientQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [cart, setCart] = useState<CartItem[]>([]);
  const [paymentMethod, setPaymentMethod] = useState<"CASH" | "TRANSFER" | "CARD_MANUAL">("CASH");
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
      const amountCents = paymentAmount.trim()
        ? Math.round(Number(paymentAmount) * 100)
        : subtotal;
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

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/reception">
              Recepcion
            </Link>
            <h1 className="mt-1 text-3xl font-semibold">POS</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Venta rapida con pago manual y validacion interna.
            </p>
          </div>
          <Link
            className="inline-flex h-11 items-center justify-center rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
            href="/catalog"
          >
            Catalogo
          </Link>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Productos</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {loading ? <p className="text-sm text-[#5d6b63]">Cargando catalogo...</p> : null}
              {products.map((product) => (
                <button
                  className="rounded-md border border-[#d8ddd2] bg-white p-4 text-left transition hover:border-[#51735f] hover:bg-[#f6f7f2]"
                  key={product.id}
                  onClick={() => addProduct(product)}
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
        </section>

        <aside className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Cliente</h2>
            <form className="mt-4 flex gap-2" onSubmit={submitClientSearch}>
              <input
                className="h-11 min-w-0 flex-1 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => setClientQuery(event.target.value)}
                placeholder="Nombre, telefono o email"
                value={clientQuery}
              />
              <button
                className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
                type="submit"
              >
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
                  onClick={() => setSelectedClient(client)}
                  type="button"
                >
                  {client.firstName} {client.lastName}
                </button>
              ))}
            </div>
          </section>

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitSale}>
            <h2 className="text-xl font-semibold">Carrito</h2>
            <div className="mt-4 grid gap-3">
              {cart.length === 0 ? <p className="text-sm text-[#5d6b63]">Sin items.</p> : null}
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
                      onChange={(event) => setQuantity(item.product.id, Number(event.target.value))}
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
                <span>{formatMoney(subtotal, products[0]?.currency ?? "MXN")}</span>
              </div>
              <div className="mt-4 grid gap-3">
                <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                  Metodo
                  <select
                    className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                    onChange={(event) => setPaymentMethod(event.target.value as "CASH" | "TRANSFER" | "CARD_MANUAL")}
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
                    onChange={(event) => setPaymentAmount(event.target.value)}
                    placeholder={(subtotal / 100).toFixed(2)}
                    type="number"
                    value={paymentAmount}
                  />
                </label>
                <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                  Referencia
                  <input
                    className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm"
                    onChange={(event) => setPaymentReference(event.target.value)}
                    value={paymentReference}
                  />
                </label>
                <label className="flex items-center gap-2 text-sm font-medium text-[#42514a]">
                  <input
                    checked={validateNow}
                    onChange={(event) => setValidateNow(event.target.checked)}
                    type="checkbox"
                  />
                  Validar pago ahora
                </label>
              </div>
            </div>

            {error ? (
              <p className="mt-4 rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
                {error}
              </p>
            ) : null}
            {message ? (
              <p className="mt-4 rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
                {message}
              </p>
            ) : null}
            <button
              className="mt-5 h-11 w-full rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={saving || cart.length === 0}
              type="submit"
            >
              {saving ? "Registrando..." : "Registrar venta"}
            </button>
          </form>
        </aside>
      </section>
    </main>
  );
}

function AuthRequired() {
  return (
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesion</h1>
        <Link
          className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
          href="/login"
        >
          Iniciar sesion
        </Link>
      </section>
    </main>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
