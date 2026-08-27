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
      <div className="flex items-center justify-between border-b border-[#e3e7df] px-3 py-2.5">
        <h2 className="text-sm font-semibold">Productos y cargos de caja</h2>
        <span className="text-[11px] text-[#5d6b63]">{products.length} en esta página</span>
      </div>
      {loading ? <TableSkeleton /> : null}
      {!loading && products.length === 0 ? <div className="p-6 text-center text-xs text-[#5d6b63]">No hay resultados para estos filtros.</div> : null}
      {!loading && products.length ? (
        <div key={products[0]?.id} className="app-scroll-list" aria-label="Listado del catálogo" role="region" tabIndex={0}>
          <table className="min-w-full text-left text-[12px]">
            <thead className="sticky top-0 z-10 bg-[#f6f8f4] text-[10px] uppercase tracking-wide text-[#5d6b63]"><tr>
              <th className="px-3 py-2">Registro</th><th className="px-3 py-2">Categoría</th><th className="px-3 py-2">Tipo</th>
              <th className="px-3 py-2">Precio</th><th className="px-3 py-2">Inventario</th><th className="px-3 py-2">Estado</th>
              <th className="px-3 py-2 text-right">Acciones</th>
            </tr></thead>
            <tbody className="divide-y divide-[#e3e7df]">
              {products.map((product) => (
                <tr className={product.archived ? "bg-[#faf7f2] opacity-80" : "hover:bg-[#fafbf9]"} key={product.id}>
                  <td className="px-3 py-2">
                    <p className="font-semibold leading-tight">{product.familyName ?? product.name}</p>
                    {product.variantName ? <p className="mt-0.5 text-[11px] font-medium leading-tight text-[#405348]">{product.variantName}{product.presentation && product.presentation !== product.variantName ? ` · ${product.presentation}` : ""}</p> : null}
                    <p className="mt-0.5 text-[10px] leading-tight text-[#6f7a73]">{product.sku}{barcodeCount(product) ? ` · ${barcodeCount(product)} ${barcodeCount(product) === 1 ? "código" : "códigos"}` : ""}</p>
                  </td>
                  <td className="px-3 py-2">{product.categoryName ?? "Sin categoría"}</td>
                  <td className="px-3 py-2">{product.productType === "PHYSICAL" ? "Producto" : "Cargo"}</td>
                  <td className="px-3 py-2 font-medium">{formatMoney(product.priceCents, product.currency)}</td>
                  <td className="px-3 py-2">{product.inventoryTracked ? "Controlado" : "No aplica"}</td>
                  <td className="px-3 py-2"><ProductStatus product={product} /></td>
                  <td className="px-3 py-2"><div className="flex flex-wrap justify-end gap-1">
                    <Link className="rounded border border-[#cdd5ca] px-1.5 py-1 text-[9px] font-semibold leading-none text-[var(--brand-primary)]" href={`/catalog/${product.id}`}>Detalle</Link>
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

function barcodeCount(product: ProductSummary) {
  return product.barcodes?.length ?? (product.barcode ? 1 : 0);
}

function ProductStatus({ product }: { product: ProductSummary }) {
  if (product.archived) return <span className="text-[10px] font-semibold text-[#76552e]">Archivado</span>;
  return <span className={product.status === "ACTIVE" ? "text-[10px] text-[#28733e]" : "text-[10px] text-[#8a5a22]"}>{product.status === "ACTIVE" ? "Activo" : "Inactivo"}</span>;
}

function Action({ danger = false, disabled = false, label, onClick }: { danger?: boolean; disabled?: boolean; label: string; onClick: () => void; }) {
  return <button className={`rounded border px-1.5 py-1 text-[9px] font-semibold leading-none ${danger ? "border-[#e2b6b6] text-[#9b2c2c]" : "border-[#cdd5ca] text-[var(--brand-primary)]"}`} disabled={disabled} onClick={onClick} type="button">{label}</button>;
}

function TableSkeleton() {
  return <div aria-label="Cargando productos" className="grid gap-2 p-3" role="status">{[0, 1, 2, 3, 4].map((row) => <div className="h-9 animate-pulse rounded-md bg-[#eef1eb]" key={row} />)}</div>;
}
