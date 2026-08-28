import { useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductFamilySummary, ProductVariantBatchPayload } from "@/lib/api";
import { CATALOG_UNIT_OPTIONS } from "../catalog-units";

type VariantDraft = {
  variantName: string;
  sku: string;
  skuManuallyEdited: boolean;
  primaryBarcode: string;
  additionalBarcodes: string[];
  presentationOverride: string;
  priceOverride: string;
  costOverride: string;
};

type Props = {
  categories: CatalogCategory[];
  families: ProductFamilySummary[];
  canManageInventory: boolean;
  canReadCosts: boolean;
  initialFamilyId: string | null;
  open: boolean;
  saving: boolean;
  onClose: () => void;
  onSubmit: (payload: ProductVariantBatchPayload) => Promise<void>;
};

const blankVariant = (): VariantDraft => ({
  variantName: "",
  sku: "",
  skuManuallyEdited: false,
  primaryBarcode: "",
  additionalBarcodes: [],
  presentationOverride: "",
  priceOverride: "",
  costOverride: "",
});

export function CatalogVariantBatchDrawer({
  categories,
  families,
  canManageInventory,
  canReadCosts,
  initialFamilyId,
  open,
  saving,
  onClose,
  onSubmit,
}: Props) {
  const [familyId, setFamilyId] = useState(initialFamilyId ?? "");
  const [usingExistingProduct, setUsingExistingProduct] = useState(Boolean(initialFamilyId));
  const [familyName, setFamilyName] = useState("");
  const [brand, setBrand] = useState("");
  const [commonPresentation, setCommonPresentation] = useState("");
  const [commonPrice, setCommonPrice] = useState("");
  const [commonCost, setCommonCost] = useState("");
  const [supplierName, setSupplierName] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [unitOfMeasure, setUnitOfMeasure] = useState("PIECE");
  const [inventoryTracked, setInventoryTracked] = useState(true);
  const [reorderPoint, setReorderPoint] = useState("0");
  const [tags, setTags] = useState("");
  const [status, setStatus] = useState<ProductVariantBatchPayload["status"]>("ACTIVE");
  const [skuPrefix, setSkuPrefix] = useState("");
  const [skuPrefixManuallyEdited, setSkuPrefixManuallyEdited] = useState(false);
  const [variants, setVariants] = useState<VariantDraft[]>([blankVariant()]);
  const [editingVariantIndex, setEditingVariantIndex] = useState<number | null>(null);
  const addVariantButton = useRef<HTMLButtonElement>(null);
  const selectedFamily = families.find((family) => family.id === familyId);
  const detailIndex = editingVariantIndex ?? -1;
  const editingVariant = variants[detailIndex] ?? null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedReorderPoint = Number(reorderPoint || "0");
    if (!Number.isFinite(parsedReorderPoint) || parsedReorderPoint < 0) return;
    await onSubmit({
      familyId: usingExistingProduct ? familyId || null : null,
      familyName: usingExistingProduct ? null : familyName.trim(),
      brand: usingExistingProduct ? null : brand.trim() || null,
      commonPresentation: commonPresentation.trim() || null,
      commonPriceCents: centsRequired(commonPrice),
      commonReferenceCostCents: canReadCosts ? centsOptional(commonCost) : null,
      currency: "MXN",
      taxable: true,
      status,
      unitOfMeasure,
      inventoryTracked,
      commonReorderPoint: canManageInventory && inventoryTracked ? parsedReorderPoint : null,
      displayOrder: 0,
      supplierName: canReadCosts ? supplierName.trim() || null : null,
      categoryId: categoryId || null,
      tags: unique(tags.split(",").map((tag) => tag.trim()).filter(Boolean)),
      variants: variants.map((variant) => ({
        variantName: variant.variantName.trim(),
        sku: variant.sku.trim(),
        primaryBarcode: variant.primaryBarcode.trim() || null,
        additionalBarcodes: unique(variant.additionalBarcodes.map((barcode) => barcode.trim()).filter(Boolean)),
        presentationOverride: variant.presentationOverride.trim() || null,
        priceCentsOverride: centsOptional(variant.priceOverride),
        referenceCostCentsOverride: canReadCosts ? centsOptional(variant.costOverride) : null,
      })),
    });
  }

  function updateVariant(index: number, values: Partial<VariantDraft>) {
    setVariants((current) => current.map((variant, currentIndex) => currentIndex === index ? { ...variant, ...values } : variant));
  }

  function updateVariantName(index: number, variantName: string) {
    setVariants((current) => current.map((variant, currentIndex) => {
      if (currentIndex !== index) return variant;
      return {
        ...variant,
        variantName,
        sku: variant.skuManuallyEdited ? variant.sku : uniqueGeneratedSku(skuPrefix, variantName, current, index),
      };
    }));
  }

  function applySkuPrefix(value: string, manual: boolean) {
    const normalized = normalizeSkuPart(value, 12);
    setSkuPrefix(normalized);
    if (manual) setSkuPrefixManuallyEdited(true);
    setVariants((current) => regenerateAutomaticSkus(current, normalized));
  }

  function changeFamilyName(value: string) {
    setFamilyName(value);
    if (!skuPrefixManuallyEdited) applySkuPrefix(suggestSkuPrefix(value), false);
  }

  function selectFamily(value: string) {
    setFamilyId(value);
    const family = families.find((item) => item.id === value);
    if (!family) {
      setFamilyName("");
      setBrand("");
      if (!skuPrefixManuallyEdited) applySkuPrefix("", false);
      return;
    }
    setFamilyName(family.name);
    setBrand(family.brand ?? "");
    if (!skuPrefixManuallyEdited) applySkuPrefix(suggestSkuPrefix(family.name), false);
  }

  function createNewBaseProduct() {
    setUsingExistingProduct(false);
    setFamilyId("");
    setFamilyName("");
    setBrand("");
    if (!skuPrefixManuallyEdited) applySkuPrefix("", false);
  }

  function addToExistingProduct() {
    setUsingExistingProduct(true);
    setFamilyName("");
    setBrand("");
  }

  function addVariant(focus = false) {
    const nextIndex = variants.length;
    setEditingVariantIndex(null);
    setVariants((current) => [...current, blankVariant()]);
    if (focus) window.setTimeout(() => document.getElementById(`variantName-${nextIndex}`)?.focus(), 0);
  }

  function removeVariant(index: number) {
    setEditingVariantIndex(null);
    setVariants((current) => current.filter((_, currentIndex) => currentIndex !== index));
  }

  function handleBarcodeEnter(event: KeyboardEvent<HTMLInputElement>, index: number) {
    if (event.key !== "Enter" && event.key !== "NumpadEnter") return;
    event.preventDefault();
    if (index + 1 < variants.length) {
      document.getElementById(`variantName-${index + 1}`)?.focus();
    } else {
      addVariantButton.current?.focus();
    }
  }

  return (
    <AppDrawer onClose={onClose} open={open} title={usingExistingProduct ? "Agregar variantes" : "Crear producto con variantes"} wide>
      <form className="grid gap-5" onSubmit={submit}>
        <section className="grid gap-4 rounded-xl border border-[var(--brand-line)] bg-white p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="font-semibold">{usingExistingProduct ? "Producto base existente" : "Producto base nuevo"}</p>
              <p className="mt-1 max-w-2xl text-xs text-[var(--brand-muted)]">{usingExistingProduct
                ? "Selecciona el producto al que agregarás sabores o presentaciones."
                : "Captura los datos una vez. GymFort organizará automáticamente sus sabores o presentaciones; no necesitas administrar grupos por separado."}</p>
            </div>
            {usingExistingProduct ? (
              <button className="text-xs font-semibold text-[var(--brand-primary)]" onClick={createNewBaseProduct} type="button">Crear producto base nuevo</button>
            ) : families.length ? (
              <button className="text-xs font-semibold text-[var(--brand-primary)]" onClick={addToExistingProduct} type="button">Agregar variantes a producto existente</button>
            ) : null}
          </div>
          {usingExistingProduct ? (
            <>
              <SelectField
                label="Producto base"
                name="variantFamily"
                options={[{ label: "Selecciona un producto", value: "" }, ...families.map((family) => ({ label: `${family.name}${family.brand ? ` · ${family.brand}` : ""} (${family.totalVariants} ${family.totalVariants === 1 ? "variante" : "variantes"})`, value: family.id }))]}
                value={familyId}
                onChange={selectFamily}
              />
              {selectedFamily ? <div className="rounded-lg bg-[var(--brand-surface)] px-3 py-2 text-sm"><span className="font-semibold">{selectedFamily.name}</span>{selectedFamily.brand ? ` · ${selectedFamily.brand}` : ""}<span className="ml-2 text-xs text-[var(--brand-muted)]">Las nuevas variantes quedarán dentro de este producto.</span></div> : null}
            </>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Nombre del producto" name="variantFamilyName" placeholder="Psychotic" value={familyName} onChange={changeFamilyName} />
              <TextField label="Marca" name="variantBrand" placeholder="Insane Labz" required={false} value={brand} onChange={setBrand} />
            </div>
          )}
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="Presentación común" name="variantCommonPresentation" placeholder="Bote de 35 scoops" required={false} value={commonPresentation} onChange={setCommonPresentation} />
            <NumberField label="Precio común" name="variantCommonPrice" step="0.01" value={commonPrice} onChange={setCommonPrice} />
            {canReadCosts ? <NumberField label="Costo común" name="variantCommonCost" required={false} step="0.01" value={commonCost} onChange={setCommonCost} /> : null}
          </div>
          {canManageInventory ? (
            <div className="flex flex-wrap items-end gap-3 rounded-lg bg-[var(--brand-surface)] px-3 py-2">
              <Check checked={inventoryTracked} label="Controlar inventario" onChange={setInventoryTracked} />
              {inventoryTracked ? <div className="w-36"><CompactOverrideField label="Stock mínimo" name="variantReorderPoint" placeholder="0" type="number" value={reorderPoint} onChange={setReorderPoint} /></div> : null}
            </div>
          ) : null}
          <details className="rounded-lg border border-[var(--brand-line)] px-3 py-2">
            <summary className="cursor-pointer text-sm font-semibold text-[var(--brand-primary)]">Más datos del producto</summary>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              {canReadCosts ? <TextField label="Proveedor" name="variantSupplier" placeholder="Proveedor" required={false} value={supplierName} onChange={setSupplierName} /> : null}
              <SelectField label="Categoría" name="variantCategory" required={false} options={[{ label: "Sin categoría", value: "" }, ...categoryOptions(categories)]} value={categoryId} onChange={setCategoryId} />
              <SelectField label="Unidad" name="variantUnit" options={[...CATALOG_UNIT_OPTIONS]} value={unitOfMeasure} onChange={setUnitOfMeasure} />
              <TextField label="Etiquetas" name="variantTags" placeholder="preentreno, energía" required={false} value={tags} onChange={setTags} />
              <TextField label="Prefijo SKU" name="variantSkuPrefix" placeholder="PSY" required={false} value={skuPrefix} onChange={(value) => applySkuPrefix(value, true)} />
              <SelectField label="Estado inicial" name="variantStatus" options={[{ label: "Activo", value: "ACTIVE" }, { label: "Inactivo", value: "INACTIVE" }]} value={status} onChange={(value) => setStatus(value as ProductVariantBatchPayload["status"])} />
            </div>
          </details>
        </section>

        <section className="grid gap-3">
          {editingVariant ? (
            <div className="overflow-hidden rounded-xl border border-[var(--brand-line)] bg-white" data-testid="variant-detail-editor">
              <div className="flex items-center justify-between gap-3 border-b border-[var(--brand-line)] px-3 py-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold">Detalles de variante {detailIndex + 1}</p>
                  <p className="truncate text-xs text-[var(--brand-muted)]">{editingVariant.variantName || "Variante sin nombre"}</p>
                </div>
                <button className="text-xs font-semibold text-[var(--brand-primary)]" onClick={() => setEditingVariantIndex(null)} type="button">Volver a variantes</button>
              </div>
              <div className="grid gap-3 bg-[var(--brand-surface)] px-3 py-3 sm:grid-cols-3">
                <CompactOverrideField label="Presentación / tamaño" name={`variantPresentation-${detailIndex}`} placeholder={commonPresentation ? `Común: ${commonPresentation}` : "Ej. Bote de 60 porciones"} value={editingVariant.presentationOverride} onChange={(presentationOverride) => updateVariant(detailIndex, { presentationOverride })} />
                <CompactOverrideField inputMode="decimal" label="Precio" name={`variantPrice-${detailIndex}`} placeholder={commonPrice ? `Común: $${commonPrice}` : "Precio especial"} type="number" value={editingVariant.priceOverride} onChange={(priceOverride) => updateVariant(detailIndex, { priceOverride })} />
                {canReadCosts ? <CompactOverrideField inputMode="decimal" label="Costo" name={`variantCost-${detailIndex}`} placeholder={commonCost ? `Común: $${commonCost}` : "Costo especial"} type="number" value={editingVariant.costOverride} onChange={(costOverride) => updateVariant(detailIndex, { costOverride })} /> : null}
              </div>
              <details className="border-t border-[var(--brand-line)] px-3 py-2">
                <summary className="cursor-pointer text-xs font-semibold text-[var(--brand-primary)]">Códigos alternativos ({editingVariant.additionalBarcodes.length})</summary>
                <div className="mt-2 grid gap-2">
                  {editingVariant.additionalBarcodes.map((barcode, aliasIndex) => (
                    <div className="flex items-end gap-2" key={aliasIndex}>
                      <div className="min-w-0 flex-1"><CompactField label={`Código alternativo ${aliasIndex + 1}`} name={`variantAlias-${detailIndex}-${aliasIndex}`} required={false} value={barcode} onChange={(value) => updateVariant(detailIndex, { additionalBarcodes: editingVariant.additionalBarcodes.map((item, currentIndex) => currentIndex === aliasIndex ? value : item) })} /></div>
                      <button aria-label={`Quitar código alternativo ${aliasIndex + 1}`} className="mb-2 text-xs font-semibold text-[#9b2c2c]" onClick={() => updateVariant(detailIndex, { additionalBarcodes: editingVariant.additionalBarcodes.filter((_, currentIndex) => currentIndex !== aliasIndex) })} type="button">Quitar</button>
                    </div>
                  ))}
                  {editingVariant.additionalBarcodes.length < 19 && editingVariant.additionalBarcodes.every((barcode) => barcode.trim()) ? <button className="w-fit text-xs font-semibold text-[var(--brand-primary)]" onClick={() => updateVariant(detailIndex, { additionalBarcodes: [...editingVariant.additionalBarcodes, ""] })} type="button">+ Código alternativo</button> : null}
                </div>
              </details>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between gap-3">
                <p className="font-semibold">Variantes <span className="text-xs font-normal text-[var(--brand-muted)]">({variants.length})</span></p>
                <button ref={addVariantButton} className="text-xs font-semibold text-[var(--brand-primary)]" onClick={() => addVariant(true)} type="button">+ Agregar</button>
              </div>
              <div className="grid gap-1.5">
                {variants.map((variant, index) => (
                  <article className="grid items-center gap-2 rounded-lg border border-[var(--brand-line)] bg-white p-2 md:grid-cols-[1.75rem_minmax(0,1.15fr)_minmax(0,1fr)_minmax(0,.75fr)_auto]" data-testid={`variant-row-${index}`} key={index}>
                    <span className="flex h-7 w-7 items-center justify-center rounded-full bg-[var(--brand-surface)] text-xs font-semibold text-[var(--brand-muted)]">{index + 1}</span>
                    <CompactField label="Variante" name={`variantName-${index}`} placeholder="Manzana · 60 porciones" value={variant.variantName} onChange={(value) => updateVariantName(index, value)} />
                    <CompactField label="Código de barras" name={`variantBarcode-${index}`} placeholder="Escanea o captura" required={false} value={variant.primaryBarcode} onChange={(primaryBarcode) => updateVariant(index, { primaryBarcode })} onKeyDown={(event) => handleBarcodeEnter(event, index)} />
                    <CompactField label="SKU" name={`variantSku-${index}`} placeholder="PSY-MAN60" value={variant.sku} onChange={(sku) => updateVariant(index, { sku, skuManuallyEdited: true })} />
                    <div className="flex items-center justify-end gap-3 md:min-w-[7rem]">
                      <button className="text-xs font-semibold text-[var(--brand-primary)]" onClick={() => setEditingVariantIndex(index)} type="button">Detalles</button>
                      {variants.length > 1 ? <button aria-label={`Quitar variante ${index + 1}`} className="text-base font-semibold text-[#9b2c2c]" onClick={() => removeVariant(index)} title="Quitar variante" type="button">×</button> : null}
                    </div>
                  </article>
                ))}
              </div>
            </>
          )}
        </section>

        <button className="app-button h-12" disabled={saving} type="submit">{saving ? "Creando variantes..." : `Crear ${variants.length} ${variants.length === 1 ? "variante" : "variantes"}`}</button>
      </form>
    </AppDrawer>
  );
}

