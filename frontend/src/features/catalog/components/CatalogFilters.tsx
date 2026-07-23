import type { CatalogCategory, ProductSearchParams } from "@/lib/api";

type CatalogFiltersProps = {
  categories: CatalogCategory[];
  filters: ProductSearchParams;
  searchInput: string;
  total: number;
  onClear: () => void;
  onSearchChange: (value: string) => void;
  onUpdate: (key: string, value: string | null) => void;
};

export function CatalogFilters({
  categories,
  filters,
  searchInput,
  total,
  onClear,
  onSearchChange,
  onUpdate,
}: CatalogFiltersProps) {
  return (
    <section aria-label="Filtros del catalogo" className="app-card grid gap-4 p-4">
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        <label className="grid gap-1 text-sm xl:col-span-2">
          <span className="font-medium">Buscar</span>
          <input
            className="h-10 rounded-md border border-[#cdd5ca] px-3"
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Nombre, SKU o codigo de barras"
            value={searchInput}
          />
        </label>
        <FilterSelect
          label="Tipo"
          value={filters.type ?? ""}
          onChange={(value) => onUpdate("type", value || null)}
          options={[
            ["", "Todos"],
            ["PHYSICAL", "Fisico"],
            ["SERVICE", "Servicio"],
          ]}
        />
        <FilterSelect
          label="Categoria"
          value={filters.categoryId ?? ""}
          onChange={(value) => onUpdate("categoryId", value || null)}
          options={[["", "Todas"], ...categories.map((category) => [category.id, category.name])]}
        />
        <FilterSelect
          label="Estado"
          value={filters.status ?? ""}
          onChange={(value) => onUpdate("status", value || null)}
          options={[
            ["", "Todos"],
            ["ACTIVE", "Activo"],
            ["INACTIVE", "Inactivo"],
          ]}
        />
        <FilterSelect
          label="Inventario"
          value={filters.inventoryTracked === undefined ? "" : String(filters.inventoryTracked)}
          onChange={(value) => onUpdate("inventoryTracked", value || null)}
          options={[
            ["", "Todos"],
            ["true", "Controlado"],
            ["false", "Sin control"],
          ]}
        />
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-[#5d6b63]">
        <span>{total} resultados</span>
        <button className="app-button-secondary" onClick={onClear} type="button">
          Limpiar filtros
        </button>
      </div>
    </section>
  );
}

function FilterSelect({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: string[][];
  value: string;
}) {
  return (
    <label className="grid gap-1 text-sm">
      <span className="font-medium">{label}</span>
      <select
        className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3"
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        {options.map(([optionValue, optionLabel]) => (
          <option key={optionValue || "all"} value={optionValue}>
            {optionLabel}
          </option>
        ))}
      </select>
    </label>
  );
}
