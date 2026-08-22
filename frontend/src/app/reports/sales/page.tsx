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
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell
} from "recharts";
import {
  Search,
  CalendarDays,
  ArrowRight,
  TrendingUp,
  Receipt,
  CreditCard,
  AlertCircle,
  Banknote,
  Smartphone,
  Wallet,
  Landmark,
  CheckCircle2,
  Clock,
  XCircle,
  ExternalLink,
  Users
} from "lucide-react";

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
      if (!active) return;
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
    if (!report || report.sales.saleCount === 0) return 0;
    return Math.round(report.sales.totalCents / report.sales.saleCount);
  }, [report]);

  async function submitFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
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

  if (loading) return <LoadingScreen text="Cargando análisis de caja..." />;
  if (!session) return <AuthRequired />;

  const currency = report?.currency ?? "MXN";

  return (
    <AppShell
      eyebrow="Finanzas"
      session={session}
      subtitle="Auditoría de ingresos, cuentas por cobrar y métodos de pago registrados en sucursal."
      title="Reporte de Ventas y Pagos"
    >
      <div className="grid gap-6">
        {/* FILTERS */}
        <div className="rounded-xl border border-[var(--brand-line)] bg-white p-4 shadow-sm">
          <form className="flex flex-col sm:flex-row gap-4 items-end" onSubmit={submitFilters}>
            <div className="flex-1 grid grid-cols-2 gap-4">
              <label className="grid gap-1.5 text-sm font-semibold text-[#42514a]">
                <span className="flex items-center gap-2"><CalendarDays className="h-4 w-4" /> Desde</span>
                <input className="h-10 rounded-lg border border-[var(--brand-line)] px-3 text-sm focus:border-[#51735f] focus:ring-1 focus:ring-[#51735f] transition-shadow" onChange={(event) => setFromDate(event.target.value)} type="date" value={fromDate} />
              </label>
              <label className="grid gap-1.5 text-sm font-semibold text-[#42514a]">
                <span className="flex items-center gap-2"><CalendarDays className="h-4 w-4" /> Hasta</span>
                <input className="h-10 rounded-lg border border-[var(--brand-line)] px-3 text-sm focus:border-[#51735f] focus:ring-1 focus:ring-[#51735f] transition-shadow" onChange={(event) => setToDate(event.target.value)} type="date" value={toDate} />
              </label>
            </div>
            <button className="app-button h-10 px-6 w-full sm:w-auto flex items-center justify-center gap-2" disabled={refreshing} type="submit">
              {refreshing ? (
                <><span className="animate-spin">⟳</span> Actualizando...</>
              ) : (
                <><Search className="h-4 w-4" /> Buscar</>
              )}
            </button>
          </form>
        </div>

        {error ? <InlineError message={error} /> : null}

        {report ? (
          <>
            {/* KPI HEROES */}
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard 
                icon={<Receipt className="h-5 w-5 text-indigo-600" />}
                label="Volumen de Ventas" 
                value={report.sales.saleCount.toString()} 
                detail={`Ticket promedio: ${formatMoney(averageTicket, currency)}`} 
                colorClass="bg-indigo-50 border-indigo-100"
              />
              <MetricCard
                icon={<TrendingUp className="h-5 w-5 text-sky-600" />}
                label="Total Vendido"
                value={formatMoney(report.sales.totalCents, currency)}
                detail="Ingresos proyectados brutos"
                colorClass="bg-sky-50 border-sky-100"
              />
              <MetricCard
                icon={<Wallet className="h-5 w-5 text-emerald-600" />}
                label="Cobrado en Firme"
                value={formatMoney(report.sales.paidCents, currency)}
                detail={`${report.payments.completedPayments} transacciones pagadas`}
                colorClass="bg-emerald-50 border-emerald-100"
              />
              <MetricCard
                icon={<AlertCircle className="h-5 w-5 text-rose-600" />}
                label="Saldo Pendiente"
                value={formatMoney(report.sales.balanceCents, currency)}
                detail={`${report.sales.pendingSales + report.sales.partiallyPaidSales} ventas por liquidar`}
                colorClass={report.sales.balanceCents > 0 ? "bg-rose-50 border-rose-100" : "bg-gray-50 border-gray-100"}
              />
            </section>

            {/* CHARTS GRID */}
            <section className="grid gap-6 lg:grid-cols-2">
              
              {/* Payment Methods Breakdown */}
              <div className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm flex flex-col">
                <div className="mb-4">
                  <h3 className="text-lg font-bold text-[#2b3a32]">Distribución por Método de Pago</h3>
                  <p className="text-sm text-gray-500">Volumen monetario cobrado</p>
                </div>
                
                <div className="h-[280px] w-full flex-shrink-0">
                  {report.paymentsByMethod.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={report.paymentsByMethod.filter(m => m.completedCents > 0)}
                          cx="50%"
                          cy="50%"
                          innerRadius={60}
                          outerRadius={90}
                          paddingAngle={5}
                          dataKey="completedCents"
                          nameKey="method"
                        >
                          {report.paymentsByMethod.map((entry, index) => (
                            <Cell key={"cell-" + index} fill={COLORS_PAYMENTS[index % COLORS_PAYMENTS.length]} />
                          ))}
                        </Pie>
                        <Tooltip formatter={(val: any) => formatMoney(Number(val), currency)} labelFormatter={(l: any) => paymentMethodLabel(String(l))} />
                      </PieChart>
                    </ResponsiveContainer>
                  ) : (
                    <EmptyState message="Sin pagos registrados" />
                  )}
                </div>

                <div className="mt-4 flex flex-wrap gap-4 justify-center">
                  {report.paymentsByMethod.filter(m => m.completedCents > 0).map((row, i) => (
                    <div key={row.method} className="flex items-center gap-2">
                      <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS_PAYMENTS[i % COLORS_PAYMENTS.length] }}></div>
                      <div className="text-sm">
                        <span className="font-medium text-gray-700 block">{paymentMethodLabel(row.method)}</span>
                        <span className="text-gray-500 font-semibold">{formatMoney(row.completedCents, currency)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Status Breakdown BarChart */}
              <div className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm flex flex-col">
                <div className="mb-4">
                  <h3 className="text-lg font-bold text-[#2b3a32]">Estado de Cartera</h3>
                  <p className="text-sm text-gray-500">Comparativa de montos según estatus</p>
                </div>
                
                <div className="h-[280px] w-full flex-shrink-0">
                  {report.salesByStatus.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={report.salesByStatus} margin={{ top: 20, right: 20, left: 20, bottom: 20 }}>
                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                        <XAxis dataKey="status" tickFormatter={saleStatusLabel} axisLine={false} tickLine={false} />
                        <YAxis tickFormatter={(val) => `$${val/100}`} axisLine={false} tickLine={false} />
                        <Tooltip formatter={(val: any) => formatMoney(Number(val), currency)} labelFormatter={(l: any) => saleStatusLabel(String(l))} cursor={{fill: 'transparent'}} />
                        <Legend />
                        <Bar dataKey="paidCents" name="Cobrado" fill="#10b981" radius={[4, 4, 0, 0]} maxBarSize={40} />
                        <Bar dataKey="balanceCents" name="Pendiente (Saldo)" fill="#f43f5e" radius={[4, 4, 0, 0]} maxBarSize={40} />
                      </BarChart>
                    </ResponsiveContainer>
                  ) : (
                    <EmptyState message="Sin ventas registradas" />
                  )}
                </div>
              </div>
            </section>

            {/* CONTEXTS / ORIGINS (Compact Horizontal Bars) */}
            <section className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
              <h3 className="text-lg font-bold text-[#2b3a32] mb-1">Ventas por Naturaleza de Negocio</h3>
              <p className="text-sm text-gray-500 mb-6">Análisis de ingresos por tipo de producto/servicio</p>
              
              <div className="grid gap-4">
                {report.salesByBusinessContext.map((row) => {
                  const percentage = report.sales.totalCents > 0 ? (row.subtotalCents / report.sales.totalCents) * 100 : 0;
                  return (
                    <div key={row.businessContext} className="group">
                      <div className="flex justify-between items-end mb-1">
                        <span className="text-sm font-semibold text-gray-700">{businessContextLabel(row.businessContext)}</span>
                        <div className="text-right">
                          <span className="text-sm font-bold text-[#2b3a32]">{formatMoney(row.subtotalCents, currency)}</span>
                          <span className="text-xs text-gray-500 ml-2">({row.saleCount} ventas)</span>
                        </div>
                      </div>
                      <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                        <div className="bg-indigo-500 h-2.5 rounded-full transition-all duration-500 group-hover:bg-indigo-600" style={{ width: `${percentage}%` }}></div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* LEDGER: RECENT SALES */}
            <section className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between mb-6">
                <div>
                  <h3 className="text-lg font-bold text-[#2b3a32]">Libro Mayor de Ventas</h3>
                  <p className="text-sm text-gray-500">Últimas operaciones procesadas en la sucursal activa.</p>
                </div>
                <Link className="inline-flex items-center gap-2 text-sm font-semibold text-white bg-[#51735f] hover:bg-[#3d5948] px-4 py-2 rounded-lg transition-colors" href="/pos">
                  Abrir Punto de Venta <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
              
              <div className="overflow-x-auto">
                <table className="w-full min-w-[800px] border-collapse">
                  <thead className="bg-gray-50/80 text-left text-xs uppercase tracking-wider text-gray-500">
                    <tr>
                      <th className="p-3 rounded-tl-lg font-medium">Referencia</th>
                      <th className="p-3 font-medium">Cliente</th>
                      <th className="p-3 font-medium">Naturaleza</th>
                      <th className="p-3 font-medium">Estado</th>
                      <th className="p-3 font-medium text-right">Monto</th>
                      <th className="p-3 rounded-tr-lg font-medium text-center">Acción</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 text-sm">
                    {report.recentSales.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="p-8 text-center text-gray-500">
                          <div className="flex flex-col items-center justify-center gap-2">
                            <Receipt className="h-8 w-8 text-gray-300" />
                            <p>No hay ventas recientes en este periodo.</p>
                          </div>
                        </td>
                      </tr>
                    ) : null}
                    {report.recentSales.map((sale) => (
                      <tr key={sale.id} className="hover:bg-gray-50/50 transition-colors group">
                        <td className="p-3">
                          <Link href={`/sales/${sale.id}`} className="font-mono font-semibold text-indigo-600 hover:text-indigo-800">
                            {sale.saleNumber}
                          </Link>
                          <div className="text-xs text-gray-400 mt-0.5">
                            {new Date(sale.soldAt).toLocaleDateString('es-MX', {day: '2-digit', month: 'short', hour: '2-digit', minute:'2-digit'})}
                          </div>
                        </td>
                        <td className="p-3">
                          <div className="font-medium text-gray-900">{sale.clientName}</div>
                        </td>
                        <td className="p-3">
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-gray-100 text-gray-700">
                            {businessContextIcon(sale.businessContext)}
                            {businessContextLabel(sale.businessContext)}
                          </span>
                        </td>
                        <td className="p-3">
                          <StatusBadge status={sale.status} />
                        </td>
                        <td className="p-3 text-right">
                          <div className="font-bold text-gray-900">{formatMoney(sale.totalCents, sale.currency)}</div>
                          {sale.balanceCents > 0 && (
                            <div className="text-xs font-semibold text-rose-500 mt-0.5">
                              Resta: {formatMoney(sale.balanceCents, sale.currency)}
                            </div>
                          )}
                        </td>
                        <td className="p-3 text-center">
                          <Link href={`/sales/${sale.id}`} className="inline-flex items-center justify-center p-2 text-gray-400 hover:text-[#51735f] hover:bg-[#eef1ea] rounded-full transition-colors">
                            <ExternalLink className="h-4 w-4" />
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        ) : null}
      </div>
    </AppShell>
  );
}

function MetricCard({ label, value, detail, icon, colorClass }: { label: string; value: string; detail: string, icon: React.ReactNode, colorClass: string }) {
  return (
    <article className={`rounded-xl border bg-white p-5 shadow-sm transition-shadow hover:shadow-md`}>
      <div className="flex items-start gap-4">
        <div className={`p-3 rounded-xl border ${colorClass}`}>
          {icon}
        </div>
        <div>
          <p className="text-sm font-semibold text-gray-500 uppercase tracking-wide">{label}</p>
          <p className="mt-1 text-2xl font-bold text-gray-900">{value}</p>
        </div>
      </div>
      <p className="mt-4 text-sm font-medium text-gray-500">{detail}</p>
    </article>
  );
}

function StatusBadge({ status }: { status: string }) {
  switch (status) {
    case 'PAID':
      return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700"><CheckCircle2 className="h-3 w-3" /> Pagada</span>;
    case 'PARTIALLY_PAID':
      return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-700"><Clock className="h-3 w-3" /> Parcial</span>;
    case 'PENDING_PAYMENT':
      return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-700"><AlertCircle className="h-3 w-3" /> Pendiente</span>;
    case 'CANCELLED':
      return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700"><XCircle className="h-3 w-3" /> Cancelada</span>;
    default:
      return <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">{status}</span>;
  }
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex h-full w-full items-center justify-center rounded-lg border-2 border-dashed border-gray-100 bg-gray-50/50 min-h-[150px]">
      <p className="text-sm font-medium text-gray-400">{message}</p>
    </div>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="Los reportes financieros detallados requieren permisos administrativos o de dirección."
      title="Inicia sesión para auditar ventas"
    />
  );
}

const COLORS_PAYMENTS = ['#10b981', '#3b82f6', '#f59e0b', '#8b5cf6', '#64748b'];

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
    CARD_MANUAL: "Tarjeta terminal",
    CARD_MERCADO_PAGO: "Mercado Pago Point",
    CARD_ONLINE: "Stripe",
  };
  return labels[method] ?? method;
}

function businessContextLabel(context: string) {
  const labels: Record<string, string> = {
    RETAIL_SALE: "Productos/Servicios",
    MEMBERSHIP_ENROLLMENT: "Nuevos Contratos",
    MEMBERSHIP_RENEWAL: "Renovaciones",
    MEMBERSHIP_PLAN_CHANGE: "Cambio Plan",
  };
  return labels[context] ?? context;
}

function businessContextIcon(context: string) {
  switch(context) {
    case 'RETAIL_SALE': return <ShoppingBagIcon />;
    case 'MEMBERSHIP_ENROLLMENT': return <Users className="h-3 w-3" />;
    case 'MEMBERSHIP_RENEWAL': return <RefreshCcwIcon />;
    case 'MEMBERSHIP_PLAN_CHANGE': return <TrendingUp className="h-3 w-3" />;
    default: return <Receipt className="h-3 w-3" />;
  }
}

function ShoppingBagIcon() {
  return <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
}

function RefreshCcwIcon() {
  return <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21v-5h5"/></svg>
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