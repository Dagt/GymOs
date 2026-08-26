"use client";

import { useEffect, useState, useMemo } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { CoachingDashboard } from "@/features/coaching/CoachingDashboard";
import {
  getOwnerDashboard,
  getSalesReport,
  getInventorySummary,
  getStoredSession,
  type OwnerDashboard,
  type SalesReportResponse,
  type InventorySummary,
  type StoredSession,
} from "@/lib/api";
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import {
  TrendingUp,
  Users,
  AlertTriangle,
  Package,
  Calendar,
  Database,
  Wallet,
  Activity,
  CheckCircle2,
  XCircle,
  Clock,
  BarChart3
} from "lucide-react";

export default function DashboardPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [dashboard, setDashboard] = useState<OwnerDashboard | null>(null);
  const [report, setReport] = useState<SalesReportResponse | null>(null);
  const [inventorySummary, setInventorySummary] = useState<InventorySummary | null>(null);
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [coachingRevision, setCoachingRevision] = useState(0);
  const [activeTab, setActiveTab] = useState<"owner" | "manager" | "coaching">("owner");

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) return;
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const initialDashboard = await getOwnerDashboard(stored.accessToken);
        if (!active) return;
        
        setDashboard(initialDashboard);
        setFromDate(initialDashboard.fromDate);
        setToDate(initialDashboard.toDate);

        const [initialReport, invSummary] = await Promise.all([
          getSalesReport(stored.accessToken, initialDashboard.fromDate, initialDashboard.toDate),
          getInventorySummary(stored.accessToken).catch(() => null), // Failsafe if not implemented
        ]);
        
        if (active) {
          setReport(initialReport);
          setInventorySummary(invSummary);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar dashboard.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, []);

  async function refresh() {
    if (!session) return;
    setError(null);
    try {
      const [newDashboard, newReport, invSummary] = await Promise.all([
        getOwnerDashboard(session.accessToken, fromDate, toDate),
        getSalesReport(session.accessToken, fromDate, toDate),
        getInventorySummary(session.accessToken).catch(() => null),
      ]);
      setDashboard(newDashboard);
      setReport(newReport);
      setInventorySummary(invSummary);
      setCoachingRevision((value) => value + 1);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar dashboard.");
    }
  }

  // Derived Metrics
  const ticketPromedio = useMemo(() => {
    if (!report || report.sales.saleCount === 0) return 0;
    return Math.round(report.sales.totalCents / report.sales.saleCount);
  }, [report]);

  const eficienciaCobro = useMemo(() => {
    if (!report || report.sales.totalCents === 0) return 0;
    return Math.round((report.sales.paidCents / report.sales.totalCents) * 100);
  }, [report]);

  // Time Series for Area Chart (Sales over time)
  const timeSeriesData = useMemo(() => {
    if (!report || !report.recentSales) return [];
    
    // Group sales by day
    const grouped = report.recentSales.reduce((acc, sale) => {
      const day = sale.soldAt.substring(0, 10);
      if (!acc[day]) {
        acc[day] = { date: day, ingresos: 0, volumen: 0 };
      }
      acc[day].ingresos += (sale.paidCents / 100);
      acc[day].volumen += 1;
      return acc;
    }, {} as Record<string, { date: string, ingresos: number, volumen: number }>);
    
    // Convert to sorted array
    return Object.values(grouped).sort((a, b) => a.date.localeCompare(b.date));
  }, [report]);

  if (loading) return <LoadingScreen text="Cargando análisis métrico..." />;
  if (!session) return <AuthRequired />;

  return (
    <AppShell
      eyebrow="Panel de Control Avanzado"
      session={session}
      subtitle="Inteligencia de negocios y métricas operativas de la sucursal activa."
      title="Dashboard"
    >
      <section className="grid min-w-0 gap-6">
        <section className="admin-panel flex flex-col md:flex-row md:items-end md:justify-between">
          <div>
            <p className="app-eyebrow">Periodo de análisis</p>
            <h2 className="text-xl font-bold">Filtro de Datos</h2>
          </div>
          <div className="admin-command-actions mt-4 justify-start md:mt-0">
            <input aria-label="Inicio del periodo" className="h-10 min-w-0 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(e) => setFromDate(e.target.value)} type="date" value={fromDate} />
            <input aria-label="Fin del periodo" className="h-10 min-w-0 rounded-md border border-[var(--brand-line)] px-3 text-sm" onChange={(e) => setToDate(e.target.value)} type="date" value={toDate} />
            <button className="app-button" onClick={refresh} type="button">Consultar</button>
          </div>
        </section>

        {error ? <InlineError message={error} /> : null}

        {dashboard && report ? (
          <>
            {/* TABS */}
            <div className="min-w-0 border-b border-[var(--brand-line)] mb-2">
              <nav className="-mb-px flex space-x-8 overflow-x-auto" aria-label="Tabs">
                <button
                  onClick={() => setActiveTab("owner")}
                  className={`${
                    activeTab === "owner"
                      ? "border-[#2b3a32] text-[#2b3a32]"
                      : "border-transparent text-[#6f7a73] hover:border-[var(--brand-line)] hover:text-gray-700"
                  } whitespace-nowrap border-b-2 px-1 py-4 text-sm font-semibold flex items-center gap-2 transition-colors`}
                >
                  <BarChart3 className="h-4 w-4" /> Inteligencia estratégica
                </button>
                <button
                  onClick={() => setActiveTab("manager")}
                  className={`${
                    activeTab === "manager"
                      ? "border-[#2b3a32] text-[#2b3a32]"
                      : "border-transparent text-[#6f7a73] hover:border-[var(--brand-line)] hover:text-gray-700"
                  } whitespace-nowrap border-b-2 px-1 py-4 text-sm font-semibold flex items-center gap-2 transition-colors`}
                >
                  <Activity className="h-4 w-4" /> Pulso Operativo
                </button>
                <button
                  onClick={() => setActiveTab("coaching")}
                  className={`${
                    activeTab === "coaching"
                      ? "border-[#2b3a32] text-[#2b3a32]"
                      : "border-transparent text-[#6f7a73] hover:border-[var(--brand-line)] hover:text-gray-700"
                  } whitespace-nowrap border-b-2 px-1 py-4 text-sm font-semibold flex items-center gap-2 transition-colors`}
                >
                  <Users className="h-4 w-4" /> Coaches y Rutinas
                </button>
              </nav>
            </div>

            {/* TAB CONTENT: OWNER (Strategic) */}
            {activeTab === "owner" && (
              <div className="animate-in fade-in duration-300 grid gap-6">
                {/* Top KPIs */}
                <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  <MetricCard
                    icon={<Wallet className="h-5 w-5 text-emerald-600" />}
                    title="Ingresos Recaudados"
                    value={formatMoney(dashboard.paidRevenueCents, "MXN")}
                    subtitle="Flujo de caja efectivo"
                  />
                  <MetricCard
                    icon={<TrendingUp className="h-5 w-5 text-indigo-600" />}
                    title="Ticket Promedio"
                    value={formatMoney(ticketPromedio, "MXN")}
                    subtitle={`Basado en ${report.sales.saleCount} ventas`}
                  />
                  <MetricCard
                    icon={<Users className="h-5 w-5 text-blue-600" />}
                    title="Membresías vigentes"
                    value={dashboard.activeMemberships.toString()}
                    subtitle={`+${dashboard.newClients} nuevos socios`}
                  />
                  <MetricCard
                    icon={<Database className="h-5 w-5 text-purple-600" />}
                    title="Capital en Inventario"
                    value={inventorySummary ? formatMoney(inventorySummary.totalValuationCents, "MXN") : "$---"}
                    subtitle={`Artículos disponibles: ${inventorySummary?.totalTrackedProducts ?? '-'}`}
                  />
                </section>

                {/* Main Charts */}
                <section className="grid gap-6 lg:grid-cols-3">
                  <div className="lg:col-span-2 rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                    <div className="flex items-center justify-between mb-6">
                      <div>
                        <h3 className="text-lg font-semibold text-[#2b3a32]">Evolución de ingresos diarios</h3>
                        <p className="text-sm text-gray-500">Tendencia de caja en el periodo seleccionado</p>
                      </div>
                      <div className="px-3 py-1 bg-emerald-50 text-emerald-700 font-medium text-xs rounded-full">
                        Eficiencia de cobro: {eficienciaCobro}%
                      </div>
                    </div>
                    
                    <div className="h-[300px] w-full">
                      {timeSeriesData.length > 0 ? (
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={timeSeriesData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                            <defs>
                              <linearGradient id="colorIngresos" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                                <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" />
                            <XAxis 
                              dataKey="date" 
                              tickFormatter={(date) => new Date(date).toLocaleDateString('es-MX', { day: '2-digit', month: 'short'})} 
                              stroke="#9ca3af" 
                              fontSize={12} 
                            />
                            <YAxis 
                              tickFormatter={(val) => `$${(val/1000).toFixed(0)}k`} 
                              stroke="#9ca3af" 
                              fontSize={12} 
                            />
                            <Tooltip 
                              formatter={(val: unknown) => formatMoney(Number(val) * 100, "MXN")}
                              labelFormatter={(date) => new Date(String(date)).toLocaleDateString('es-MX', { weekday: 'long', day: '2-digit', month: 'long'})} 
                              contentStyle={{ borderRadius: '8px', border: '1px solid #e5e7eb', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                            />
                            <Area type="monotone" dataKey="ingresos" name="Cobrado" stroke="#10b981" strokeWidth={3} fillOpacity={1} fill="url(#colorIngresos)" />
                          </AreaChart>
                        </ResponsiveContainer>
                      ) : (
                        <EmptyState message="No hay suficientes datos históricos en este periodo" />
                      )}
                    </div>
                  </div>

                  <div className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                    <h3 className="text-lg font-semibold mb-1 text-[#2b3a32]">Fuentes de Ingreso</h3>
                    <p className="text-sm text-gray-500 mb-6">Distribución por concepto</p>
                    <div className="h-[250px] w-full">
                      {report.salesByBusinessContext.length > 0 ? (
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={report.salesByBusinessContext}
                              cx="50%"
                              cy="50%"
                              innerRadius={65}
                              outerRadius={95}
                              paddingAngle={5}
                              dataKey="paidCents"
                              nameKey="businessContext"
                            >
                              {report.salesByBusinessContext.map((entry, index) => (
                                <Cell key={"cell-" + index} fill={COLORS_CONTEXT[index % COLORS_CONTEXT.length]} />
                              ))}
                            </Pie>
                            <Tooltip formatter={(val: unknown) => formatMoney(Number(val), "MXN")} labelFormatter={(l: unknown) => businessContextLabel(String(l))} />
                          </PieChart>
                        </ResponsiveContainer>
                      ) : (
                        <EmptyState message="Sin datos" />
                      )}
                    </div>
                    {/* Custom Legend */}
                    <div className="mt-4 space-y-2">
                      {report.salesByBusinessContext.map((item, i) => (
                        <div key={item.businessContext} className="flex items-center justify-between text-sm">
                          <div className="flex items-center gap-2">
                            <span className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS_CONTEXT[i % COLORS_CONTEXT.length] }}></span>
                            <span className="text-gray-600">{businessContextLabel(item.businessContext)}</span>
                          </div>
                          <span className="font-medium text-[#2b3a32]">{formatMoney(item.paidCents, "MXN")}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </section>
              </div>
            )}

            {/* TAB CONTENT: MANAGER (Operational) */}
            {activeTab === "manager" && (
              <div className="animate-in fade-in duration-300 grid gap-6">
                {/* Top KPIs */}
                <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  <MetricCard
                    icon={<AlertTriangle className="h-5 w-5 text-rose-600" />}
                    title="Dinero por Cobrar"
                    value={formatMoney(report.sales.balanceCents, "MXN")}
                    subtitle={`${report.sales.pendingSales + report.sales.partiallyPaidSales} ventas con adeudo`}
                    alert={report.sales.balanceCents > 0}
                  />
                  <MetricCard
                    icon={<Package className="h-5 w-5 text-amber-600" />}
                    title="Alertas de Inventario"
                    value={String((inventorySummary?.lowStockCount ?? 0) + (inventorySummary?.outOfStockCount ?? 0))}
                    subtitle={`${inventorySummary?.outOfStockCount ?? 0} agotados, ${inventorySummary?.lowStockCount ?? 0} bajo stock`}
                    alert={((inventorySummary?.lowStockCount ?? 0) + (inventorySummary?.outOfStockCount ?? 0)) > 0}
                  />
                  <MetricCard
                    icon={<Calendar className="h-5 w-5 text-slate-600" />}
                    title="Flujo de Reservas"
                    value={dashboard.bookingCount.toString()}
                    subtitle="Reservas en el periodo"
                  />
                  <MetricCard
                    icon={<Activity className="h-5 w-5 text-emerald-600" />}
                    title="Rutinas & Coaches"
                    value={dashboard.activeWorkoutAssignments.toString()}
                    subtitle={`${dashboard.activeProfessionals} profesionales activos`}
                  />
                </section>

                {/* Operations & Charts */}
                <section className="grid gap-6 lg:grid-cols-3">
                  {/* Recent Operations Timeline */}
                  <div className="lg:col-span-1 rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm flex flex-col">
                    <div className="flex items-center justify-between mb-4 pb-4 border-b border-gray-100">
                      <h3 className="text-lg font-semibold text-[#2b3a32]">Últimos movimientos</h3>
                      <span className="text-xs font-medium bg-[#f1f4ec] text-[#51735f] px-2 py-1 rounded-full">
                        Recientes
                      </span>
                    </div>
                    
                    <div className="flex-1 overflow-y-auto pr-2 max-h-[400px] custom-scrollbar">
                      {report.recentSales.length > 0 ? (
                        <div className="space-y-4">
                          {report.recentSales.map((sale) => (
                            <div key={sale.id} className="flex items-start gap-3 group">
                              <div className="relative mt-1">
                                <div className={`h-8 w-8 rounded-full flex items-center justify-center shrink-0 ${sale.status === 'PAID' ? 'bg-emerald-100' : sale.status === 'CANCELLED' ? 'bg-rose-100' : 'bg-amber-100'}`}>
                                  {sale.status === 'PAID' ? <CheckCircle2 className="h-4 w-4 text-emerald-600" /> : 
                                   sale.status === 'CANCELLED' ? <XCircle className="h-4 w-4 text-rose-600" /> : 
                                   <Clock className="h-4 w-4 text-amber-600" />}
                                </div>
                                <div className="absolute top-8 bottom-[-16px] left-1/2 -ml-px w-px bg-gray-100 group-last:hidden"></div>
                              </div>
                              <div className="flex-1 pb-1">
                                <div className="flex justify-between items-start">
                                  <p className="text-sm font-semibold text-gray-900 leading-tight">{sale.clientName}</p>
                                  <p className="text-sm font-bold text-gray-900 leading-tight">{formatMoney(sale.totalCents, sale.currency)}</p>
                                </div>
                                <div className="flex justify-between items-center mt-1">
                                  <p className="text-xs text-gray-500">{businessContextLabel(sale.businessContext)} · {sale.saleNumber}</p>
                                  <p className={`text-[10px] font-bold uppercase tracking-wider ${sale.status === 'PAID' ? 'text-emerald-600' : 'text-amber-600'}`}>
                                    {saleStatusLabel(sale.status)}
                                  </p>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <EmptyState message="No hay operaciones recientes" />
                      )}
                    </div>
                  </div>

                  <div className="lg:col-span-2 grid grid-rows-2 gap-6">
                    {/* Status Chart */}
                    <div className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                      <h3 className="text-sm font-semibold text-gray-500 mb-4 uppercase tracking-wider">Estado de Cobranza (Volumen)</h3>
                      <div className="h-[160px] w-full">
                        {report.salesByStatus.length > 0 ? (
                          <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={report.salesByStatus} margin={{ top: 0, right: 0, left: 0, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f3f4f6" />
                              <XAxis dataKey="status" tickFormatter={saleStatusLabel} axisLine={false} tickLine={false} fontSize={12} />
                              <YAxis axisLine={false} tickLine={false} fontSize={12} />
                              <Tooltip formatter={(val: unknown) => Number(val)} labelFormatter={(l: unknown) => saleStatusLabel(String(l))} cursor={{fill: '#f9fafb'}} />
                              <Bar dataKey="saleCount" name="Operaciones" fill="#3b82f6" radius={[4, 4, 0, 0]} maxBarSize={60} />
                            </BarChart>
                          </ResponsiveContainer>
                        ) : (
                          <EmptyState message="No hay ventas" />
                        )}
                      </div>
                    </div>

                    {/* Payment Methods Chart */}
                    <div className="rounded-xl border border-[var(--brand-line)] bg-white p-5 shadow-sm">
                      <h3 className="text-sm font-semibold text-gray-500 mb-4 uppercase tracking-wider">Vías de ingreso (métodos de pago)</h3>
                      <div className="h-[160px] w-full">
                        {report.paymentsByMethod.length > 0 ? (
                          <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={report.paymentsByMethod} layout="vertical" margin={{ top: 0, right: 30, left: 30, bottom: 0 }}>
                              <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#f3f4f6" />
                              <XAxis type="number" tickFormatter={(val) => formatMoney(val, "MXN")} axisLine={false} tickLine={false} fontSize={12} />
                              <YAxis dataKey="method" type="category" tickFormatter={paymentMethodLabel} axisLine={false} tickLine={false} fontSize={12} width={80} />
                              <Tooltip formatter={(val: unknown) => formatMoney(Number(val), "MXN")} labelFormatter={(l: unknown) => paymentMethodLabel(String(l))} cursor={{fill: '#f9fafb'}} />
                              <Bar dataKey="completedCents" name="Cobrado" fill="#10b981" radius={[0, 4, 4, 0]} maxBarSize={30} />
                            </BarChart>
                          </ResponsiveContainer>
                        ) : (
                          <EmptyState message="No hay pagos" />
                        )}
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            )}
            {activeTab === "coaching" && <CoachingDashboard key={coachingRevision} accessToken={session.accessToken} from={dashboard.fromDate} to={dashboard.toDate} />}
          </>
        ) : null}
      </section>
      <style dangerouslySetInnerHTML={{__html: `
        .custom-scrollbar::-webkit-scrollbar {
          width: 4px;
        }
        .custom-scrollbar::-webkit-scrollbar-track {
          background: transparent;
        }
        .custom-scrollbar::-webkit-scrollbar-thumb {
          background-color: #e5e7eb;
          border-radius: 10px;
        }
      `}} />
    </AppShell>
  );
}

// UI Components
function MetricCard({ title, value, subtitle, icon, alert = false }: { title: string, value: string, subtitle: string, icon: React.ReactNode, alert?: boolean }) {
  return (
    <div className={`rounded-xl border bg-white p-5 shadow-sm transition-all hover:shadow-md flex flex-col justify-between ${alert ? "border-rose-200 bg-rose-50/30" : "border-[var(--brand-line)]"}`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-semibold text-gray-500 uppercase tracking-wide">{title}</p>
          <h4 className="mt-2 text-3xl font-bold text-[#2b3a32]">{value}</h4>
        </div>
        <div className={`p-3 rounded-xl shadow-sm ${alert ? "bg-white text-rose-600" : "bg-[#f1f4ec] text-[#2b3a32]"}`}>
          {icon}
        </div>
      </div>
      <p className={`mt-4 text-sm flex items-center gap-1 font-medium ${alert ? "text-rose-600" : "text-[var(--brand-muted)]"}`}>
        {alert && <AlertTriangle className="h-4 w-4" />}
        {subtitle}
      </p>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex h-full w-full items-center justify-center rounded-xl border-2 border-dashed border-gray-200 bg-gray-50/50 min-h-[150px]">
      <p className="text-sm font-medium text-gray-500">{message}</p>
    </div>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="El panel de control requiere permisos administrativos o de dirección."
      title="Inicia sesión para ver indicadores"
    />
  );
}

// Formatters
const COLORS_CONTEXT = ['#10b981', '#3b82f6', '#8b5cf6', '#f59e0b', '#ec4899'];

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency, maximumFractionDigits: 0 }).format(cents / 100);
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
    CARD_MANUAL: "Terminal",
    CARD_MERCADO_PAGO: "Mercado Pago",
    CARD_ONLINE: "Stripe",
  };
  return labels[method] ?? method;
}

function businessContextLabel(context: string) {
  const labels: Record<string, string> = {
    RETAIL_SALE: "Mostrador",
    MEMBERSHIP_ENROLLMENT: "Nuevas membresías",
    MEMBERSHIP_RENEWAL: "Renovaciones",
    MEMBERSHIP_PLAN_CHANGE: "Upgrades",
  };
  return labels[context] ?? context;
}