function CompactField({ label, name, onChange, onKeyDown, placeholder, required = true, value }: {
  label: string;
  name: string;
  onChange: (value: string) => void;
  onKeyDown?: (event: KeyboardEvent<HTMLInputElement>) => void;
  placeholder?: string;
  required?: boolean;
  value: string;
}) {
  return <label className="form-field gap-1" htmlFor={name}><span className="md:sr-only">{label}{required ? <b>*</b> : null}</span><input aria-label={label} className="app-input h-9 min-h-9 px-2 py-1 text-sm" id={name} name={name} onChange={(event) => onChange(event.target.value)} onKeyDown={onKeyDown} placeholder={placeholder} required={required} value={value} /></label>;
}

function CompactOverrideField({ inputMode, label, name, onChange, placeholder, type = "text", value }: {
  inputMode?: "decimal";
  label: string;
  name: string;
  onChange: (value: string) => void;
  placeholder: string;
  type?: "number" | "text";
  value: string;
}) {
  return <label className="form-field gap-1" htmlFor={name}><span className="text-xs">{label}</span><input className="app-input h-9 min-h-9 px-2 py-1 text-sm" id={name} inputMode={inputMode} min={type === "number" ? "0" : undefined} name={name} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} step={type === "number" ? "0.01" : undefined} type={type} value={value} /></label>;
}

