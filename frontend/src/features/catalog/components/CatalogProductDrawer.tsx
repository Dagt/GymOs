import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductPayload } from "@/lib/api";
import { CATALOG_UNIT_OPTIONS } from "../catalog-units";

type Props = {
  categories: CatalogCategory[];
  canManageImages: boolean;
  canManageInventory: boolean;
  canReadCosts: boolean;
  editing: boolean;
  editingVariant: boolean;
  existingImage: boolean;
  form: ProductPayload;
  open: boolean;
  price: string;
  reorderPoint: string;
  tagsText: string;
  image: File | null;
  saving: boolean;
  onClose: () => void;
  onFormChange: (form: ProductPayload) => void;
  onPriceChange: (value: string) => void;
  onReorderPointChange: (value: string) => void;
  onTagsTextChange: (value: string) => void;
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
  editingVariant,
  existingImage,
  form,
  open,
  price,
  reorderPoint,
  tagsText,
  image,
  saving,
  onClose,
  onFormChange,
  onPriceChange,
  onReorderPointChange,
  onTagsTextChange,
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
      unitOfMeasure: productType === "PHYSICAL" ? form.unitOfMeasure ?? "PIECE" : null,
      referenceCostCents: productType === "PHYSICAL" ? form.referenceCostCents : null,
      supplierName: productType === "PHYSICAL" ? form.supplierName : null,
      familyId: productType === "PHYSICAL" ? form.familyId : null,
      variantName: productType === "PHYSICAL" ? form.variantName : null,
      barcodes: productType === "PHYSICAL" ? form.barcodes : [],
      barcode: productType === "PHYSICAL" ? form.barcode : null,
    });
  }

  function changePrimaryBarcode(value: string) {
    const aliases = form.barcodes.slice(1);
    const barcodes = value || aliases.length ? [value, ...aliases] : [];
    change({ barcode: value.trim() || null, barcodes });
  }

  function addAlternateBarcode() {
    const current = form.barcodes.length ? form.barcodes : [""];
    if (current.length >= 20) return;
    change({ barcodes: [...current, ""] });
  }

  function changeAlternateBarcode(index: number, value: string) {
    change({ barcodes: form.barcodes.map((barcode, currentIndex) => currentIndex === index ? value : barcode) });
  }

  function removeAlternateBarcode(index: number) {
    change({ barcodes: form.barcodes.filter((_, currentIndex) => currentIndex !== index) });
  }

  return (
    <AppDrawer onClose={onClose} open={open} title={editing ? editingVariant ? "Editar variante" : "Editar artículo o cargo" : "Nuevo artículo o cargo"}>
      <form className="grid gap-4" onSubmit={onSubmit}>
        <section className="grid gap-4 rounded-xl border border-[#d8ddd2] bg-white p-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Nombre" name="productName" placeholder={form.productType === "SERVICE" ? "Scoop de preentreno" : "Agua 600 ml"} value={form.name} onChange={(name) => change({ name })} />
            <TextField label="SKU" name="productSku" placeholder={form.productType === "SERVICE" ? "SCOOP-PRE" : "AGUA-600"} value={form.sku} onChange={(sku) => change({ sku })} />
            <NumberField label="Precio" name="productPrice" step="0.01" value={price} onChange={onPriceChange} />
            <SelectField label="Tipo" name="productType" options={[{ label: "Producto físico", value: "PHYSICAL" }, { label: "Cargo de caja", value: "SERVICE" }]} value={form.productType} onChange={changeType} />
            {form.productType === "PHYSICAL" ? (
              <div className="grid content-start gap-1.5">
                <TextField label="Código de barras" name="productBarcode" placeholder="Escanea o captura" required={false} value={form.barcodes[0] ?? ""} onChange={changePrimaryBarcode} />
                {form.barcodes.slice(1).map((barcode, aliasIndex) => {
                  const barcodeIndex = aliasIndex + 1;
                  return <div className="flex items-end gap-2" key={barcodeIndex}>
                    <div className="min-w-0 flex-1"><TextField label={`Código alternativo ${barcodeIndex}`} name={`productBarcodeAlias-${barcodeIndex}`} required={false} value={barcode} onChange={(value) => changeAlternateBarcode(barcodeIndex, value)} /></div>
                    <button aria-label={`Quitar código alternativo ${barcodeIndex}`} className="mb-2 text-xs font-semibold text-[#9b2c2c]" onClick={() => removeAlternateBarcode(barcodeIndex)} type="button">Quitar</button>
                  </div>;
                })}
                {form.barcodes.length < 20 ? <button className="w-fit text-xs font-semibold text-[var(--brand-primary)]" onClick={addAlternateBarcode} type="button">+ Código alternativo</button> : null}
              </div>
            ) : null}
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
                <TextField label="Marca" name="productBrand" placeholder="Marca" required={false} value={form.brand ?? ""} onChange={(brand) => change({ brand: brand || null })} />
                {editingVariant ? <TextField help="Esta presentación pertenece a un producto con variantes." label="Sabor o variante" name="productVariantName" placeholder="Frutos rojos" required={false} value={form.variantName ?? ""} onChange={(variantName) => change({ variantName: variantName || null })} /> : null}
                <TextField label="Presentación" name="productPresentation" placeholder="Bote de 30 porciones" required={false} value={form.presentation ?? ""} onChange={(presentation) => change({ presentation: presentation || null })} />
                <SelectField
                  label="Unidad"
                  name="productUnit"
                  options={[...CATALOG_UNIT_OPTIONS]}
                  value={form.unitOfMeasure ?? "PIECE"}
                  onChange={(unitOfMeasure) => change({ unitOfMeasure })}
                />
              </>
            ) : null}
            <TextField label="Etiquetas" name="productTags" placeholder="energía, cafeína, preentreno" required={false} value={tagsText} onChange={onTagsTextChange} />
            <div className="sm:col-span-2">
              <TextAreaField label="Descripción" name="productDescription" required={false} placeholder="Descripción opcional" value={form.description ?? ""} onChange={(description) => change({ description })} />
            </div>
          </div>
        </details>

        {canReadCosts && form.productType === "PHYSICAL" ? (
          <details className="rounded-xl border border-[#d8ddd2] bg-white p-4">
            <summary className="cursor-pointer font-semibold">Costo y proveedor</summary>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <NumberField label="Costo" name="productReferenceCost" required={false} step="0.01" value={money(form.referenceCostCents)} onChange={(value) => change({ referenceCostCents: cents(value) })} />
              <TextField label="Proveedor" name="productSupplier" placeholder="Proveedor" required={false} value={form.supplierName ?? ""} onChange={(supplierName) => change({ supplierName: supplierName || null })} />
            </div>
          </details>
        ) : null}

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
