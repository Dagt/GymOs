import { useState, type FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductFamilySummary, ProductVariantBatchPayload } from "@/lib/api";
import { CATALOG_UNIT_OPTIONS } from "../catalog-units";

type VariantDraft = {
  variantName: string;
  sku: string;
  presentation: string;
  price: string;
  cost: string;
  barcodes: string;
};

type Props = {
  categories: CatalogCategory[];
  families: ProductFamilySummary[];
  canManageInventory: boolean;
  canReadCosts: boolean;
  open: boolean;
  saving: boolean;
  onClose: () => void;
  onSubmit: (payload: ProductVariantBatchPayload, reorderPoint: number) => Promise<void>;
};

const blankVariant = (): VariantDraft => ({
  variantName: "",
  sku: "",
  presentation: "",
  price: "",
  cost: "",
  barcodes: "",
});

export function CatalogVariantBatchDrawer({
  categories,
  families,
  canManageInventory,
  canReadCosts,
  open,
  saving,
  onClose,
  onSubmit,
}: Props) {
  const [familyId, setFamilyId] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [brand, setBrand] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [unitOfMeasure, setUnitOfMeasure] = useState("PIECE");
  const [inventoryTracked, setInventoryTracked] = useState(true);
  const [reorderPoint, setReorderPoint] = useState("0");
  const [tags, setTags] = useState("");
  const [variants, setVariants] = useState<VariantDraft[]>([blankVariant(), blankVariant()]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedReorderPoint = Number(reorderPoint || "0");
    if (!Number.isFinite(parsedReorderPoint) || parsedReorderPoint < 0) return;
    await onSubmit({
      familyId: familyId || null,
      familyName: familyId ? null : familyName.trim(),
      brand: brand.trim() || null,
      currency: "MXN",
      taxable: true,
      status: "ACTIVE",
      unitOfMeasure,
      inventoryTracked,
      displayOrder: 0,
      categoryId: categoryId || null,
      tags: unique(tags.split(",").map((tag) => tag.trim()).filter(Boolean)),
      variants: variants.map((variant) => ({
        variantName: variant.variantName.trim(),
        sku: variant.sku.trim(),
        presentation: variant.presentation.trim() || null,
        priceCents: Math.round(Number(variant.price || "0") * 100),
        barcodes: unique(variant.barcodes.split(/\r?\n|,/).map((barcode) => barcode.trim()).filter(Boolean)),
        referenceCostCents: variant.cost.trim() ? Math.round(Number(variant.cost) * 100) : null,
      })),
    }, parsedReorderPoint);
  }

  function updateVariant(index: number, values: Partial<VariantDraft>) {
    setVariants((current) => current.map((variant, currentIndex) => currentIndex === index ? { ...variant, ...values } : variant));
  }

  return (
    <AppDrawer onClose={onClose} open={open} title="Producto con variantes">
      <form className="grid gap-4" onSubmit={submit}>
        <section className="grid gap-4 rounded-xl border border-[var(--brand-line)] bg-white p-4">
          <div>
            <p className="font-semibold">Familia comercial</p>
            <p className="mt-1 text-xs text-[var(--brand-muted)]">Captura el producto una vez y agrega abajo sus sabores o presentaciones vendibles.</p>
          </div>
          <SelectField
            label="Usar familia"
            name="variantFamily"
            required={false}
            options={[{ label: "Crear una familia nueva", value: "" }, ...families.map((family) => ({ label: `${family.name}${family.brand ? ` · ${family.brand}` : ""}`, value: family.id }))]}
            value={familyId}
            onChange={(value) => {
              setFamilyId(value);
              const family = families.find((item) => item.id === value);
              if (family) setBrand(family.brand ?? "");
            }}
          />
          {!familyId ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Nombre del producto" name="variantFamilyName" placeholder="Psychotic" value={familyName} onChange={setFamilyName} />
              <TextField label="Marca" name="variantBrand" placeholder="Insane Labz" required={false} value={brand} onChange={setBrand} />
            </div>
          ) : null}
        </section>

        <details className="rounded-xl border border-[var(--brand-line)] bg-white p-4">
          <summary className="cursor-pointer text-sm font-semibold">Configuración opcional</summary>
          <p className="mt-1 text-xs text-[var(--brand-muted)]">Categoría, unidad, inventario mínimo y etiquetas se aplican a todas las variantes.</p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <SelectField label="Categoría" name="variantCategory" required={false} options={[{ label: "Sin categoría", value: "" }, ...categoryOptions(categories)]} value={categoryId} onChange={setCategoryId} />
            <SelectField label="Unidad" name="variantUnit" options={[...CATALOG_UNIT_OPTIONS]} value={unitOfMeasure} onChange={setUnitOfMeasure} />
            <TextField label="Etiquetas" name="variantTags" placeholder="preentreno, energía" required={false} value={tags} onChange={setTags} />
            {canManageInventory && inventoryTracked ? <NumberField label="Stock mínimo común" min="0" name="variantReorderPoint" value={reorderPoint} onChange={setReorderPoint} /> : null}
          </div>
          {canManageInventory ? (
            <label className="mt-4 flex h-11 items-center gap-3 rounded-md border border-[var(--brand-line)] px-3 text-sm font-medium">
              <input checked={inventoryTracked} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => setInventoryTracked(event.target.checked)} type="checkbox" />
              Controlar inventario de cada variante
            </label>
          ) : null}
        </details>

        <section className="grid gap-3">
          <div className="flex items-center justify-between gap-3">
            <div><p className="font-semibold">Variantes vendibles</p><p className="text-xs text-[var(--brand-muted)]">Cada fila conserva su propio SKU, precio, costo y existencias.</p></div>
            <button className="app-button-secondary h-9 px-3" onClick={() => setVariants((current) => [...current, blankVariant()])} type="button">Agregar variante</button>
          </div>
          {variants.map((variant, index) => (
            <article className="grid gap-4 rounded-xl border border-[var(--brand-line)] bg-white p-4" key={index}>
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-semibold">Variante {index + 1}</p>
                {variants.length > 1 ? <button className="text-xs font-semibold text-[#9b2c2c]" onClick={() => setVariants((current) => current.filter((_, currentIndex) => currentIndex !== index))} type="button">Quitar</button> : null}
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField label="Sabor o variante" name={`variantName-${index}`} placeholder="Frutos rojos" value={variant.variantName} onChange={(variantName) => updateVariant(index, { variantName })} />
                <TextField label="SKU" name={`variantSku-${index}`} placeholder="PSY-ROJO" value={variant.sku} onChange={(sku) => updateVariant(index, { sku })} />
                <TextField label="Presentación" name={`variantPresentation-${index}`} placeholder="Bote de 35 porciones" required={false} value={variant.presentation} onChange={(presentation) => updateVariant(index, { presentation })} />
                <NumberField label="Precio" name={`variantPrice-${index}`} step="0.01" value={variant.price} onChange={(price) => updateVariant(index, { price })} />
                {canReadCosts ? <NumberField label="Costo" name={`variantCost-${index}`} required={false} step="0.01" value={variant.cost} onChange={(cost) => updateVariant(index, { cost })} /> : null}
                <div className={canReadCosts ? "" : "sm:col-span-2"}>
                  <TextAreaField help="Uno por línea. Todos identificarán esta variante en caja." label="Códigos de barras" name={`variantBarcodes-${index}`} placeholder={"7501234567890\n7501234567891"} required={false} rows={2} value={variant.barcodes} onChange={(barcodes) => updateVariant(index, { barcodes })} />
                </div>
              </div>
            </article>
          ))}
        </section>

        <button className="app-button h-12" disabled={saving} type="submit">{saving ? "Creando variantes..." : `Crear ${variants.length} variantes`}</button>
      </form>
    </AppDrawer>
  );
}

function categoryOptions(categories: CatalogCategory[]) {
  const sorted = categories.filter((category) => category.status === "ACTIVE").sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, "es"));
  const roots = sorted.filter((item) => !item.parentId);
  return roots.flatMap((root) => [
    { label: root.name, value: root.id },
    ...sorted.filter((child) => child.parentId === root.id).map((child) => ({ label: `↳ ${child.name}`, value: child.id })),
  ]);
}

function unique(values: string[]) {
  return [...new Set(values)];
}
