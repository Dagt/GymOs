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
    <section aria-label="Filtros del catálogo" className="app-card grid gap-3 p-4">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_180px_220px_auto] md:items-end">
        <label className="grid gap-1 text-sm">
          <span className="font-medium">Buscar</span>
          <input className="app-input" onChange={(event) => onSearchChange(event.target.value)} placeholder="Nombre, SKU o código" value={searchInput} />
        </label>
        <Select label="Tipo" value={filters.type ?? ""} onChange={(value) => onUpdate("type", value || null)} options={[["", "Todos"], ["PHYSICAL", "Productos"], ["SERVICE", "Cargos"]]} />
        <Select label="Categoría" value={filters.categoryId ?? ""} onChange={(value) => onUpdate("categoryId", value || null)} options={[["", "Todas"], ...activeCategories.map((category) => [category.id, category.name])]} />
        <button className="app-button-secondary h-10" onClick={onClear} type="button">Limpiar</button>
      </div>
      <details className="rounded-lg border border-[#e1e5de] bg-[#fafbf9] px-3 py-2">
        <summary className="cursor-pointer text-sm font-semibold text-[#42514a]">Más filtros</summary>
        <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Select label="Estado" value={filters.status ?? ""} onChange={(value) => onUpdate("status", value || null)} options={[["", "Todos"], ["ACTIVE", "Activo"], ["INACTIVE", "Inactivo"]]} />
          <Select label="Inventario" value={filters.inventoryTracked === undefined ? "" : String(filters.inventoryTracked)} onChange={(value) => onUpdate("inventoryTracked", value || null)} options={[["", "Todos"], ["true", "Controlado"], ["false", "Sin control"]]} />
          <Select label="Orden" value={filters.sort ?? "displayOrder,asc"} onChange={(value) => onUpdate("sort", value)} options={[["displayOrder,asc", "Predeterminado"], ["name,asc", "Nombre A-Z"], ["name,desc", "Nombre Z-A"], ["createdAt,desc", "Más recientes"], ["price,asc", "Menor precio"], ["price,desc", "Mayor precio"]]} />
          <Select label="Por página" value={String(filters.size ?? 25)} onChange={(value) => onUpdate("size", value)} options={[["10", "10"], ["25", "25"], ["50", "50"], ["100", "100"]]} />
          <label className="flex items-center gap-2 text-sm"><input checked={Boolean(filters.includeArchived)} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => onUpdate("includeArchived", event.target.checked ? "true" : null)} type="checkbox" />Incluir archivados</label>
        </div>
      </details>
      <span className="text-xs text-[#6f7a73]">{total} resultados</span>
    </section>
  );
}

function Select({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: string[][]; value: string }) {
  return <label className="grid gap-1 text-sm"><span className="font-medium">{label}</span><select className="app-input bg-white" onChange={(event) => onChange(event.target.value)} value={value}>{options.map(([optionValue, optionLabel]) => <option key={optionValue || "all"} value={optionValue}>{optionLabel}</option>)}</select></label>;
}
