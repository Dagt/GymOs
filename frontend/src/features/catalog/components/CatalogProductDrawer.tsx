import type { FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { NumberField, SelectField, TextAreaField, TextField } from "@/components/FormFields";
import type { CatalogCategory, ProductPayload } from "@/lib/api";

const serviceSubtypeOptions = [
  { label: "Servicio general", value: "GENERAL_SERVICE" },
  { label: "Pase o visita", value: "VISIT_PASS" },
  { label: "Actividad o servicio con reserva", value: "BOOKING" },
  { label: "Rutina o programa de entrenamiento", value: "ROUTINE" },
  { label: "Consulta profesional", value: "CONSULTATION" },
  { label: "Bienestar y recuperacion", value: "WELLNESS_SERVICE" },
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
  const categoryOptions = buildCategoryOptions(availableCategories);

  function change(values: Partial<ProductPayload>) {
    onFormChange({ ...form, ...values });
  }

  function changeProductType(value: string) {
    const productType = value as ProductPayload["productType"];
    change({
      productType,
      commercialSubtype: productType === "PHYSICAL"
        ? "GENERAL_PRODUCT"
        : form.commercialSubtype === "GENERAL_PRODUCT" ? "GENERAL_SERVICE" : form.commercialSubtype,
      inventoryTracked: productType === "PHYSICAL" ? form.inventoryTracked : false,
    });
  }

  return (
    <AppDrawer
      description="Empieza con lo indispensable. Los detalles internos, costos y promociones quedan separados para no complicar el alta."
      onClose={onClose}
      open={open}
      title={editing ? "Editar producto o servicio" : "Nuevo producto o servicio"}
    >
      <form className="grid gap-5" onSubmit={onSubmit}>
        <section className="overflow-hidden rounded-2xl border border-[#d8ddd2] bg-white shadow-sm">
          <div className="border-b border-[#e3e7df] bg-[#f7f9f5] px-5 py-4">
            <p className="app-eyebrow">Información esencial</p>
            <h3 className="text-lg font-semibold text-[#111815]">Datos para vender</h3>
            <p className="mt-1 text-sm text-[#5d6b63]">Nombre, código, precio y tipo son suficientes para comenzar.</p>
          </div>

          <div className="grid gap-4 p-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Nombre" name="productName" value={form.name}
                placeholder={form.productType === "SERVICE" ? "Ej. Consulta de nutricion" : "Ej. Agua 600 ml"}
                onChange={(name) => change({ name })} />
              <TextField label="SKU" name="productSku" value={form.sku}
                help="Código interno único. Ej. AGUA-600 o NUTRICION-01" onChange={(sku) => change({ sku })} />
              <NumberField label="Precio" name="productPrice" value={price}
                help="Precio al público en MXN." onChange={onPriceChange} />
              <SelectField label="¿Qué estás vendiendo?" name="productType"
                options={[
                  { label: "Producto físico", value: "PHYSICAL" },
                  { label: "Servicio", value: "SERVICE" },
                ]}
                value={form.productType} onChange={changeProductType} />
            </div>

            <TypeExplanation type={form.productType} />

            {form.productType === "SERVICE" ? (
              <div className="grid gap-2 rounded-xl border border-[#d8ddd2] bg-[#fbfcfa] p-4">
                <SelectField label="Tipo de servicio" name="productCommercialSubtype"
                  options={serviceSubtypeOptions}
                  value={form.commercialSubtype}
                  onChange={(commercialSubtype) => change({ commercialSubtype: commercialSubtype as ProductPayload["commercialSubtype"] })} />
                <p className="text-xs text-[#5d6b63]">{serviceSubtypeDescription(form.commercialSubtype)}</p>
              </div>
            ) : null}

            <SelectField label="Categoría" name="productCategory" required={false}
              options={[{ label: "Sin categoría", value: "" }, ...categoryOptions]}
              value={form.categoryId ?? ""}
              onChange={(categoryId) => change({ categoryId: categoryId || null })} />

            <label className="flex items-start gap-3 rounded-xl border border-[#cdd5ca] bg-[#f8faf7] p-4 text-sm">
              <input checked={form.inventoryTracked} disabled={form.productType !== "PHYSICAL"}
                className="mt-1 h-4 w-4 accent-[var(--brand-primary)]"
                onChange={(event) => change({ inventoryTracked: event.target.checked })} type="checkbox" />
              <span>
                <strong className="block text-[#111815]">Controlar existencias</strong>
                {form.productType === "PHYSICAL"
                  ? "Actívalo cuando el POS deba descontar unidades del inventario al vender."
                  : "Los servicios no manejan inventario físico."}
              </span>
            </label>
          </div>
        </section>

        <details className="rounded-2xl border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <summary className="cursor-pointer text-base font-semibold text-[#111815]">Descripción y detalles de venta</summary>
          <p className="mt-2 text-sm text-[#5d6b63]">
            Información útil para identificar el artículo o explicar el servicio. Todo este bloque es opcional.
          </p>

          <div className="mt-4 grid gap-4">
            <TextAreaField label="Descripción" name="productDescription" required={false}
              placeholder={form.productType === "SERVICE"
                ? "Explica qué incluye, duración aproximada y condiciones."
                : "Describe el producto, contenido o características importantes."}
              value={form.description ?? ""} onChange={(description) => change({ description })} />

            {form.productType === "PHYSICAL" ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField label="Código de barras" name="productBarcode" required={false}
                  value={form.barcode ?? ""} onChange={(barcode) => change({ barcode: barcode || null })} />
                <TextField label="Marca" name="productBrand" required={false}
                  value={form.brand ?? ""} onChange={(brand) => change({ brand: brand || null })} />
                <TextField label="Presentación" name="productPresentation" required={false}
                  placeholder="Ej. Bote de 30 porciones" value={form.presentation ?? ""}
                  onChange={(presentation) => change({ presentation: presentation || null })} />
                <TextField label="Unidad de venta" name="productUnit" required={false}
                  placeholder="Ej. pieza, botella, bote" value={form.unitOfMeasure ?? ""}
                  onChange={(unitOfMeasure) => change({ unitOfMeasure: unitOfMeasure || null })} />
              </div>
            ) : null}

            <TextField label="Etiquetas" name="productTags" required={false}
              help="Palabras para búsqueda, separadas por coma. Ej. energia, cafeina, entrenamiento."
              value={form.tags.join(", ")}
              onChange={(value) => change({ tags: value.split(",").map((tag) => tag.trim()).filter(Boolean) })} />
          </div>
        </details>

        {canReadCosts ? (
          <details className="rounded-2xl border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <summary className="cursor-pointer text-base font-semibold text-[#111815]">Costo interno y proveedor</summary>
            <p className="mt-2 text-sm text-[#5d6b63]">
              El costo de referencia es lo que el gimnasio paga o estima que cuesta ofrecerlo. No se muestra al cliente y sirve para calcular margen.
            </p>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <NumberField label="Costo de referencia" name="productReferenceCost" required={false}
                help="Ej. si lo compras en $300 y lo vendes en $450, escribe 300."
                value={moneyValue(form.referenceCostCents)}
                onChange={(value) => change({ referenceCostCents: cents(value) })} />
              <TextField label="Proveedor principal" name="productSupplier" required={false}
                placeholder="Ej. Distribuidora deportiva"
                value={form.supplierName ?? ""} onChange={(supplierName) => change({ supplierName: supplierName || null })} />
            </div>
          </details>
        ) : null}

        <details className="rounded-2xl border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <summary className="cursor-pointer text-base font-semibold text-[#111815]">Promoción temporal</summary>
          <p className="mt-2 text-sm text-[#5d6b63]">
            Úsala sólo cuando quieras vender temporalmente a otro precio. Si no hay promoción, deja todo vacío.
          </p>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <NumberField label="Precio promocional" name="productPromotionalPrice" required={false}
              value={moneyValue(form.promotionalPriceCents)}
              onChange={(value) => change({ promotionalPriceCents: cents(value) })} />
            <div className="hidden sm:block" aria-hidden="true" />
            <DateTimeField label="Inicio de promoción" value={form.promotionStartsAt}
              onChange={(promotionStartsAt) => change({ promotionStartsAt })} />
            <DateTimeField label="Fin de promoción" value={form.promotionEndsAt}
              onChange={(promotionEndsAt) => change({ promotionEndsAt })} />
          </div>
        </details>

        {canManageImages ? (
          <details className="rounded-2xl border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <summary className="cursor-pointer text-base font-semibold text-[#111815]">Imagen</summary>
            <div className="mt-4 grid gap-3">
              <span className="text-sm text-[#5d6b63]">
                {existingImage ? "Este registro tiene una imagen almacenada." : "Todavía no hay una imagen almacenada."}
              </span>
              {editing && existingImage ? (
                <button className="w-fit rounded-md border border-[#e2b6b6] px-3 py-2 text-sm font-semibold text-[#9b2c2c]"
                  disabled={saving} onClick={onRemoveImage} type="button">Eliminar imagen</button>
              ) : null}
              <input accept="image/jpeg,image/png,image/webp" className="rounded-md border border-[#cdd5ca] p-2"
                onChange={(event) => onImageChange(event.target.files?.[0] ?? null)} type="file" />
              <span className="text-xs text-[#5d6b63]">{image ? image.name : "JPG, PNG o WebP; máximo 5 MB."}</span>
            </div>
          </details>
        ) : null}

        <button className="app-button h-12 text-base" disabled={saving} type="submit">
          {saving ? "Guardando..." : editing ? "Guardar cambios" : form.productType === "SERVICE" ? "Crear servicio" : "Crear producto"}
        </button>
      </form>
    </AppDrawer>
  );
}

function TypeExplanation({ type }: { type: ProductPayload["productType"] }) {
  if (type === "SERVICE") {
    return (
      <aside className="rounded-xl border border-[#c9dbe1] bg-[#f3f9fb] px-4 py-3 text-sm text-[#34515b]">
        <strong className="block text-[#203b44]">¿Qué entra como servicio?</strong>
        Algo que cobras pero no entregas como inventario: consulta de nutrición, sesión de entrenamiento personal, masaje, evaluación física, pase de día o una actividad reservable.
      </aside>
    );
  }
  return (
    <aside className="rounded-xl border border-[#d5dfcf] bg-[#f5f9f3] px-4 py-3 text-sm text-[#425846]">
      <strong className="block text-[#263a2a]">Producto físico</strong>
      Algo que entregas y puede tener existencias: agua, suplementos, ropa, accesorios, toallas o bebidas.
    </aside>
  );
}

function buildCategoryOptions(categories: CatalogCategory[]) {
  const sorted = [...categories].sort((left, right) => left.displayOrder - right.displayOrder || left.name.localeCompare(right.name, "es"));
  const roots = sorted.filter((category) => !category.parentId);
  const childrenByParent = new Map<string, CatalogCategory[]>();
  for (const category of sorted) {
    if (!category.parentId) continue;
    const children = childrenByParent.get(category.parentId) ?? [];
    children.push(category);
    childrenByParent.set(category.parentId, children);
  }

  const options: { label: string; value: string }[] = [];
  for (const root of roots) {
    options.push({ label: root.status === "ACTIVE" ? root.name : `${root.name} (inactiva)`, value: root.id });
    for (const child of childrenByParent.get(root.id) ?? []) {
      options.push({
        label: `↳ ${child.name}${child.status === "ACTIVE" ? "" : " (inactiva)"}`,
        value: child.id,
      });
    }
  }

  for (const category of sorted.filter((item) => item.parentId && !categories.some((candidate) => candidate.id === item.parentId))) {
    options.push({ label: `↳ ${category.name}`, value: category.id });
  }
  return options;
}

function serviceSubtypeDescription(subtype: ProductPayload["commercialSubtype"]) {
  const descriptions: Partial<Record<ProductPayload["commercialSubtype"], string>> = {
    GENERAL_SERVICE: "Cobro por un servicio que no necesita agenda ni un flujo especial, por ejemplo inscripción, medición corporal o renta de toalla.",
    VISIT_PASS: "Entrada de un día, pase de invitado o paquete de visitas.",
    BOOKING: "Servicio que normalmente requiere elegir fecha, hora o recurso, como una clase o sesión reservada.",
    ROUTINE: "Diseño o seguimiento de una rutina y programa de entrenamiento.",
    CONSULTATION: "Consulta con nutrición, fisioterapia, medicina, coaching u otro profesional.",
    WELLNESS_SERVICE: "Masaje, recuperación, terapia, spa u otro servicio de bienestar.",
  };
  return descriptions[subtype] ?? "Selecciona la opción que mejor describa el servicio.";
}

function cents(value: string) {
  return value.trim() ? Math.round(Number(value) * 100) : null;
}

function moneyValue(value?: number | null) {
  return value == null ? "" : String(value / 100);
}

function DateTimeField({ label, value, onChange }: {
  label: string;
  value?: string | null;
  onChange: (value: string | null) => void;
}) {
  return (
    <label className="grid gap-1 text-sm">
      <span className="font-medium">{label}</span>
      <input className="app-input" type="datetime-local"
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
