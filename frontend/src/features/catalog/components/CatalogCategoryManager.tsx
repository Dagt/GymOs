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
    <section className="grid gap-4 xl:grid-cols-[360px_minmax(0,1fr)] xl:items-start">
      <form className="app-card grid gap-4 p-5 xl:sticky xl:top-4" onSubmit={submit}>
        <div className="flex items-center justify-between gap-3">
          <h2 className="app-card-title">{editingId ? "Editar categoría" : "Nueva categoría"}</h2>
          {editingId ? <button className="text-sm font-semibold text-[var(--brand-primary)]" disabled={saving} onClick={reset} type="button">Cancelar</button> : null}
        </div>
        {error ? <p className="rounded-lg border border-[#e2b6b6] bg-[#fff7f7] px-3 py-2 text-sm text-[#9b2c2c]">{error}</p> : null}
        <Field label="Nombre"><input className="app-input" maxLength={120} placeholder="Ej. Suplementos" required value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></Field>
        <Field label="Ubicación">
          <select className="app-input bg-white" disabled={editingHasChildren} value={draft.parentId ?? ""} onChange={(event) => setDraft({ ...draft, parentId: event.target.value || null })}>
            <option value="">Categoría principal</option>
            {roots.filter((item) => item.id !== editingId && item.status === "ACTIVE").map((item) => <option key={item.id} value={item.id}>Dentro de {item.name}</option>)}
          </select>
        </Field>
        <Field label="Descripción"><textarea className="app-input min-h-24" maxLength={500} placeholder="Opcional" value={draft.description ?? ""} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></Field>
        <button className="app-button h-11" disabled={saving || !draft.name.trim()} type="submit">{saving ? "Guardando..." : editingId ? "Guardar" : draft.parentId ? "Crear subcategoría" : "Crear categoría"}</button>
      </form>

      <section className="app-card overflow-hidden">
        <div className="flex items-center justify-between border-b border-[#e3e7df] p-5">
          <h2 className="app-card-title">Estructura</h2>
          <span className="text-sm text-[#6f7a73]">{categories.length}</span>
        </div>
        <div className="max-h-[65vh] overflow-y-auto p-4">
          {roots.length === 0 ? <p className="py-8 text-center text-sm text-[#5d6b63]">No hay categorías.</p> : null}
          <div className="grid gap-3 lg:grid-cols-2">
            {roots.map((root) => (
              <article className="rounded-xl border border-[#d8ddd2] bg-[#fbfcfa] p-4" key={root.id}>
                <CategoryRow category={root} label="Principal" saving={saving} onArchive={archive} onEdit={edit} onToggle={toggle} />
                {(children.get(root.id) ?? []).length ? (
                  <div className="mt-3 grid gap-2 border-l-2 border-[var(--brand-accent)] pl-3">
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
    <div className={compact ? "rounded-lg border border-[#e1e5de] bg-white p-3" : ""}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <strong className="truncate">{category.name}</strong>
            <span className="rounded-full bg-[#eef2ec] px-2 py-1 text-[10px] font-semibold text-[#536158]">{label}</span>
            {category.status !== "ACTIVE" ? <span className="text-xs font-semibold text-[#8a5a22]">Inactiva</span> : null}
          </div>
          {category.description ? <p className="mt-1 line-clamp-2 text-xs text-[#6f7a73]">{category.description}</p> : null}
        </div>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Action label="Editar" disabled={saving} onClick={() => onEdit(category)} />
        <Action label={category.status === "ACTIVE" ? "Desactivar" : "Activar"} disabled={saving} onClick={() => void onToggle(category)} />
        <Action label="Archivar" danger disabled={saving} onClick={() => void onArchive(category)} />
      </div>
    </div>
  );
}

function Action({ label, disabled, danger = false, onClick }: { label: string; disabled: boolean; danger?: boolean; onClick: () => void }) {
  return <button className={danger ? "rounded-md border border-[#e2b6b6] px-2 py-1 text-xs font-semibold text-[#9b2c2c]" : "app-button-secondary h-8 px-2 text-xs"} disabled={disabled} onClick={onClick} type="button">{label}</button>;
}
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="grid gap-1.5 text-sm"><span className="font-semibold text-[#28332d]">{label}</span>{children}</label>; }
function compare(a: CatalogCategory, b: CatalogCategory) { return a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, "es"); }
