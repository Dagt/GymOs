import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductPayload } from "@/lib/api";

const subtypeOptions = [
  { label: "Producto general", value: "GENERAL_PRODUCT" },
  { label: "Servicio general", value: "GENERAL_SERVICE" },
  { label: "Pase de visita", value: "VISIT_PASS" },
  { label: "Reserva", value: "BOOKING" },
  { label: "Rutina", value: "ROUTINE" },
  { label: "Consulta", value: "CONSULTATION" },
  { label: "Servicio wellness", value: "WELLNESS_SERVICE" },
];

type CatalogProductDrawerProps = {
  categories: CatalogCategory[];
  canManageImages: boolean;
  canReadCosts: boolean;
  editing: boolean;
  existingImage: boolean;
  form: ProductPayload;
  open: boolean;
  price: string;
  image: File | null;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: ProductPayload) => void;
  onPriceChange: (value: string) => void;
  onImageChange: (file: File | null) => void;
  onRemoveImage: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CatalogProductDrawer({
  categories, canManageImages, canReadCosts, editing, existingImage, form, open, price, image, saving,
  onClose, onFormChange, onPriceChange, onImageChange, onRemoveImage, onSubmit,
}: CatalogProductDrawerProps) {
  const availableCategories = categories.filter((category) =>
    category.status === "ACTIVE" || category.id === form.categoryId,
  );

  function change(values: Partial<ProductPayload>) {
    onFormChange({ ...form, ...values });
  }

  return (
    <AppDrawer
      description="Captura primero lo indispensable. Los datos comerciales avanzados son opcionales."
      onClose={onClose}
      open={open}
      title={editing ? "Editar producto o servicio" : "Nuevo producto o servicio"}
    >
      <form className="grid gap-5" onSubmit={onSubmit}>
        <section className="grid gap-4 rounded-xl border border-[#d8ddd2] bg-white p-4">
          <div>
            <p className="text-sm font-semibold text-[#111815]">Datos basicos</p>
            <p className="mt-1 text-xs text-[#5d6b63]">Sólo nombre, SKU, precio y tipo son necesarios para empezar.</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Nombre" name="productName" value={form.name}
              placeholder="Ej. Agua 600 ml" onChange={(name) => change({ name })} />
            <TextField label="SKU" name="productSku" value={form.sku}
              help="Código interno único. Ej. AGUA-600" onChange={(sku) => change({ sku })} />
            <NumberField label="Precio" name="productPrice" value={price} onChange={onPriceChange} />
            <SelectField label="Tipo" name="productType"
              options={[{ label: "Producto fisico", value: "PHYSICAL" }, { label: "Servicio", value: "SERVICE" }]}
              value={form.productType}
              onChange={(value) => change({
                productType: value as ProductPayload["productType"],
                commercialSubtype: value === "PHYSICAL" ? "GENERAL_PRODUCT" : "GENERAL_SERVICE",
                inventoryTracked: value === "PHYSICAL" ? form.inventoryTracked : false,
              })} />
            <SelectField label="Categoria" name="productCategory" required={false}
              options={[{ label: "Sin categoria", value: "" }, ...availableCategories.map(({ id, name, status }) => ({
                label: status === "ACTIVE" ? name : `${name} (inactiva)`, value: id,
              }))]}
              value={form.categoryId ?? ""} onChange={(categoryId) => change({ categoryId: categoryId || null })} />
          </div>

          <label className="flex items-start gap-3 rounded-md border border-[#cdd5ca] bg-[#f8faf7] p-3 text-sm">
            <input checked={form.inventoryTracked} disabled={form.productType !== "PHYSICAL"}
              onChange={(event) => change({ inventoryTracked: event.target.checked })} type="checkbox" />
            <span><strong className="block">Controlar existencias</strong>
              {form.productType === "PHYSICAL"
                ? "El POS descontara inventario al completar una venta."
                : "Los servicios no manejan existencias."}
            </span>
          </label>
        </section>

        <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
          <summary className="cursor-pointer font-semibold text-[#111815]">Datos opcionales y avanzados</summary>
          <p className="mt-2 text-xs text-[#5d6b63]">Úsalos sólo cuando aporten valor operativo o comercial.</p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <TextField label="Codigo de barras" name="productBarcode" required={false}
              value={form.barcode ?? ""} onChange={(barcode) => change({ barcode: barcode || null })} />
            <TextField label="Marca" name="productBrand" required={false}
              value={form.brand ?? ""} onChange={(brand) => change({ brand: brand || null })} />
            <TextField label="Presentacion" name="productPresentation" required={false}
              value={form.presentation ?? ""} onChange={(presentation) => change({ presentation: presentation || null })} />
            <TextField label="Unidad" name="productUnit" required={false}
              value={form.unitOfMeasure ?? ""} onChange={(unitOfMeasure) => change({ unitOfMeasure: unitOfMeasure || null })} />
            <SelectField label="Subtipo comercial" name="productCommercialSubtype"
              options={subtypeOptions.filter((option) => subtypeAllowed(form.productType, option.value))}
              value={form.commercialSubtype}
              onChange={(commercialSubtype) => change({ commercialSubtype: commercialSubtype as ProductPayload["commercialSubtype"] })} />
            <SelectField label="Estado" name="productStatus"
              options={[{ label: "Activo", value: "ACTIVE" }, { label: "Inactivo", value: "INACTIVE" }]}
              value={form.status} onChange={(status) => change({ status: status as ProductPayload["status"] })} />
            <TextField label="Moneda" name="productCurrency" value={form.currency}
              onChange={(currency) => change({ currency })} />
            <NumberField label="Orden de aparicion" name="productDisplayOrder" required={false}
              value={String(form.displayOrder)} onChange={(value) => change({ displayOrder: Number(value || 0) })} />
            {canReadCosts ? (
              <>
                <NumberField label="Costo de referencia" name="productReferenceCost" required={false}
                  value={moneyValue(form.referenceCostCents)}
                  onChange={(value) => change({ referenceCostCents: cents(value) })} />
                <TextField label="Proveedor principal" name="productSupplier" required={false}
                  value={form.supplierName ?? ""} onChange={(supplierName) => change({ supplierName: supplierName || null })} />
              </>
            ) : null}
            <NumberField label="Precio promocional" name="productPromotionalPrice" required={false}
              value={moneyValue(form.promotionalPriceCents)}
              onChange={(value) => change({ promotionalPriceCents: cents(value) })} />
            <DateTimeField label="Inicio de promocion" value={form.promotionStartsAt}
              onChange={(promotionStartsAt) => change({ promotionStartsAt })} />
            <DateTimeField label="Fin de promocion" value={form.promotionEndsAt}
              onChange={(promotionEndsAt) => change({ promotionEndsAt })} />
          </div>
          <div className="mt-4 grid gap-4">
            <TextField label="Etiquetas" name="productTags" required={false}
              help="Separadas por coma." value={form.tags.join(", ")}
              onChange={(value) => change({ tags: value.split(",").map((tag) => tag.trim()).filter(Boolean) })} />
            <TextAreaField label="Descripcion" name="productDescription" value={form.description ?? ""}
              onChange={(description) => change({ description })} />
          </div>
        </details>

        {canManageImages ? (
          <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
            <summary className="cursor-pointer font-semibold text-[#111815]">Imagen del producto</summary>
            <div className="mt-4 grid gap-3">
              <span className="text-xs text-[#5d6b63]">
                {existingImage ? "El producto tiene una imagen almacenada." : "Todavia no hay una imagen almacenada."}
              </span>
              {editing && existingImage ? (
                <button className="w-fit rounded-md border border-[#e2b6b6] px-3 py-2 text-sm font-semibold text-[#9b2c2c]"
                  disabled={saving} onClick={onRemoveImage} type="button">Eliminar imagen</button>
              ) : null}
              <input accept="image/jpeg,image/png,image/webp" className="rounded-md border border-[#cdd5ca] p-2"
                onChange={(event) => onImageChange(event.target.files?.[0] ?? null)} type="file" />
              <span className="text-xs text-[#5d6b63]">{image ? image.name : "JPG, PNG o WebP; maximo 5 MB."}</span>
            </div>
          </details>
        ) : null}

        <button className="app-button h-11" disabled={saving} type="submit">
          {saving ? "Guardando..." : editing ? "Guardar cambios" : "Crear producto"}
        </button>
      </form>
    </AppDrawer>
  );
}

function cents(value: string) { return value.trim() ? Math.round(Number(value) * 100) : null; }
function moneyValue(value?: number | null) { return value == null ? "" : String(value / 100); }
function subtypeAllowed(type: ProductPayload["productType"], subtype: string) {
  if (type === "PHYSICAL") return subtype === "GENERAL_PRODUCT";
  return subtype !== "GENERAL_PRODUCT" && subtype !== "MEMBERSHIP";
}

function DateTimeField({ label, value, onChange }: { label: string; value?: string | null; onChange: (value: string | null) => void; }) {
  return (
    <label className="grid gap-1 text-sm">
      <span className="font-medium">{label}</span>
      <input className="h-10 rounded-md border border-[#cdd5ca] px-3" type="datetime-local"
        value={localDateTime(value)} onChange={(event) => onChange(event.target.value || null)} />
    </label>
  );
}

function localDateTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(0, 16);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
