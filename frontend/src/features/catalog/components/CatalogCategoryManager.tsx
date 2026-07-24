"use client";

import { type FormEvent, type ReactNode, useMemo, useState } from "react";
import {
  archiveCatalogCategory,
  createCatalogCategory,
  updateCatalogCategory,
  type CatalogCategory,
  type CatalogCategoryPayload,
} from "@/lib/api";

const emptyDraft: CatalogCategoryPayload = {
  parentId: null,
  name: "",
  description: null,
  status: "ACTIVE",
  displayOrder: 0,
};

export function CatalogCategoryManager({ accessToken, categories, onChanged }: {
  accessToken: string;
  categories: CatalogCategory[];
  onChanged: () => Promise<void>;
}) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<CatalogCategoryPayload>(emptyDraft);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sorted = useMemo(() => [...categories].sort(compare), [categories]);
  const roots = useMemo(() => sorted.filter((item) => !item.parentId), [sorted]);
  const children = useMemo(() => {
    const grouped = new Map<string, CatalogCategory[]>();
    sorted.forEach((item) => {
      if (!item.parentId) return;
      grouped.set(item.parentId, [...(grouped.get(item.parentId) ?? []), item]);
    });
    return grouped;
  }, [sorted]);
  const editingHasChildren = Boolean(editingId && children.get(editingId)?.length);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.name.trim()) return;
    await run(async () => {
      const payload: CatalogCategoryPayload = {
        ...draft,
        name: draft.name.trim(),
        description: draft.description?.trim() || null,
        status: editingId ? draft.status : "ACTIVE",
        displayOrder: editingId ? draft.displayOrder : nextOrder(draft.parentId),
      };
      if (editingId) await updateCatalogCategory(accessToken, editingId, payload);
      else await createCatalogCategory(accessToken, payload);
      reset();
    });
  }

  function nextOrder(parentId?: string | null) {
    const siblings = categories.filter((item) => item.parentId === (parentId ?? null));
    return siblings.length ? Math.max(...siblings.map((item) => item.displayOrder)) + 1 : 0;
  }

  function edit(category: CatalogCategory) {
    setEditingId(category.id);
    setDraft({
      parentId: category.parentId,
      name: category.name,
      description: category.description,
      status: category.status,
      displayOrder: category.displayOrder,
    });
    setError(null);
  }

  async function toggle(category: CatalogCategory) {
    await run(() => updateCatalogCategory(accessToken, category.id, {
      parentId: category.parentId,
      name: category.name,
      description: category.description,
      status: category.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
      displayOrder: category.displayOrder,
    }));
  }

  async function archive(category: CatalogCategory) {
    if (!window.confirm(`¿Archivar ${category.name}?`)) return;
    await run(async () => {
      await archiveCatalogCategory(accessToken, category.id);
      if (editingId === category.id) reset();
    });
  }

  async function run(operation: () => Promise<unknown>) {
    setSaving(true); setError(null);
    try { await operation(); await onChanged(); }
    catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible guardar la categoría."); }
    finally { setSaving(false); }
  }

  function reset() { setEditingId(null); setDraft({ ...emptyDraft }); }

  return (
    <section className="grid gap-3 xl:grid-cols-[310px_minmax(0,1fr)] xl:items-start">
      <form className="app-card grid gap-3 p-4 xl:sticky xl:top-4" onSubmit={submit}>
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-base font-semibold">{editingId ? "Editar categoría" : "Nueva categoría"}</h2>
          {editingId ? <button className="text-xs font-semibold text-[var(--brand-primary)]" disabled={saving} onClick={reset} type="button">Cancelar</button> : null}
        </div>
        {error ? <p className="rounded-md border border-[#e2b6b6] bg-[#fff7f7] px-2 py-1.5 text-xs text-[#9b2c2c]">{error}</p> : null}
        <Field label="Nombre"><input className="app-input h-10 min-h-10 text-sm" maxLength={120} placeholder="Suplementos" required value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></Field>
        <Field label="Ubicación">
          <select className="app-input h-10 min-h-10 bg-white text-sm" disabled={editingHasChildren} value={draft.parentId ?? ""} onChange={(event) => setDraft({ ...draft, parentId: event.target.value || null })}>
            <option value="">Categoría principal</option>
            {roots.filter((item) => item.id !== editingId && item.status === "ACTIVE").map((item) => <option key={item.id} value={item.id}>Dentro de {item.name}</option>)}
          </select>
        </Field>
        <Field label="Descripción"><textarea className="app-input min-h-16 py-2 text-sm" maxLength={500} placeholder="Opcional" value={draft.description ?? ""} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></Field>
        <button className="app-button h-10 min-h-10 text-sm" disabled={saving || !draft.name.trim()} type="submit">{saving ? "Guardando..." : editingId ? "Guardar" : draft.parentId ? "Crear subcategoría" : "Crear categoría"}</button>
      </form>

      <section className="app-card overflow-hidden">
        <div className="flex items-center justify-between border-b border-[#e3e7df] px-3 py-2.5">
          <h2 className="text-base font-semibold">Estructura</h2>
          <span className="text-xs text-[#6f7a73]">{categories.length}</span>
        </div>
        <div className="max-h-[58vh] overflow-y-auto p-3 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {roots.length === 0 ? <p className="py-6 text-center text-xs text-[#5d6b63]">No hay categorías.</p> : null}
          <div className="columns-1 gap-2 md:columns-2 xl:columns-3">
            {roots.map((root) => (
              <article className="mb-2 break-inside-avoid rounded-lg border border-[#d8ddd2] bg-[#fbfcfa] p-2.5" key={root.id}>
                <CategoryRow category={root} label="Principal" saving={saving} onArchive={archive} onEdit={edit} onToggle={toggle} />
                {(children.get(root.id) ?? []).length ? (
                  <div className="mt-2 grid gap-1.5 border-l-2 border-[var(--brand-accent)] pl-2">
                    {(children.get(root.id) ?? []).map((child) => <CategoryRow key={child.id} category={child} label="Subcategoría" compact saving={saving} onArchive={archive} onEdit={edit} onToggle={toggle} />)}
                  </div>
                ) : null}
              </article>
            ))}
          </div>
        </div>
      </section>
    </section>
  );
}

