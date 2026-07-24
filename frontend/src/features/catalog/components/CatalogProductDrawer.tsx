import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductPayload } from "@/lib/api";

type Props = {
  categories: CatalogCategory[];
  canManageImages: boolean;
  canManageInventory: boolean;
  canReadCosts: boolean;
  editing: boolean;
  existingImage: boolean;
  form: ProductPayload;
  open: boolean;
  price: string;
  reorderPoint: string;
  image: File | null;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: ProductPayload) => void;
  onPriceChange: (value: string) => void;
  onReorderPointChange: (value: string) => void;
  onImageChange: (file: File | null) => void;
  onRemoveImage: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CatalogProductDrawer({
  categories,
  canManageImages,
  canManageInventory,
  canReadCosts,
  editing,
  existingImage,
  form,
  open,
  price,
  reorderPoint,
  image,
  saving,
  onClose,
  onFormChange,
  onPriceChange,
  onReorderPointChange,
  onImageChange,
  onRemoveImage,
  onSubmit,
}: Props) {
  const options = categoryOptions(categories.filter((category) =>
    category.status === "ACTIVE" || category.id === form.categoryId,
  ));
  const change = (values: Partial<ProductPayload>) => onFormChange({ ...form, ...values });

  function changeType(value: string) {
    const productType = value as ProductPayload["productType"];
    change({
      productType,
      commercialSubtype: productType === "PHYSICAL" ? "GENERAL_PRODUCT" : "GENERAL_SERVICE",
      inventoryTracked: productType === "PHYSICAL" ? form.inventoryTracked : false,
    });
  }

  return (
    <AppDrawer onClose={onClose} open={open} title={editing ? "Editar artículo o cargo" : "Nuevo artículo o cargo"}>
      <form className="grid gap-4" onSubmit={onSubmit}>
        <section className="grid gap-4 rounded-xl border border-[#d8ddd2] bg-white p-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Nombre" name="productName" placeholder={form.productType === "SERVICE" ? "Inscripción" : "Agua 600 ml"} value={form.name} onChange={(name) => change({ name })} />
            <TextField label="SKU" name="productSku" placeholder={form.productType === "SERVICE" ? "INSCRIPCION" : "AGUA-600"} value={form.sku} onChange={(sku) => change({ sku })} />
            <NumberField label="Precio" name="productPrice" value={price} onChange={onPriceChange} />
            <SelectField label="Tipo" name="productType" options={[{ label: "Producto físico", value: "PHYSICAL" }, { label: "Cargo de caja", value: "SERVICE" }]} value={form.productType} onChange={changeType} />
            <SelectField label="Categoría" name="productCategory" required={false} options={[{ label: "Sin categoría", value: "" }, ...options]} value={form.categoryId ?? ""} onChange={(categoryId) => change({ categoryId: categoryId || null })} />
            {form.productType === "PHYSICAL" && form.inventoryTracked && canManageInventory ? (
              <NumberField label="Stock mínimo" min="0" name="productReorderPoint" required={false} value={reorderPoint} onChange={onReorderPointChange} />
            ) : null}
          </div>

          {form.productType === "PHYSICAL" ? (
            <label className="flex h-11 items-center gap-3 rounded-md border border-[#cdd5ca] px-3 text-sm font-medium">
              <input checked={form.inventoryTracked} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => change({ inventoryTracked: event.target.checked })} type="checkbox" />
              Controlar inventario
            </label>
          ) : null}
        </section>

        <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
          <summary className="cursor-pointer font-semibold">Más datos</summary>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            {form.productType === "PHYSICAL" ? (
              <>
                <TextField label="Código de barras" name="productBarcode" placeholder="7501234567890" required={false} value={form.barcode ?? ""} onChange={(barcode) => change({ barcode: barcode || null })} />
                <TextField label="Marca" name="productBrand" placeholder="Marca" required={false} value={form.brand ?? ""} onChange={(brand) => change({ brand: brand || null })} />
                <TextField label="Presentación" name="productPresentation" placeholder="Bote de 30 porciones" required={false} value={form.presentation ?? ""} onChange={(presentation) => change({ presentation: presentation || null })} />
                <TextField label="Unidad" name="productUnit" placeholder="Pieza" required={false} value={form.unitOfMeasure ?? ""} onChange={(unitOfMeasure) => change({ unitOfMeasure: unitOfMeasure || null })} />
              </>
            ) : null}
            <TextField label="Etiquetas" name="productTags" placeholder="energía, cafeína" required={false} value={form.tags.join(", ")} onChange={(value) => change({ tags: value.split(",").map((tag) => tag.trim()).filter(Boolean) })} />
            <div className="sm:col-span-2">
              <TextAreaField label="Descripción" name="productDescription" required={false} placeholder="Descripción opcional" value={form.description ?? ""} onChange={(description) => change({ description })} />
            </div>
          </div>
        </details>

        {canReadCosts ? (
          <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
            <summary className="cursor-pointer font-semibold">Costo y proveedor</summary>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <NumberField label="Costo" name="productReferenceCost" required={false} value={money(form.referenceCostCents)} onChange={(value) => change({ referenceCostCents: cents(value) })} />
              <TextField label="Proveedor" name="productSupplier" placeholder="Proveedor" required={false} value={form.supplierName ?? ""} onChange={(supplierName) => change({ supplierName: supplierName || null })} />
            </div>
          </details>
        ) : null}

        <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
          <summary className="cursor-pointer font-semibold">Promoción</summary>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <NumberField label="Precio promocional" name="productPromotionalPrice" required={false} value={money(form.promotionalPriceCents)} onChange={(value) => change({ promotionalPriceCents: cents(value) })} />
            <div className="hidden sm:block" aria-hidden="true" />
            <DateTimeField label="Inicio" value={form.promotionStartsAt} onChange={(promotionStartsAt) => change({ promotionStartsAt })} />
            <DateTimeField label="Fin" value={form.promotionEndsAt} onChange={(promotionEndsAt) => change({ promotionEndsAt })} />
          </div>
        </details>

        {canManageImages ? (
          <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
            <summary className="cursor-pointer font-semibold">Imagen</summary>
            <div className="mt-4 grid gap-3">
              {editing && existingImage ? <button className="w-fit rounded-md border border-[#e2b6b6] px-3 py-2 text-sm font-semibold text-[#9b2c2c]" disabled={saving} onClick={onRemoveImage} type="button">Eliminar imagen</button> : null}
              <input accept="image/jpeg,image/png,image/webp" className="rounded-md border border-[#cdd5ca] p-2" onChange={(event) => onImageChange(event.target.files?.[0] ?? null)} type="file" />
              {image ? <span className="text-xs text-[#5d6b63]">{image.name}</span> : null}
            </div>
          </details>
        ) : null}

        <button className="app-button h-12" disabled={saving} type="submit">
          {saving ? "Guardando..." : editing ? "Guardar cambios" : form.productType === "SERVICE" ? "Crear cargo" : "Crear producto"}
        </button>
      </form>
    </AppDrawer>
  );
}

function categoryOptions(categories: CatalogCategory[]) {
  const sorted = [...categories].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, "es"));
  const roots = sorted.filter((item) => !item.parentId);
  return roots.flatMap((root) => [
    { label: root.name, value: root.id },
    ...sorted.filter((child) => child.parentId === root.id).map((child) => ({ label: `↳ ${child.name}`, value: child.id })),
  ]);
}

function cents(value: string) {
  return value.trim() ? Math.round(Number(value) * 100) : null;
}

function money(value?: number | null) {
  return value == null ? "" : String(value / 100);
}

function DateTimeField({ label, value, onChange }: { label: string; value?: string | null; onChange: (value: string | null) => void }) {
  return <label className="grid gap-1 text-sm"><span className="font-medium">{label}</span><input className="app-input" type="datetime-local" value={localDateTime(value)} onChange={(event) => onChange(event.target.value || null)} /></label>;
}

function localDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}
