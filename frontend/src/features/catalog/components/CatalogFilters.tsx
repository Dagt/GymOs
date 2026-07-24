import type { CatalogCategory, ProductSearchParams } from "@/lib/api";

type Props = {
  categories: CatalogCategory[];
  filters: ProductSearchParams;
  searchInput: string;
  total: number;
  onClear: () => void;
  onSearchChange: (value: string) => void;
  onUpdate: (key: string, value: string | null) => void;
};

export function CatalogFilters({ categories, filters, searchInput, total, onClear, onSearchChange, onUpdate }: Props) {
  const activeCategories = categories.filter((category) => category.status === "ACTIVE");
  return (
    <section aria-label="Filtros del catálogo" className="app-card h-full p-2.5">
      <div className="grid gap-1.5 sm:grid-cols-2 lg:grid-cols-[minmax(190px,250px)_120px_160px_auto] lg:justify-start">
        <label>
          <span className="sr-only">Buscar</span>
          <input className="app-input h-8 min-h-8 px-2.5 text-xs" onChange={(event) => onSearchChange(event.target.value)} placeholder="Nombre, SKU o código" value={searchInput} />
        </label>
        <CompactSelect label="Tipo" value={filters.type ?? ""} onChange={(value) => onUpdate("type", value || null)} options={[["", "Todos los tipos"], ["PHYSICAL", "Productos"], ["SERVICE", "Cargos"]]} />
        <CompactSelect label="Categoría" value={filters.categoryId ?? ""} onChange={(value) => onUpdate("categoryId", value || null)} options={[["", "Todas las categorías"], ...activeCategories.map((category) => [category.id, category.name])]} />
        <button className="app-button-secondary h-8 min-h-8 px-2.5 text-[11px]" onClick={onClear} type="button">Limpiar</button>
      </div>
      <details className="mt-1.5 rounded-md border border-[#e1e5de] bg-[#fafbf9] px-2.5 py-1.5" open>
        <summary className="cursor-pointer text-[10px] font-semibold leading-none text-[#42514a]">Filtros · {total} resultados</summary>
        <div className="mt-1.5 grid items-end gap-1.5 sm:grid-cols-2 lg:grid-cols-[105px_115px_145px_90px_auto]">
          <Select label="Estado" value={filters.status ?? ""} onChange={(value) => onUpdate("status", value || null)} options={[["", "Todos"], ["ACTIVE", "Activo"], ["INACTIVE", "Inactivo"]]} />
          <Select label="Inventario" value={filters.inventoryTracked === undefined ? "" : String(filters.inventoryTracked)} onChange={(value) => onUpdate("inventoryTracked", value || null)} options={[["", "Todos"], ["true", "Controlado"], ["false", "Sin control"]]} />
          <Select label="Orden" value={filters.sort ?? "displayOrder,asc"} onChange={(value) => onUpdate("sort", value)} options={[["displayOrder,asc", "Predeterminado"], ["name,asc", "Nombre A-Z"], ["name,desc", "Nombre Z-A"], ["createdAt,desc", "Más recientes"], ["price,asc", "Menor precio"], ["price,desc", "Mayor precio"]]} />
          <Select label="Por página" value={String(filters.size ?? 25)} onChange={(value) => onUpdate("size", value)} options={[["10", "10"], ["25", "25"], ["50", "50"], ["100", "100"]]} />
          <label className="flex h-8 items-center gap-1.5 whitespace-nowrap text-[10px]"><input checked={Boolean(filters.includeArchived)} className="h-3.5 w-3.5 accent-[var(--brand-primary)]" onChange={(event) => onUpdate("includeArchived", event.target.checked ? "true" : null)} type="checkbox" />Archivados</label>
        </div>
      </details>
    </section>
  );
}

function CompactSelect({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: string[][]; value: string }) {
  return <label><span className="sr-only">{label}</span><select aria-label={label} className="app-input h-8 min-h-8 bg-white px-2 text-[11px]" onChange={(event) => onChange(event.target.value)} value={value}>{options.map(([optionValue, optionLabel]) => <option key={optionValue || "all"} value={optionValue}>{optionLabel}</option>)}</select></label>;
}

function Select({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: string[][]; value: string }) {
  return <label className="grid gap-0.5 text-[9px]"><span className="font-semibold text-[#536158]">{label}</span><select className="app-input h-8 min-h-8 bg-white px-2 text-[10px]" onChange={(event) => onChange(event.target.value)} value={value}>{options.map(([optionValue, optionLabel]) => <option key={optionValue || "all"} value={optionValue}>{optionLabel}</option>)}</select></label>;
}
