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
  optionsOpen: boolean;
  presentationOverride: string;
  priceOverride: string;
  costOverride: string;
  promotionalPrice: string;
  promotionStartsAt: string;
  promotionEndsAt: string;
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
  optionsOpen: false,
  presentationOverride: "",
  priceOverride: "",
  costOverride: "",
  promotionalPrice: "",
  promotionStartsAt: "",
  promotionEndsAt: "",
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
  const [variants, setVariants] = useState<VariantDraft[]>([blankVariant(), blankVariant()]);
  const addVariantButton = useRef<HTMLButtonElement>(null);
  const selectedFamily = families.find((family) => family.id === familyId);

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
        promotionalPriceCents: centsOptional(variant.promotionalPrice),
        promotionStartsAt: isoDateTime(variant.promotionStartsAt),
        promotionEndsAt: isoDateTime(variant.promotionEndsAt),
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
    setVariants((current) => [...current, blankVariant()]);
    if (focus) window.setTimeout(() => document.getElementById(`variantName-${nextIndex}`)?.focus(), 0);
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
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Presentación común" name="variantCommonPresentation" placeholder="Bote de 35 scoops" required={false} value={commonPresentation} onChange={setCommonPresentation} />
            <NumberField label="Precio común" name="variantCommonPrice" step="0.01" value={commonPrice} onChange={setCommonPrice} />
            {canReadCosts ? <NumberField label="Costo común" name="variantCommonCost" required={false} step="0.01" value={commonCost} onChange={setCommonCost} /> : null}
            {canReadCosts ? <TextField label="Proveedor" name="variantSupplier" placeholder="Proveedor" required={false} value={supplierName} onChange={setSupplierName} /> : null}
            <SelectField label="Categoría" name="variantCategory" required={false} options={[{ label: "Sin categoría", value: "" }, ...categoryOptions(categories)]} value={categoryId} onChange={setCategoryId} />
            <SelectField label="Unidad" name="variantUnit" options={[...CATALOG_UNIT_OPTIONS]} value={unitOfMeasure} onChange={setUnitOfMeasure} />
            <TextField label="Etiquetas" name="variantTags" placeholder="preentreno, energía" required={false} value={tags} onChange={setTags} />
            <TextField help="Se usa para sugerir SKU editables, por ejemplo PSY-MAN." label="Prefijo SKU" name="variantSkuPrefix" placeholder="PSY" required={false} value={skuPrefix} onChange={(value) => applySkuPrefix(value, true)} />
            <SelectField label="Estado inicial" name="variantStatus" options={[{ label: "Activo", value: "ACTIVE" }, { label: "Inactivo", value: "INACTIVE" }]} value={status} onChange={(value) => setStatus(value as ProductVariantBatchPayload["status"])} />
            {canManageInventory && inventoryTracked ? <NumberField label="Stock mínimo común" min="0" name="variantReorderPoint" value={reorderPoint} onChange={setReorderPoint} /> : null}
          </div>
          {canManageInventory ? <Check checked={inventoryTracked} label="Controlar inventario de cada variante" onChange={setInventoryTracked} /> : null}
        </section>

        <section className="grid gap-3">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <p className="font-semibold">Variantes</p>
            <button ref={addVariantButton} className="app-button-secondary h-9 px-3" onClick={() => addVariant(true)} type="button">+ Agregar variante</button>
          </div>
          <div className="overflow-hidden rounded-xl border border-[var(--brand-line)] bg-white">
            <div className="hidden grid-cols-[minmax(0,1.15fr)_minmax(0,1.1fr)_minmax(0,.8fr)_auto] gap-2 border-b border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 py-2 text-[11px] font-semibold uppercase tracking-wide text-[var(--brand-muted)] md:grid">
              <span>Variante</span><span>Código de barras</span><span>SKU</span><span>Acciones</span>
            </div>
            {variants.map((variant, index) => (
              <article className="border-b border-[var(--brand-line)] last:border-b-0" data-testid={`variant-row-${index}`} key={index}>
                <div className="grid items-center gap-2 px-3 py-2 md:grid-cols-[minmax(0,1.15fr)_minmax(0,1.1fr)_minmax(0,.8fr)_auto]">
                  <CompactField label="Sabor, tamaño o variante" name={`variantName-${index}`} placeholder="Manzana · 60 porciones" value={variant.variantName} onChange={(value) => updateVariantName(index, value)} />
                  <div className="flex min-w-0 items-end gap-1">
                    <div className="min-w-0 flex-1"><CompactField label="Código de barras" name={`variantBarcode-${index}`} placeholder="Escanea o captura" required={false} value={variant.primaryBarcode} onChange={(primaryBarcode) => updateVariant(index, { primaryBarcode })} onKeyDown={(event) => handleBarcodeEnter(event, index)} /></div>
                    {variant.additionalBarcodes.length < 19 ? <button aria-label={`Agregar código alternativo a variante ${index + 1}`} className="flex h-9 w-8 shrink-0 items-center justify-center rounded-md border border-[var(--brand-line)] text-base font-semibold text-[var(--brand-primary)]" onClick={() => updateVariant(index, { additionalBarcodes: [...variant.additionalBarcodes, ""] })} title="Agregar código alternativo" type="button">+</button> : null}
                  </div>
                  <CompactField label="SKU" name={`variantSku-${index}`} placeholder="PSY-MAN60" value={variant.sku} onChange={(sku) => updateVariant(index, { sku, skuManuallyEdited: true })} />
                  <div className="flex items-center justify-end gap-3 md:min-w-[10rem]">
                    <button aria-expanded={variant.optionsOpen} className="text-xs font-semibold text-[var(--brand-primary)]" onClick={() => updateVariant(index, { optionsOpen: !variant.optionsOpen })} type="button">{variant.optionsOpen ? "Cerrar" : "Precio / tamaño"}</button>
                    {variants.length > 1 ? <button className="text-xs font-semibold text-[#9b2c2c]" onClick={() => setVariants((current) => current.filter((_, currentIndex) => currentIndex !== index))} type="button">Quitar</button> : null}
                  </div>
                </div>

                {variant.additionalBarcodes.length ? (
                  <div className="flex flex-wrap items-end gap-2 border-t border-dashed border-[var(--brand-line)] px-3 py-2">
                    {variant.additionalBarcodes.map((barcode, aliasIndex) => (
                      <div className="flex min-w-[12rem] flex-1 items-end gap-2" key={aliasIndex}>
                        <div className="min-w-0 flex-1"><CompactField label={`Código alternativo ${aliasIndex + 1}`} name={`variantAlias-${index}-${aliasIndex}`} required={false} value={barcode} onChange={(value) => updateVariant(index, { additionalBarcodes: variant.additionalBarcodes.map((item, currentIndex) => currentIndex === aliasIndex ? value : item) })} /></div>
                        <button aria-label={`Quitar código alternativo ${aliasIndex + 1}`} className="mb-2 text-xs font-semibold text-[#9b2c2c]" onClick={() => updateVariant(index, { additionalBarcodes: variant.additionalBarcodes.filter((_, currentIndex) => currentIndex !== aliasIndex) })} type="button">Quitar</button>
                      </div>
                    ))}
                  </div>
                ) : null}

                {variant.optionsOpen ? (
                  <div className="grid gap-3 border-t border-dashed border-[var(--brand-line)] bg-[var(--brand-surface)] px-3 py-3">
                    <div className="grid gap-3 sm:grid-cols-3">
                      <CompactOverrideField label="Presentación / tamaño" name={`variantPresentation-${index}`} placeholder={commonPresentation ? `Común: ${commonPresentation}` : "Ej. Bote de 60 porciones"} value={variant.presentationOverride} onChange={(presentationOverride) => updateVariant(index, { presentationOverride })} />
                      <CompactOverrideField inputMode="decimal" label="Precio" name={`variantPrice-${index}`} placeholder={commonPrice ? `Común: $${commonPrice}` : "Precio especial"} type="number" value={variant.priceOverride} onChange={(priceOverride) => updateVariant(index, { priceOverride })} />
                      {canReadCosts ? <CompactOverrideField inputMode="decimal" label="Costo" name={`variantCost-${index}`} placeholder={commonCost ? `Común: $${commonCost}` : "Costo especial"} type="number" value={variant.costOverride} onChange={(costOverride) => updateVariant(index, { costOverride })} /> : null}
                    </div>
                    <details className="rounded-md border border-[var(--brand-line)] bg-white px-3 py-2">
                      <summary className="cursor-pointer text-xs font-semibold text-[var(--brand-primary)]">Promoción opcional</summary>
                      <div className="mt-3 grid gap-3 sm:grid-cols-3">
                        <NumberField label="Precio promocional" name={`variantPromoPrice-${index}`} required={false} step="0.01" value={variant.promotionalPrice} onChange={(promotionalPrice) => updateVariant(index, { promotionalPrice })} />
                        <DateTimeField label="Inicio de promoción" name={`variantPromoStart-${index}`} value={variant.promotionStartsAt} onChange={(promotionStartsAt) => updateVariant(index, { promotionStartsAt })} />
                        <DateTimeField label="Fin de promoción" name={`variantPromoEnd-${index}`} value={variant.promotionEndsAt} onChange={(promotionEndsAt) => updateVariant(index, { promotionEndsAt })} />
                      </div>
                    </details>
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        </section>

        <button className="app-button h-12" disabled={saving} type="submit">{saving ? "Creando variantes..." : `Crear ${variants.length} variantes`}</button>
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
  return <label className="form-field gap-1" htmlFor={name}><span className="md:sr-only">{label}{required ? <b>*</b> : null}</span><input className="app-input h-9 min-h-9 px-2 py-1 text-sm" id={name} name={name} onChange={(event) => onChange(event.target.value)} onKeyDown={onKeyDown} placeholder={placeholder} required={required} value={value} /></label>;
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

function DateTimeField({ label, name, onChange, value }: { label: string; name: string; onChange: (value: string) => void; value: string }) {
  return <label className="form-field" htmlFor={name}><span>{label}</span><input className="app-input" id={name} name={name} onChange={(event) => onChange(event.target.value)} type="datetime-local" value={value} /></label>;
}

function Check({ checked, label, onChange }: { checked: boolean; label: string; onChange: (checked: boolean) => void }) {
  return <label className="flex h-11 items-center gap-3 rounded-md border border-[var(--brand-line)] px-3 text-sm font-medium"><input checked={checked} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => onChange(event.target.checked)} type="checkbox" />{label}</label>;
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

function isoDateTime(value: string) {
  return value ? new Date(value).toISOString() : null;
}

function unique(values: string[]) {
  return [...new Set(values)];
}
