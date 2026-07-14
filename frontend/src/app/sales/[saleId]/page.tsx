"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import {
  getSale,
  getStoredSession,
  uploadPaymentReceipt,
  validatePayment,
  type SaleDetailResponse,
  type StoredSession,
} from "@/lib/api";

export default function SaleDetailPage() {
  const params = useParams<{ saleId: string }>();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [data, setData] = useState<SaleDetailResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [validatingPaymentId, setValidatingPaymentId] = useState<string | null>(null);
  const [uploadingPaymentId, setUploadingPaymentId] = useState<string | null>(null);

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
        const response = await getSale(storedSession.accessToken, params.saleId);
        if (active) {
          setData(response);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar venta.");
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
  }, [params.saleId]);

  async function validate(paymentId: string) {
    if (!session) {
      return;
    }
    setValidatingPaymentId(paymentId);
    setError(null);
    try {
      setData(await validatePayment(session.accessToken, paymentId));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible validar pago.");
    } finally {
      setValidatingPaymentId(null);
    }
  }

  async function uploadReceipt(paymentId: string, file: File | null) {
    if (!session || !file) {
      return;
    }
    setUploadingPaymentId(paymentId);
    setError(null);
    try {
      setData(await uploadPaymentReceipt(session.accessToken, paymentId, file));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible subir comprobante.");
    } finally {
      setUploadingPaymentId(null);
    }
  }

  if (!session) {
    return <AuthRequired />;
  }

  if (loading || !data) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando venta...
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/pos">
              POS
            </Link>
            <h1 className="mt-1 text-3xl font-semibold">{data.sale.saleNumber}</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {data.sale.clientName} - {data.sale.status}
            </p>
          </div>
          <div className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-right">
            <p className="text-xs font-semibold uppercase tracking-wide text-[#51735f]">Saldo</p>
            <p className="text-2xl font-semibold">{formatMoney(data.sale.balanceCents, data.sale.currency)}</p>
          </div>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1fr_1fr]">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Items</h2>
          <div className="mt-4 grid gap-3">
            {data.items.map((item) => (
              <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={item.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{item.name}</p>
                    <p className="mt-1 text-sm text-[#5d6b63]">
                      {item.quantity} x {formatMoney(item.unitPriceCents, data.sale.currency)}
                    </p>
                  </div>
                  <p className="font-semibold">{formatMoney(item.totalCents, data.sale.currency)}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Pagos</h2>
          <div className="mt-4 grid gap-3">
            {data.payments.length === 0 ? (
              <p className="text-sm text-[#5d6b63]">Sin pagos registrados.</p>
            ) : null}
            {data.payments.map((payment) => (
              <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={payment.id}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold">{payment.method}</p>
                    <p className="mt-1 text-sm text-[#5d6b63]">
                      {payment.status} - {payment.reference ?? "Sin referencia"}
                    </p>
                  </div>
                  <p className="font-semibold">{formatMoney(payment.amountCents, payment.currency)}</p>
                </div>
                {payment.receiptObjectKey ? (
                  <p className="mt-2 break-all text-xs text-[#6f7a73]">
                    Comprobante: {payment.receiptObjectKey}
                  </p>
                ) : null}
                <label className="mt-3 grid gap-2 text-sm font-medium text-[#42514a]">
                  Comprobante
                  <input
                    className="text-sm"
                    disabled={uploadingPaymentId === payment.id}
                    onChange={(event) => uploadReceipt(payment.id, event.target.files?.[0] ?? null)}
                    type="file"
                  />
                </label>
                {payment.status === "PENDING_VALIDATION" ? (
                  <button
                    className="mt-3 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                    disabled={validatingPaymentId === payment.id}
                    onClick={() => validate(payment.id)}
                    type="button"
                  >
                    {validatingPaymentId === payment.id ? "Validando..." : "Validar pago"}
                  </button>
                ) : null}
              </article>
            ))}
          </div>
        </section>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm lg:col-span-2">
          <h2 className="text-xl font-semibold">Membresias activadas</h2>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            {data.memberships.length === 0 ? (
              <p className="text-sm text-[#5d6b63]">Sin membresias activadas por esta venta.</p>
            ) : null}
            {data.memberships.map((membership) => (
              <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={membership.id}>
                <p className="font-semibold">{membership.planName}</p>
                <p className="mt-1 text-sm text-[#5d6b63]">
                  {membership.status} - {membership.startsOn} a {membership.endsOn}
                </p>
                <p className="mt-1 text-xs text-[#6f7a73]">
                  Gracia hasta {membership.graceEndsOn}
                </p>
              </article>
            ))}
          </div>
        </section>

        {error ? (
          <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f] lg:col-span-2">
            {error}
          </p>
        ) : null}
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
