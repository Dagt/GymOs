import type { CatalogSummary } from "@/lib/api/catalog-types";

export function CatalogSummaryCards({ summary }: { summary: CatalogSummary | null }) {
  const metrics = [
    ["Productos", summary?.activeProducts ?? 0],
    ["Cargos", summary?.activeServices ?? 0],
    ["Planes", summary?.activePlans ?? 0],
    ["Stock bajo", summary?.lowStockProducts ?? 0],
  ];
  return (
    <section aria-label="Resumen del catálogo" className="app-card grid grid-cols-2 overflow-hidden p-0 md:grid-cols-4">
      {metrics.map(([label, value], index) => (
        <article
          className={`flex items-center justify-between gap-2 px-3 py-2 ${index > 0 ? "border-l border-[#e3e7df]" : ""}`}
          key={label}
        >
          <p className="text-[11px] font-medium text-[#6f7a73]">{label}</p>
          <p className="text-base font-semibold leading-none">{value}</p>
        </article>
      ))}
    </section>
  );
}
