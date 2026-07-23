import type { ProductSummary } from "@/lib/api";

export function CatalogSummaryCards({ products }: { products: ProductSummary[] }) {
  const metrics = [
    ["Activos", products.filter((product) => product.status === "ACTIVE").length],
    ["Fisicos", products.filter((product) => product.productType === "PHYSICAL").length],
    ["Servicios", products.filter((product) => product.productType === "SERVICE").length],
    ["Con inventario", products.filter((product) => product.inventoryTracked).length],
    [
      "Incompletos",
      products.filter((product) => !product.categoryId || (!product.barcode && product.productType === "PHYSICAL")).length,
    ],
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
