"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  getSalesReport,
  getStoredSession,
  type PaymentMethodBreakdown,
  type SaleBusinessContextBreakdown,
  type SaleStatusBreakdown,
  type SalesReportResponse,
  type StoredSession,
} from "@/lib/api";

export default function SalesReportPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [report, setReport] = useState<SalesReportResponse | null>(null);
  const [fromDate, setFromDate] = useState(() => addDays(todayIso(), -6));
  const [toDate, setToDate] = useState(todayIso());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
        const loadedReport = await getSalesReport(storedSession.accessToken, fromDate, toDate);
        if (active) {
          setReport(loadedReport);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar reporte.");
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
  }, [fromDate, toDate]);

  const averageTicket = useMemo(() => {
    if (!report || report.sales.saleCount === 0) {
      return 0;
    }
    return Math.round(report.sales.totalCents / report.sales.saleCount);
  }, [report]);

  async function submitFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setRefreshing(true);
    setError(null);
    try {
      setReport(await getSalesReport(session.accessToken, fromDate, toDate));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar reporte.");
    } finally {
      setRefreshing(false);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando reporte..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  const currency = report?.currency ?? "MXN";

  return (
    <AppShell
      eyebrow="Gerencia"
      session={session}
      subtitle="Resumen operativo de ventas, saldos y pagos manuales."
      title="Reporte de ventas"
    >
      <section className="grid gap-6">
        <form
          className="grid gap-3 rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm md:grid-cols-[1fr_1fr_auto]"
          onSubmit={submitFilters}
        >
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Desde
            <input className="h-11 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setFromDate(event.target.value)} type="date" value={fromDate} />
          </label>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Hasta
            <input className="h-11 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(event) => setToDate(event.target.value)} type="date" value={toDate} />
          </label>
          <button className="app-button h-11 self-end" disabled={refreshing} type="submit">
            {refreshing ? "Actualizando..." : "Actualizar"}
          </button>
        </form>

        {error ? <InlineError message={error} /> : null}

        {report ? (
          <>
            <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <MetricCard label="Ventas" value={report.sales.saleCount.toString()} detail={periodLabel(report)} />
              <MetricCard
                detail={`${formatMoney(averageTicket, currency)} ticket promedio`}
                label="Total vendido"
                value={formatMoney(report.sales.totalCents, currency)}
              />
              <MetricCard
                detail={`${report.payments.completedPayments} pagos completados`}
                label="Cobrado"
                value={formatMoney(report.sales.paidCents, currency)}
              />
              <MetricCard
                detail={`${report.sales.pendingSales + report.sales.partiallyPaidSales} ventas abiertas`}
                label="Saldo pendiente"
                value={formatMoney(report.sales.balanceCents, currency)}
              />
            </section>

            <section className="grid gap-6 lg:grid-cols-2">
              <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                <h2 className="text-xl font-semibold">Ventas por estado</h2>
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-[560px] border-collapse text-sm">
                    <thead className="text-left text-[#6f7a73]">
                      <tr>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Estado</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Ventas</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Total</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Cobrado</th>
                        <th className="border-b border-[#e4e8df] py-2">Saldo</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.salesByStatus.length === 0 ? (
                        <tr>
                          <td className="py-4 text-[var(--brand-muted)]" colSpan={5}>
                            Sin ventas en el periodo.
                          </td>
                        </tr>
                      ) : null}
                      {report.salesByStatus.map((row) => (
                        <SaleStatusRow currency={currency} key={row.status} row={row} />
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>

              <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                <h2 className="text-xl font-semibold">Pagos por método</h2>
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-[620px] border-collapse text-sm">
                    <thead className="text-left text-[#6f7a73]">
                      <tr>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Método</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Pagos</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Completado</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Pendiente</th>
                        <th className="border-b border-[#e4e8df] py-2">Rechazado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.paymentsByMethod.length === 0 ? (
                        <tr>
                          <td className="py-4 text-[var(--brand-muted)]" colSpan={5}>
                            Sin pagos en el periodo.
                          </td>
                        </tr>
                      ) : null}
                      {report.paymentsByMethod.map((row) => (
                        <PaymentMethodRow currency={currency} key={row.method} row={row} />
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>

              <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                <h2 className="text-xl font-semibold">Ventas por tipo</h2>
                <div className="mt-4 overflow-x-auto">
                  <table className="w-full min-w-[620px] border-collapse text-sm">
                    <thead className="text-left text-[#6f7a73]">
                      <tr>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Tipo</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Ventas</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Subtotal</th>
                        <th className="border-b border-[#e4e8df] py-2 pr-3">Descuentos</th>
                        <th className="border-b border-[#e4e8df] py-2">Cobrado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.salesByBusinessContext.map((row) => (
                        <BusinessContextRow currency={currency} key={row.businessContext} row={row} />
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            </section>

            <section className="rounded-lg border border-[var(--brand-line)] bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <h2 className="text-xl font-semibold">Ventas recientes</h2>
                  <p className="mt-1 text-sm text-[var(--brand-muted)]">Últimas operaciones de la sucursal activa.</p>
                </div>
                <Link className="text-sm font-semibold text-[#51735f]" href="/pos">
                  Abrir POS
                </Link>
              </div>
              <div className="mt-4 grid gap-3">
                {report.recentSales.map((sale) => (
                  <Link
                    className="rounded-md border border-[var(--brand-line)] px-4 py-3 transition hover:border-[#51735f] hover:bg-[var(--brand-surface)]"
                    href={`/sales/${sale.id}`}
                    key={sale.id}
                  >
                    <span className="block font-semibold">{sale.saleNumber}</span>
                    <span className="mt-1 block text-sm text-[var(--brand-muted)]">
                      {sale.clientName} - {businessContextLabel(sale.businessContext)} - {saleStatusLabel(sale.status)} - {formatMoney(sale.totalCents, sale.currency)}
                    </span>
                  </Link>
                ))}
              </div>
            </section>
          </>
        ) : null}
      </section>
    </AppShell>
  );
}

function MetricCard({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <article className="app-card p-5">
      <p className="text-sm font-medium text-[#6f7a73]">{label}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
      <p className="mt-2 text-sm text-[var(--brand-muted)]">{detail}</p>
    </article>
  );
}

function SaleStatusRow({ row, currency }: { row: SaleStatusBreakdown; currency: string }) {
  return (
    <tr>
      <td className="border-b border-[#eef1ea] py-3 pr-3 font-semibold">{saleStatusLabel(row.status)}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{row.saleCount}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{formatMoney(row.totalCents, currency)}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{formatMoney(row.paidCents, currency)}</td>
      <td className="border-b border-[#eef1ea] py-3">{formatMoney(row.balanceCents, currency)}</td>
    </tr>
  );
}

function PaymentMethodRow({ row, currency }: { row: PaymentMethodBreakdown; currency: string }) {
  return (
    <tr>
      <td className="border-b border-[#eef1ea] py-3 pr-3 font-semibold">{paymentMethodLabel(row.method)}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{row.paymentCount}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{formatMoney(row.completedCents, currency)}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{formatMoney(row.pendingCents, currency)}</td>
      <td className="border-b border-[#eef1ea] py-3">{formatMoney(row.rejectedCents, currency)}</td>
    </tr>
  );
}

function BusinessContextRow({ row, currency }: { row: SaleBusinessContextBreakdown; currency: string }) {
  return (
    <tr>
      <td className="border-b border-[#eef1ea] py-3 pr-3 font-semibold">{businessContextLabel(row.businessContext)}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{row.saleCount}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{formatMoney(row.subtotalCents, currency)}</td>
      <td className="border-b border-[#eef1ea] py-3 pr-3">{formatMoney(row.discountCents, currency)}</td>
      <td className="border-b border-[#eef1ea] py-3">{formatMoney(row.paidCents, currency)}</td>
    </tr>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="Los reportes de ventas requieren permisos administrativos o de dirección."
      title="Inicia sesión para ver reportes"
    />
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

function saleStatusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING_PAYMENT: "Pendiente",
    PARTIALLY_PAID: "Parcial",
    PAID: "Pagada",
    CANCELLED: "Cancelada",
  };
  return labels[status] ?? status;
}

function paymentMethodLabel(method: string) {
  const labels: Record<string, string> = {
    CASH: "Efectivo",
    TRANSFER: "Transferencia",
    CARD_MANUAL: "Tarjeta manual",
  };
  return labels[method] ?? method;
}

function businessContextLabel(context: string) {
  const labels: Record<string, string> = {
    RETAIL_SALE: "Productos y servicios",
    MEMBERSHIP_ENROLLMENT: "Contrataciones",
    MEMBERSHIP_RENEWAL: "Renovaciones",
    MEMBERSHIP_PLAN_CHANGE: "Cambios de plan",
  };
  return labels[context] ?? context;
}

function periodLabel(report: SalesReportResponse) {
  return `${report.fromDate} a ${report.toDate}`;
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function addDays(dateIso: string, days: number) {
  const date = new Date(`${dateIso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}