function CategoryRow({ category, label, compact = false, saving, onArchive, onEdit, onToggle }: {
  category: CatalogCategory;
  label: string;
  compact?: boolean;
  saving: boolean;
  onArchive: (category: CatalogCategory) => Promise<void>;
  onEdit: (category: CatalogCategory) => void;
  onToggle: (category: CatalogCategory) => Promise<void>;
}) {
  return (
    <div className={compact ? "rounded-md border border-[#e1e5de] bg-white p-2" : ""}>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-1.5">
          <strong className="truncate text-[12px] leading-tight">{category.name}</strong>
          <span className="rounded-full bg-[#eef2ec] px-1.5 py-0.5 text-[9px] font-semibold text-[#536158]">{label}</span>
          {category.status !== "ACTIVE" ? <span className="text-[9px] font-semibold text-[#8a5a22]">Inactiva</span> : null}
        </div>
        {category.description ? <p className="mt-1 line-clamp-1 text-[10px] leading-tight text-[#6f7a73]">{category.description}</p> : null}
      </div>
      <div className="mt-2 flex flex-wrap gap-1">
        <Action label="Editar" disabled={saving} onClick={() => onEdit(category)} />
        <Action label={category.status === "ACTIVE" ? "Desactivar" : "Activar"} disabled={saving} onClick={() => void onToggle(category)} />
        <Action label="Archivar" danger disabled={saving} onClick={() => void onArchive(category)} />
      </div>
    </div>
  );
}

function Action({ label, disabled, danger = false, onClick }: { label: string; disabled: boolean; danger?: boolean; onClick: () => void }) {
  return <button className={danger ? "rounded border border-[#e2b6b6] px-1.5 py-1 text-[9px] font-semibold leading-none text-[#9b2c2c]" : "rounded border border-[#cdd5ca] bg-white px-1.5 py-1 text-[9px] font-semibold leading-none text-[var(--brand-primary)]"} disabled={disabled} onClick={onClick} type="button">{label}</button>;
}
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="grid gap-1 text-xs"><span className="font-semibold text-[#28332d]">{label}</span>{children}</label>; }
function compare(a: CatalogCategory, b: CatalogCategory) { return a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, "es"); }
