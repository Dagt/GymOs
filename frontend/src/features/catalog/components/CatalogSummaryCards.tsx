import type { CatalogSummary } from "@/lib/api/catalog-types";

export function CatalogSummaryCards({ summary }: { summary: CatalogSummary | null }) {
  const metrics = [
    ["Productos", summary?.activeProducts ?? 0],
    ["Cargos", summary?.activeServices ?? 0],
    ["Planes", summary?.activePlans ?? 0],
    ["Stock bajo", summary?.lowStockProducts ?? 0],
  ];
  return (
    <section aria-label="Resumen del catálogo" className="app-card grid grid-cols-2 gap-2 p-2.5">
      {metrics.map(([label, value]) => (
        <article className="flex min-h-16 flex-col justify-center rounded-lg border border-[#e3e7df] bg-[#fafbf9] px-3 py-2" key={label}>
          <p className="text-[10px] font-semibold uppercase tracking-wide text-[#6f7a73]">{label}</p>
          <p className="mt-1 text-xl font-semibold leading-none text-[#111815]">{value}</p>
        </article>
      ))}
    </section>
  );
}
