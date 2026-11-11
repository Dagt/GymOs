import Link from "next/link";
import { Clock3 } from "lucide-react";
import { useEffect, useState } from "react";
import { getRecentSales, type SaleSummary, type StoredSession } from "@/lib/api";

const operationLabels = new Map([
  ["MEMBERSHIP_ENROLLMENT", "Asignación de membresía"],
  ["MEMBERSHIP_RENEWAL", "Renovación"],
  ["MEMBERSHIP_PLAN_CHANGE", "Cambio de plan"],
  ["VISIT_PACKAGE_PURCHASE", "Paquete de visitas"],
]);
const statusLabels = {
  PAID: { label: "Pagada", color: "bg-green-100 text-green-800" },
  PENDING_PAYMENT: { label: "Pendiente", color: "bg-amber-100 text-amber-900" },
  PARTIALLY_PAID: { label: "Pago parcial", color: "bg-blue-100 text-blue-800" },
  CANCELLED: { label: "Cancelada", color: "bg-slate-100 text-slate-700" },
};
const date = new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" });

export function RecentReceptionSales({ session, revision }: { session: StoredSession; revision: number }) {
  const [result, setResult] = useState<{ rows: SaleSummary[]; failed: boolean; sampleHasSales: boolean }>();
  const [retry, setRetry] = useState(0);
  const token = session.accessToken;
  useEffect(() => {
    let active = true;
    getRecentSales(token).then((sales) => {
      if (active) setResult({ rows: sales.filter((sale) => operationLabels.has(sale.businessContext)).slice(0, 8), failed: false, sampleHasSales: sales.length > 0 });
    }).catch(() => {
      if (active) setResult({ rows: [], failed: true, sampleHasSales: false });
    });
    return () => { active = false; };
  }, [token, revision, retry]);

  return (
    <section className="app-card min-w-0 p-4" aria-labelledby="reception-recent">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="app-card-title flex items-center gap-2" id="reception-recent">
          <Clock3 aria-hidden="true" className="size-5" /> Operaciones recientes
        </h2>
        {session.user.permissions.some((permission) => permission === "report.sales.read" || permission === "sale.create") ? <Link className="text-sm font-semibold text-[var(--brand-primary)] underline" href={session.user.permissions.includes("report.sales.read") ? "/reports/sales" : "/pos"}>
          Ver todas →
        </Link> : null}
      </div>
      {!result ? <p role="status">Cargando operaciones…</p>
        : result.failed ? <div className="flex flex-wrap items-center gap-3"><p role="alert">No fue posible consultar las operaciones recientes.</p>
          <button type="button" className="app-button-secondary" onClick={() => { setResult(undefined); setRetry((value) => value + 1); }}>Reintentar</button>
        </div>
        : result.rows.length === 0 ? <p className="text-sm text-[var(--brand-muted)]">{result.sampleHasSales ? "No hay contrataciones entre las últimas ventas consultadas." : "Todavía no hay contrataciones registradas en esta sucursal."}</p>
        : (
          <table className="w-full table-fixed text-left text-sm md:table-auto">
            <thead className="bg-[var(--brand-surface)]">
              <tr>
                {["Fecha y hora", "Socio", "Operación", "Detalle", "Estado", "Acción"].map((label) => (
                  <th key={label} scope="col" className="hidden px-2 py-2 md:table-cell">{label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((sale) => {
                const status = statusLabels[sale.status];
                const total = new Intl.NumberFormat("es-MX", { style: "currency", currency: sale.currency }).format(sale.totalCents / 100);
                return (
                  <tr className="grid grid-cols-2 gap-2 border-t border-[var(--brand-line)] py-3 md:table-row" key={sale.id}>
                    <td className="min-w-0 px-2 md:py-2"><time dateTime={sale.soldAt}>{date.format(new Date(sale.soldAt))}</time></td>
                    <td className="min-w-0 wrap-anywhere px-2 font-medium md:py-2">{sale.clientName}</td>
                    <td className="min-w-0 px-2 md:py-2">{operationLabels.get(sale.businessContext)}</td>
                    <td className="min-w-0 wrap-anywhere px-2 md:py-2">{sale.saleNumber} · {total}</td>
                    <td className="px-2 md:py-2"><span className={`inline-block rounded-full px-2 py-1 text-xs font-semibold ${status?.color ?? ""}`}>{status?.label ?? sale.status}</span></td>
                    <td className="px-2 md:py-2"><Link className="underline" aria-label={`Consultar venta ${sale.saleNumber}`} href={`/sales/${sale.id}`}>Consultar</Link></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
    </section>
  );
}
