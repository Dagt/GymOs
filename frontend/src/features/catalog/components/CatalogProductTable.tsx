import Link from "next/link";
import type { ProductSummary } from "@/lib/api";
import { formatMoney } from "@/app/catalog/CatalogHelpers";

type CatalogProductTableProps = {
  loading: boolean;
  products: ProductSummary[];
  saving: boolean;
  canArchive: boolean;
  canManageStatus: boolean;
  canUpdate: boolean;
  onArchive: (product: ProductSummary) => void;
  onEdit: (product: ProductSummary) => void;
  onRestore: (product: ProductSummary) => void;
  onToggleStatus: (product: ProductSummary) => void;
};

export function CatalogProductTable({ loading, products, saving, canArchive, canManageStatus, canUpdate,
  onArchive, onEdit, onRestore, onToggleStatus }: CatalogProductTableProps) {
  return (
    <section className="app-card overflow-hidden">
      <div className="flex items-center justify-between border-b border-[#e3e7df] p-4">
        <div><p className="app-eyebrow">Administracion</p><h2 className="app-card-title">Productos y servicios</h2></div>
        <span className="text-sm text-[#5d6b63]">{products.length} en esta pagina</span>
      </div>
      {loading ? <TableSkeleton /> : null}
      {!loading && products.length === 0 ? <div className="p-8 text-center text-sm text-[#5d6b63]">No hay resultados para estos filtros. Limpialos o crea un producto nuevo.</div> : null}
      {!loading && products.length ? (
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-[#f6f8f4] text-xs uppercase tracking-wide text-[#5d6b63]"><tr>
              <th className="px-4 py-3">Producto</th><th className="px-4 py-3">Categoria</th><th className="px-4 py-3">Tipo</th>
              <th className="px-4 py-3">Precio</th><th className="px-4 py-3">Inventario</th><th className="px-4 py-3">Estado</th>
              <th className="px-4 py-3 text-right">Acciones</th>
            </tr></thead>
            <tbody className="divide-y divide-[#e3e7df]">
              {products.map((product) => (
                <tr className={product.archived ? "bg-[#faf7f2] opacity-80" : undefined} key={product.id}>
                  <td className="px-4 py-3"><p className="font-semibold">{product.name}</p><p className="text-xs text-[#6f7a73]">{product.sku}{product.barcode ? ` · ${product.barcode}` : ""}</p></td>
                  <td className="px-4 py-3">{product.categoryName ?? "Sin categoria"}</td>
                  <td className="px-4 py-3">{product.productType === "PHYSICAL" ? "Fisico" : "Servicio"}</td>
                  <td className="px-4 py-3 font-medium">{formatMoney(product.priceCents, product.currency)}</td>
                  <td className="px-4 py-3">{product.inventoryTracked ? "Controlado" : "No controlado"}</td>
                  <td className="px-4 py-3"><ProductStatus product={product} /></td>
                  <td className="px-4 py-3"><div className="flex justify-end gap-2">
                    <Link className="rounded-md border border-[#cdd5ca] px-2 py-1 text-xs font-semibold text-[var(--brand-primary)]" href={`/catalog/${product.id}`}>Detalle</Link>
                    {product.archived ? (
                      canArchive ? <Action disabled={saving} label="Restaurar" onClick={() => onRestore(product)} /> : null
                    ) : (<>
                      {canUpdate ? <Action label="Editar" onClick={() => onEdit(product)} /> : null}
                      {canManageStatus ? <Action disabled={saving} label={product.status === "ACTIVE" ? "Desactivar" : "Activar"} onClick={() => onToggleStatus(product)} /> : null}
                      {canArchive ? <Action danger disabled={saving} label="Archivar" onClick={() => onArchive(product)} /> : null}
                    </>)}
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

function ProductStatus({ product }: { product: ProductSummary }) {
  if (product.archived) return <span className="font-semibold text-[#76552e]">Archivado</span>;
  return <span className={product.status === "ACTIVE" ? "text-[#28733e]" : "text-[#8a5a22]"}>{product.status === "ACTIVE" ? "Activo" : "Inactivo"}</span>;
}

function Action({ danger = false, disabled = false, label, onClick }: { danger?: boolean; disabled?: boolean; label: string; onClick: () => void; }) {
  return <button className={`rounded-md border px-2 py-1 text-xs font-semibold ${danger ? "border-[#e2b6b6] text-[#9b2c2c]" : "border-[#cdd5ca] text-[var(--brand-primary)]"}`} disabled={disabled} onClick={onClick} type="button">{label}</button>;
}

function TableSkeleton() {
  return <div aria-label="Cargando productos" className="grid gap-3 p-4" role="status">{[0, 1, 2].map((row) => <div className="h-12 animate-pulse rounded-md bg-[#eef1eb]" key={row} />)}</div>;
}