function Check({ checked, label, onChange }: { checked: boolean; label: string; onChange: (checked: boolean) => void }) {
  return <label className="inline-flex h-9 w-fit items-center gap-2 text-sm font-medium"><input checked={checked} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => onChange(event.target.checked)} type="checkbox" />{label}</label>;
}

function categoryOptions(categories: CatalogCategory[]) {
  const sorted = categories.filter((category) => category.status === "ACTIVE").sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, "es"));
  const roots = sorted.filter((item) => !item.parentId);
  return roots.flatMap((root) => [
    { label: root.name, value: root.id },
    ...sorted.filter((child) => child.parentId === root.id).map((child) => ({ label: `↳ ${child.name}`, value: child.id })),
  ]);
}

function regenerateAutomaticSkus(variants: VariantDraft[], prefix: string) {
  const used = new Set(variants.filter((variant) => variant.skuManuallyEdited && variant.sku.trim()).map((variant) => variant.sku.trim().toUpperCase()));
  return variants.map((variant) => {
    if (variant.skuManuallyEdited) return variant;
    const sku = availableSku(prefix, variant.variantName, used);
    if (sku) used.add(sku);
    return { ...variant, sku };
  });
}

function uniqueGeneratedSku(prefix: string, variantName: string, variants: VariantDraft[], index: number) {
  const used = new Set(variants.filter((_, currentIndex) => currentIndex !== index).map((variant) => variant.sku.trim().toUpperCase()).filter(Boolean));
  return availableSku(prefix, variantName, used);
}

