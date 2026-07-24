import type { CatalogSummary } from "@/lib/api/catalog-types";

export function CatalogSummaryCards({ summary }: { summary: CatalogSummary | null }) {
  const metrics = [
    ["Productos", summary?.activeProducts ?? 0],
    ["Cargos", summary?.activeServices ?? 0],
    ["Planes", summary?.activePlans ?? 0],
    ["Stock bajo", summary?.lowStockProducts ?? 0],
  ];
  return (
    <section aria-label="Resumen del catálogo" className="app-card grid h-fit grid-cols-2 gap-2 p-2">
      {metrics.map(([label, value]) => (
        <article className="flex min-h-[4.25rem] min-w-0 flex-col justify-center rounded-md border border-[#e3e7df] bg-[#fafbf9] px-3 py-2" key={label}>
          <p className="truncate text-[9px] font-semibold uppercase tracking-wide text-[#6f7a73]">{label}</p>
          <p className="mt-1 text-lg font-semibold leading-none text-[#111815]">{value}</p>
        </article>
      ))}
    </section>
  );
}
