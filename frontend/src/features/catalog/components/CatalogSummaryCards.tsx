import type { CatalogSummary } from "@/lib/api/catalog-types";

export function CatalogSummaryCards({ summary }: { summary: CatalogSummary | null }) {
  const metrics = [
    ["Productos activos", summary?.activeProducts ?? 0],
    ["Servicios activos", summary?.activeServices ?? 0],
    ["Planes activos", summary?.activePlans ?? 0],
    ["Stock bajo", summary?.lowStockProducts ?? 0],
    ["Incompletos", (summary?.uncategorizedProducts ?? 0) + (summary?.productsWithoutBarcode ?? 0) + (summary?.productsWithoutImage ?? 0)],
  ];
  return (
    <section aria-label="Resumen del catalogo" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      {metrics.map(([label, value]) => (
        <article className="app-card p-4" key={label}>
          <p className="text-sm text-[#5d6b63]">{label}</p>
          <p className="mt-1 text-2xl font-semibold">{value}</p>
        </article>
      ))}
    </section>
  );
}