function availableSku(prefix: string, variantName: string, used: Set<string>) {
  const normalizedPrefix = normalizeSkuPart(prefix, 12);
  const variant = suggestVariantSkuPart(variantName);
  if (!normalizedPrefix || !variant) return "";
  const base = `${normalizedPrefix}-${variant}`;
  if (!used.has(base)) return base;
  let suffix = 2;
  while (used.has(`${base}-${suffix}`)) suffix += 1;
  return `${base}-${suffix}`;
}

function suggestVariantSkuPart(value: string) {
  const words = value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .split(/[^A-Z0-9]+/)
    .filter(Boolean);
  const firstWord = words[0] ?? "";
  const letters = firstWord.replace(/[^A-Z]/g, "").slice(0, 3);
  const firstNumber = words.map((word) => word.match(/\d+/)?.[0]).find(Boolean) ?? "";
  const fallback = normalizeSkuPart(firstWord, 6);
  const base = letters || fallback;
  const numericSuffix = firstNumber && !base.includes(firstNumber) ? firstNumber.slice(0, 4) : "";
  return `${base}${numericSuffix}`.slice(0, 8);
}

function suggestSkuPrefix(value: string) {
  const firstWord = value.trim().split(/\s+/)[0] ?? "";
  return normalizeSkuPart(firstWord, 3);
}

function normalizeSkuPart(value: string, maxLength: number) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, maxLength);
}

function centsRequired(value: string) {
  return Math.round(Number(value || "0") * 100);
}

function centsOptional(value: string) {
  return value.trim() ? Math.round(Number(value) * 100) : null;
}

function unique(values: string[]) {
  return [...new Set(values)];
}
