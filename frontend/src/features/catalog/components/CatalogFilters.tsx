import controls from "@/components/CommercialControls.module.css";
import styles from "./CatalogFilters.module.css";
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
    <section aria-label="Filtros del catálogo" className={`app-card ${styles.filters} ${controls.controls}`}>
      <div className={styles.main}>
        <label className="form-field">
          <span>Buscar</span>
          <input className="app-input" onChange={(event) => onSearchChange(event.target.value)} placeholder="Nombre, SKU o código" value={searchInput} />
        </label>
        <CompactSelect label="Tipo" value={filters.type ?? ""} onChange={(value) => onUpdate("type", value || null)} options={[["", "Todos los tipos"], ["PHYSICAL", "Productos"], ["SERVICE", "Cargos"]]} />
        <CompactSelect label="Categoría" value={filters.categoryId ?? ""} onChange={(value) => onUpdate("categoryId", value || null)} options={[["", "Todas las categorías"], ...activeCategories.map((category) => [category.id, category.name])]} />
        <button className="app-button-secondary" onClick={onClear} type="button">Limpiar</button>
      </div>
      <details className={styles.advanced} open>
        <summary className="cursor-pointer text-sm font-semibold">Filtros · {total} resultados</summary>
        <div className={styles.options}>
          <Select label="Estado" value={filters.status ?? ""} onChange={(value) => onUpdate("status", value || null)} options={[["", "Todos"], ["ACTIVE", "Activo"], ["INACTIVE", "Inactivo"]]} />
          <Select label="Inventario" value={filters.inventoryTracked === undefined ? "" : String(filters.inventoryTracked)} onChange={(value) => onUpdate("inventoryTracked", value || null)} options={[["", "Todos"], ["true", "Controlado"], ["false", "Sin control"]]} />
          <Select label="Orden" value={filters.sort ?? "displayOrder,asc"} onChange={(value) => onUpdate("sort", value)} options={[["displayOrder,asc", "Predeterminado"], ["name,asc", "Nombre A-Z"], ["name,desc", "Nombre Z-A"], ["createdAt,desc", "Más recientes"], ["price,asc", "Menor precio"], ["price,desc", "Mayor precio"]]} />
          <label className="flex min-h-11 items-center gap-2 text-sm"><input checked={Boolean(filters.includeArchived)} className="h-3.5 w-3.5 accent-[var(--brand-primary)]" onChange={(event) => onUpdate("includeArchived", event.target.checked ? "true" : null)} type="checkbox" />Archivados</label>
        </div>
      </details>
    </section>
  );
}

function CompactSelect({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: string[][]; value: string }) {
  return <label className="form-field"><span>{label}</span><select aria-label={label} className="app-input" onChange={(event) => onChange(event.target.value)} value={value}>{options.map(([optionValue, optionLabel]) => <option key={optionValue || "all"} value={optionValue}>{optionLabel}</option>)}</select></label>;
}

function Select({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: string[][]; value: string }) {
  return <label className="form-field"><span className="font-semibold text-[#536158]">{label}</span><select aria-label={label} className="app-input" onChange={(event) => onChange(event.target.value)} value={value}>{options.map(([optionValue, optionLabel]) => <option key={optionValue || "all"} value={optionValue}>{optionLabel}</option>)}</select></label>;
}
