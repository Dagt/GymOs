"use client";

import { type FormEvent, useMemo, useState } from "react";
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

export function CatalogCategoryManager({
  accessToken,
  categories,
  onChanged,
}: {
  accessToken: string;
  categories: CatalogCategory[];
  onChanged: () => Promise<void>;
}) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<CatalogCategoryPayload>(emptyDraft);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const categoriesById = useMemo(() => new Map(categories.map((category) => [category.id, category])), [categories]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.name.trim()) return;
    await run(async () => {
      const payload = {
        ...draft,
        name: draft.name.trim(),
        description: draft.description?.trim() || null,
      };
      if (editingId) await updateCatalogCategory(accessToken, editingId, payload);
      else await createCatalogCategory(accessToken, payload);
      reset();
    });
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
    if (!window.confirm(`Archivar la categoria ${category.name}?`)) return;
    await run(async () => {
      await archiveCatalogCategory(accessToken, category.id);
      if (editingId === category.id) reset();
    });
  }

  async function run(operation: () => Promise<unknown>) {
    setSaving(true);
    setError(null);
    try {
      await operation();
      await onChanged();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible administrar categorias.");
    } finally {
      setSaving(false);
    }
  }

  function reset() {
    setEditingId(null);
    setDraft({ ...emptyDraft, displayOrder: categories.length });
  }

  return (
    <section className="app-card grid gap-5 p-5">
      <div>
        <p className="app-eyebrow">Clasificacion</p>
        <h2 className="app-card-title">Categorias y subcategorias</h2>
        <p className="app-card-copy">Organiza el catalogo con jerarquia, descripcion y orden operativo.</p>
      </div>
      {error ? <p className="text-sm text-[#9b2c2c]" role="alert">{error}</p> : null}
      <form className="grid gap-3 rounded-md border border-[#d8ddd2] p-4" onSubmit={submit}>
        <div className="grid gap-3 md:grid-cols-2">
          <Field label="Nombre">
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3" maxLength={120}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })} value={draft.name} />
          </Field>
          <Field label="Categoria padre">
            <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3"
              onChange={(event) => setDraft({ ...draft, parentId: event.target.value || null })}
              value={draft.parentId ?? ""}>
              <option value="">Categoria raiz</option>
              {categories.filter((category) => category.id !== editingId && category.status === "ACTIVE").map((category) => (
                <option key={category.id} value={category.id}>{category.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Orden">
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3" min={0} type="number"
              onChange={(event) => setDraft({ ...draft, displayOrder: Number(event.target.value || 0) })}
              value={draft.displayOrder} />
          </Field>
          <Field label="Estado">
            <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3"
              onChange={(event) => setDraft({ ...draft, status: event.target.value as CatalogCategory["status"] })}
              value={draft.status}>
              <option value="ACTIVE">Activa</option>
              <option value="INACTIVE">Inactiva</option>
            </select>
          </Field>
        </div>
        <Field label="Descripcion">
          <textarea className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2" maxLength={500}
            onChange={(event) => setDraft({ ...draft, description: event.target.value })}
            value={draft.description ?? ""} />
        </Field>
        <div className="flex flex-wrap gap-2">
          <button className="app-button" disabled={saving || !draft.name.trim()} type="submit">
            {saving ? "Guardando..." : editingId ? "Guardar categoria" : "Agregar categoria"}
          </button>
          {editingId ? <button className="app-button-secondary" disabled={saving} onClick={reset} type="button">Cancelar</button> : null}
        </div>
      </form>
      <div className="grid gap-2">
        {categories.length === 0 ? <p className="text-sm text-[#5d6b63]">Todavia no hay categorias.</p> : null}
        {categories.map((category) => (
          <article className="flex flex-col gap-3 rounded-md border border-[#d8ddd2] px-4 py-3 sm:flex-row sm:items-center sm:justify-between" key={category.id}>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <strong>{category.name}</strong>
                <span className={category.status === "ACTIVE" ? "text-xs font-semibold text-[#28733e]" : "text-xs font-semibold text-[#8a5a22]"}>
                  {category.status === "ACTIVE" ? "Activa" : "Inactiva"}
                </span>
              </div>
              <p className="mt-1 text-xs text-[#6f7a73]">
                {category.parentId ? `Subcategoria de ${categoriesById.get(category.parentId)?.name ?? "categoria archivada"}` : "Categoria raiz"}
                {` · Orden ${category.displayOrder}`}
              </p>
              {category.description ? <p className="mt-1 text-sm text-[#5d6b63]">{category.description}</p> : null}
            </div>
            <div className="flex flex-wrap gap-2">
              <button className="app-button-secondary" disabled={saving} onClick={() => edit(category)} type="button">Editar</button>
              <button className="app-button-secondary" disabled={saving} onClick={() => toggle(category)} type="button">
                {category.status === "ACTIVE" ? "Desactivar" : "Activar"}
              </button>
              <button className="rounded-md border border-[#e2b6b6] px-3 py-2 text-sm font-semibold text-[#9b2c2c]" disabled={saving}
                onClick={() => archive(category)} type="button">Archivar</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="grid gap-1 text-sm"><span className="font-medium">{label}</span>{children}</label>;
}
