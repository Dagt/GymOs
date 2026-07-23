import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductPayload } from "@/lib/api";

type CatalogProductDrawerProps = {
  categories: CatalogCategory[];
  editing: boolean;
  form: ProductPayload;
  open: boolean;
  price: string;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: ProductPayload) => void;
  onPriceChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function CatalogProductDrawer({
  categories,
  editing,
  form,
  open,
  price,
  saving,
  onClose,
  onFormChange,
  onPriceChange,
  onSubmit,
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
          <NumberField label="Precio" name="productPrice" value={price} onChange={onPriceChange} />
          <TextField label="Moneda" name="productCurrency" value={form.currency} onChange={(currency) => change({ currency })} />
          <TextField label="Codigo de barras" name="productBarcode" value={form.barcode ?? ""} onChange={(barcode) => change({ barcode })} />
          <TextField label="Marca" name="productBrand" value={form.brand ?? ""} onChange={(brand) => change({ brand })} />
          <TextField label="Presentacion" name="productPresentation" value={form.presentation ?? ""} onChange={(presentation) => change({ presentation })} />
          <TextField label="Unidad" name="productUnit" value={form.unitOfMeasure ?? ""} onChange={(unitOfMeasure) => change({ unitOfMeasure })} />
        </div>
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
            inventoryTracked: value === "PHYSICAL" ? form.inventoryTracked : false,
          })}
        />
        <SelectField
          label="Categoria"
          name="productCategory"
          options={[{ label: "Sin categoria", value: "" }, ...categories.map(({ id, name }) => ({ label: name, value: id }))]}
          value={form.categoryId ?? ""}
          onChange={(categoryId) => change({ categoryId: categoryId || null })}
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
