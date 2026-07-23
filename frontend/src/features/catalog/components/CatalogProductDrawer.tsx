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
  canReadCosts: boolean;
  editing: boolean;
  form: ProductPayload;
  open: boolean;
  price: string;
  image: File | null;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: ProductPayload) => void;
  onPriceChange: (value: string) => void;
  onImageChange: (file: File | null) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CatalogProductDrawer({
  categories, canReadCosts, editing, form, open, price, image, saving, onClose,
  onFormChange, onPriceChange, onImageChange, onSubmit,
}: CatalogProductDrawerProps) {
  return (
    <AppDrawer
      description="Configura datos comerciales, clasificacion e inventario del elemento vendible."
      onClose={onClose}
      open={open}
      title={editing ? "Editar producto" : "Nuevo producto"}
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="SKU" name="productSku" value={form.sku} onChange={(sku) => change({ sku })} />
          <TextField label="Nombre" name="productName" value={form.name} onChange={(name) => change({ name })} />
          <NumberField label="Precio regular" name="productPrice" value={price} onChange={onPriceChange} />
          <TextField label="Moneda" name="productCurrency" value={form.currency} onChange={(currency) => change({ currency })} />
          {canReadCosts ? (
            <>
              <NumberField
                label="Costo de referencia"
                name="productReferenceCost"
                value={moneyValue(form.referenceCostCents)}
                onChange={(value) => change({ referenceCostCents: cents(value) })}
              />
              <TextField label="Proveedor principal" name="productSupplier" value={form.supplierName ?? ""} onChange={(supplierName) => change({ supplierName })} />
            </>
          ) : null}
          <NumberField
            label="Precio promocional"
            name="productPromotionalPrice"
            value={moneyValue(form.promotionalPriceCents)}
            onChange={(value) => change({ promotionalPriceCents: cents(value) })}
          />
          <TextField label="Codigo de barras" name="productBarcode" value={form.barcode ?? ""} onChange={(barcode) => change({ barcode })} />
          <TextField label="Marca" name="productBrand" value={form.brand ?? ""} onChange={(brand) => change({ brand })} />
          <TextField label="Presentacion" name="productPresentation" value={form.presentation ?? ""} onChange={(presentation) => change({ presentation })} />
          <TextField label="Unidad" name="productUnit" value={form.unitOfMeasure ?? ""} onChange={(unitOfMeasure) => change({ unitOfMeasure })} />
          <NumberField label="Orden" name="productDisplayOrder" value={String(form.displayOrder)} onChange={(value) => change({ displayOrder: Number(value || 0) })} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label="Tipo"
            name="productType"
            options={[
              { label: "Producto fisico", value: "PHYSICAL" },
              { label: "Servicio", value: "SERVICE" },
            ]}
            value={form.productType}
            onChange={(value) => change({
              productType: value as ProductPayload["productType"],
              commercialSubtype: value === "PHYSICAL" ? "GENERAL_PRODUCT" : "GENERAL_SERVICE",
              inventoryTracked: value === "PHYSICAL" ? form.inventoryTracked : false,
            })}
          />
          <SelectField
            label="Subtipo comercial"
            name="productCommercialSubtype"
            options={subtypeOptions.filter((option) => subtypeAllowed(form.productType, option.value))}
            value={form.commercialSubtype}
            onChange={(commercialSubtype) => change({ commercialSubtype: commercialSubtype as ProductPayload["commercialSubtype"] })}
          />
          <SelectField
            label="Categoria"
            name="productCategory"
            options={[{ label: "Sin categoria", value: "" }, ...categories.map(({ id, name }) => ({ label: name, value: id }))]}
            value={form.categoryId ?? ""}
            onChange={(categoryId) => change({ categoryId: categoryId || null })}
          />
          <SelectField
            label="Estado"
            name="productStatus"
            options={[{ label: "Activo", value: "ACTIVE" }, { label: "Inactivo", value: "INACTIVE" }]}
            value={form.status}
            onChange={(status) => change({ status: status as ProductPayload["status"] })}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <DateTimeField label="Inicio de promocion" value={form.promotionStartsAt} onChange={(promotionStartsAt) => change({ promotionStartsAt })} />
          <DateTimeField label="Fin de promocion" value={form.promotionEndsAt} onChange={(promotionEndsAt) => change({ promotionEndsAt })} />
        </div>

        <TextField
          label="Etiquetas (separadas por coma)"
          name="productTags"
          value={form.tags.join(", ")}
          onChange={(value) => change({ tags: value.split(",").map((tag) => tag.trim()).filter(Boolean) })}
        />
        <TextAreaField label="Descripcion" name="productDescription" value={form.description ?? ""} onChange={(description) => change({ description })} />

        <label className="flex items-start gap-3 rounded-md border border-[#cdd5ca] p-3 text-sm">
          <input
            checked={form.inventoryTracked}
            disabled={form.productType !== "PHYSICAL"}
            onChange={(event) => change({ inventoryTracked: event.target.checked })}
            type="checkbox"
          />
          <span>
            <strong className="block">Controlar existencias</strong>
            El POS descontara inventario al completar una venta.
          </span>
        </label>

        <label className="grid gap-2 text-sm font-medium">
          Imagen principal
          <input
            accept="image/jpeg,image/png,image/webp"
            className="rounded-md border border-[#cdd5ca] p-2"
            onChange={(event) => onImageChange(event.target.files?.[0] ?? null)}
            type="file"
          />
          <span className="text-xs font-normal text-[#5d6b63]">
            {image ? image.name : "JPG, PNG o WebP; maximo 5 MB."}
          </span>
        </label>

        <button className="app-button h-11" disabled={saving} type="submit">
          {saving ? "Guardando..." : editing ? "Guardar cambios" : "Crear producto"}
        </button>
      </form>
    </AppDrawer>
  );

  function change(values: Partial<ProductPayload>) {
    onFormChange({ ...form, ...values });
  }
}

function cents(value: string) {
  return value.trim() ? Math.round(Number(value) * 100) : null;
}

function moneyValue(value?: number | null) {
  return value == null ? "" : String(value / 100);
}

function subtypeAllowed(type: ProductPayload["productType"], subtype: string) {
  if (type === "PHYSICAL") return subtype === "GENERAL_PRODUCT";
  return subtype !== "GENERAL_PRODUCT" && subtype !== "MEMBERSHIP";
}

function DateTimeField({ label, value, onChange }: {
  label: string;
  value?: string | null;
  onChange: (value: string | null) => void;
}) {
  return (
    <label className="grid gap-1 text-sm">
      <span className="font-medium">{label}</span>
      <input
        className="h-10 rounded-md border border-[#cdd5ca] px-3"
        type="datetime-local"
        value={localDateTime(value)}
        onChange={(event) => onChange(event.target.value || null)}
      />
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
