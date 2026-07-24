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

  const sortedCategories = useMemo(
    () => [...categories].sort(compareCategories),
    [categories],
  );
  const categoriesById = useMemo(
    () => new Map(sortedCategories.map((category) => [category.id, category])),
    [sortedCategories],
  );
  const rootCategories = useMemo(
    () => sortedCategories.filter((category) => !category.parentId),
    [sortedCategories],
  );
  const childrenByParent = useMemo(() => {
    const grouped = new Map<string, CatalogCategory[]>();
    for (const category of sortedCategories) {
      if (!category.parentId) continue;
      const children = grouped.get(category.parentId) ?? [];
      children.push(category);
      grouped.set(category.parentId, children);
    }
    return grouped;
  }, [sortedCategories]);
  const orphanedCategories = useMemo(
    () => sortedCategories.filter((category) => category.parentId && !categoriesById.has(category.parentId)),
    [categoriesById, sortedCategories],
  );
  const editingHasChildren = Boolean(editingId && childrenByParent.get(editingId)?.length);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!draft.name.trim()) return;
    await run(async () => {
      const payload: CatalogCategoryPayload = {
        ...draft,
        name: draft.name.trim(),
        description: draft.description?.trim() || null,
        status: editingId ? draft.status : "ACTIVE",
        displayOrder: editingId ? draft.displayOrder : nextDisplayOrder(draft.parentId),
      };
      if (editingId) await updateCatalogCategory(accessToken, editingId, payload);
      else await createCatalogCategory(accessToken, payload);
      reset();
    });
  }

  function nextDisplayOrder(parentId?: string | null) {
    const siblings = categories.filter((category) => category.parentId === (parentId ?? null));
    return siblings.length === 0 ? 0 : Math.max(...siblings.map((category) => category.displayOrder)) + 1;
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
    setDraft({ ...emptyDraft });
  }

  return (
    <section className="grid gap-5">
      <section className="overflow-hidden rounded-2xl border border-[#d8ddd2] bg-white shadow-sm">
        <div className="border-b border-[#e3e7df] bg-[#f7f9f5] px-5 py-4">
          <p className="app-eyebrow">Clasificacion</p>
          <h2 className="app-card-title">{editingId ? "Editar categoria" : "Nueva categoria"}</h2>
          <p className="app-card-copy">
            Una categoria principal agrupa productos. Una subcategoria aparece dentro de otra, por ejemplo Suplementos → Preentrenos.
          </p>
        </div>

        <form className="grid gap-4 p-5" onSubmit={submit}>
          {error ? <p className="rounded-lg border border-[#e2b6b6] bg-[#fff7f7] px-3 py-2 text-sm text-[#9b2c2c]" role="alert">{error}</p> : null}

          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Nombre">
              <input
                className="app-input"
                maxLength={120}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                placeholder="Ej. Suplementos"
                required
                value={draft.name}
              />
            </Field>

            <Field label="Ubicacion">
              <select
                className="app-input bg-white"
                disabled={editingHasChildren}
                onChange={(event) => setDraft({ ...draft, parentId: event.target.value || null })}
                value={draft.parentId ?? ""}
              >
                <option value="">Categoria principal</option>
                {rootCategories
                  .filter((category) => category.id !== editingId && category.status === "ACTIVE")
                  .map((category) => (
                    <option key={category.id} value={category.id}>Dentro de {category.name}</option>
                  ))}
              </select>
              <small className="text-[#6f7a73]">
                {editingHasChildren
                  ? "Esta categoria contiene subcategorias y debe permanecer como principal."
                  : "Selecciona una categoria principal sólo cuando estés creando una subcategoria."}
              </small>
            </Field>
          </div>

          <Field label="Descripcion opcional">
            <textarea
              className="app-input min-h-24"
              maxLength={500}
              onChange={(event) => setDraft({ ...draft, description: event.target.value })}
              placeholder="Ej. Proteinas, creatinas, preentrenos y otros suplementos."
              value={draft.description ?? ""}
            />
          </Field>

          <div className="flex flex-wrap gap-2">
            <button className="app-button" disabled={saving || !draft.name.trim()} type="submit">
              {saving ? "Guardando..." : editingId ? "Guardar cambios" : draft.parentId ? "Crear subcategoria" : "Crear categoria"}
            </button>
            {editingId ? (
              <button className="app-button-secondary" disabled={saving} onClick={reset} type="button">Cancelar</button>
            ) : null}
          </div>
        </form>
      </section>

      <section className="rounded-2xl border border-[#d8ddd2] bg-white p-5 shadow-sm">
        <div className="mb-4">
          <p className="app-eyebrow">Estructura actual</p>
          <h2 className="app-card-title">Categorias del catalogo</h2>
          <p className="app-card-copy">Las subcategorias se muestran indentadas debajo de su categoria principal.</p>
        </div>

        <div className="grid gap-4">
          {rootCategories.length === 0 && orphanedCategories.length === 0 ? (
            <p className="rounded-xl border border-dashed border-[#cdd5ca] bg-[#fafbf9] px-4 py-6 text-center text-sm text-[#5d6b63]">
              Todavia no hay categorias.
            </p>
          ) : null}

          {rootCategories.map((category) => {
            const children = childrenByParent.get(category.id) ?? [];
            return (
              <article className="rounded-xl border border-[#d8ddd2] bg-[#fbfcfa] p-4" key={category.id}>
                <CategoryHeader category={category} kind="Categoria principal" saving={saving}
                  onArchive={archive} onEdit={edit} onToggle={toggle} />

                {children.length > 0 ? (
                  <div className="mt-4 grid gap-2 border-l-2 border-[var(--brand-accent)] pl-4">
                    {children.map((child) => (
                      <div className="rounded-lg border border-[#e1e5de] bg-white px-4 py-3" key={child.id}>
                        <CategoryHeader category={child} kind={`Subcategoria de ${category.name}`} saving={saving}
                          onArchive={archive} onEdit={edit} onToggle={toggle} compact />
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-3 text-xs text-[#7a847e]">Sin subcategorias.</p>
                )}
              </article>
            );
          })}

          {orphanedCategories.length > 0 ? (
            <section className="rounded-xl border border-[#e5cfaa] bg-[#fffaf1] p-4">
              <strong className="text-sm text-[#79531e]">Categorias con padre archivado</strong>
              <div className="mt-3 grid gap-2">
                {orphanedCategories.map((category) => (
                  <div className="rounded-lg border border-[#ead9bd] bg-white px-4 py-3" key={category.id}>
                    <CategoryHeader category={category} kind="Subcategoria sin categoria principal visible" saving={saving}
                      onArchive={archive} onEdit={edit} onToggle={toggle} compact />
                  </div>
                ))}
              </div>
            </section>
          ) : null}
        </div>
      </section>
    </section>
  );
}

function CategoryHeader({
  category,
  compact = false,
  kind,
  onArchive,
  onEdit,
  onToggle,
  saving,
}: {
  category: CatalogCategory;
  compact?: boolean;
  kind: string;
  onArchive: (category: CatalogCategory) => Promise<void>;
  onEdit: (category: CatalogCategory) => void;
  onToggle: (category: CatalogCategory) => Promise<void>;
  saving: boolean;
}) {
  return (
    <div className={`flex flex-col gap-3 ${compact ? "sm:flex-row sm:items-center sm:justify-between" : "md:flex-row md:items-start md:justify-between"}`}>
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <strong className={compact ? "text-sm" : "text-base"}>{category.name}</strong>
          <span className="rounded-full bg-[#eef2ec] px-2 py-1 text-[11px] font-semibold text-[#536158]">{kind}</span>
          <span className={category.status === "ACTIVE"
            ? "rounded-full bg-[#eaf6ed] px-2 py-1 text-[11px] font-semibold text-[#28733e]"
            : "rounded-full bg-[#fff3df] px-2 py-1 text-[11px] font-semibold text-[#8a5a22]"}>
            {category.status === "ACTIVE" ? "Activa" : "Inactiva"}
          </span>
        </div>
        {category.description ? <p className="mt-2 text-sm text-[#5d6b63]">{category.description}</p> : null}
      </div>

      <div className="flex flex-wrap gap-2">
        <button className="app-button-secondary" disabled={saving} onClick={() => onEdit(category)} type="button">Editar</button>
        <button className="app-button-secondary" disabled={saving} onClick={() => void onToggle(category)} type="button">
          {category.status === "ACTIVE" ? "Desactivar" : "Activar"}
        </button>
        <button className="rounded-md border border-[#e2b6b6] px-3 py-2 text-sm font-semibold text-[#9b2c2c]"
          disabled={saving} onClick={() => void onArchive(category)} type="button">Archivar</button>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-1.5 text-sm"><span className="font-semibold text-[#28332d]">{label}</span>{children}</label>;
}

function compareCategories(left: CatalogCategory, right: CatalogCategory) {
  return left.displayOrder - right.displayOrder || left.name.localeCompare(right.name, "es");
}
