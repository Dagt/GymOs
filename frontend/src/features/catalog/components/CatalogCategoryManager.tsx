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
    if (!window.confirm(`¿Archivar ${category.name}?`)) return;
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
      setError(exception instanceof Error ? exception.message : "No fue posible administrar categorías.");
    } finally {
      setSaving(false);
    }
  }

  function reset() {
    setEditingId(null);
    setDraft({ ...emptyDraft });
  }

  return (
    <section className="grid items-start gap-4 xl:grid-cols-[minmax(280px,0.75fr)_minmax(0,1.25fr)]">
      <section className="app-card overflow-hidden xl:sticky xl:top-4">
        <div className="border-b border-[#e3e7df] px-4 py-3">
          <p className="app-eyebrow">Categorías</p>
          <h2 className="app-card-title">{editingId ? "Editar" : "Nueva categoría"}</h2>
        </div>
        <form className="grid gap-4 p-4" onSubmit={submit}>
          {error ? (
            <p className="rounded-lg border border-[#e2b6b6] bg-[#fff7f7] px-3 py-2 text-sm text-[#9b2c2c]" role="alert">
              {error}
            </p>
          ) : null}
          <Field label="Nombre">
            <input
              className="app-input"
              maxLength={120}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              placeholder="Suplementos"
              required
              value={draft.name}
            />
          </Field>
          <Field label="Ubicación">
            <select
              className="app-input bg-white"
              disabled={editingHasChildren}
              onChange={(event) => setDraft({ ...draft, parentId: event.target.value || null })}
              value={draft.parentId ?? ""}
            >
              <option value="">Categoría principal</option>
              {rootCategories
                .filter((category) => category.id !== editingId && category.status === "ACTIVE")
                .map((category) => (
                  <option key={category.id} value={category.id}>Dentro de {category.name}</option>
                ))}
            </select>
          </Field>
          <Field label="Descripción">
            <textarea
              className="app-input min-h-20"
              maxLength={500}
              onChange={(event) => setDraft({ ...draft, description: event.target.value })}
              placeholder="Opcional"
              value={draft.description ?? ""}
            />
          </Field>
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
            <button className="app-button h-10" disabled={saving || !draft.name.trim()} type="submit">
              {saving ? "Guardando..." : editingId ? "Guardar" : draft.parentId ? "Crear subcategoría" : "Crear categoría"}
            </button>
            {editingId ? (
              <button className="app-button-secondary h-10" disabled={saving} onClick={reset} type="button">
                Cancelar
              </button>
            ) : null}
          </div>
        </form>
      </section>

      <section className="app-card overflow-hidden">
        <div className="flex items-center justify-between border-b border-[#e3e7df] px-4 py-3">
          <h2 className="app-card-title">Estructura</h2>
          <span className="text-sm text-[#6f7a73]">{categories.length}</span>
        </div>
        <div className="max-h-[620px] overflow-y-auto p-4">
          <div className="grid gap-3">
            {rootCategories.length === 0 && orphanedCategories.length === 0 ? (
              <p className="rounded-lg border border-dashed border-[#cdd5ca] p-6 text-center text-sm text-[#5d6b63]">
                No hay categorías.
              </p>
            ) : null}

            {rootCategories.map((category) => {
              const children = childrenByParent.get(category.id) ?? [];
              return (
                <article className="rounded-xl border border-[#d8ddd2] bg-[#fbfcfa] p-3" key={category.id}>
                  <CategoryRow category={category} saving={saving} onArchive={archive} onEdit={edit} onToggle={toggle} />
                  {children.length > 0 ? (
                    <details className="mt-3 rounded-lg border border-[#e1e5de] bg-white">
                      <summary className="cursor-pointer px-3 py-2 text-sm font-semibold text-[#536158]">
                        {children.length} {children.length === 1 ? "subcategoría" : "subcategorías"}
                      </summary>
                      <div className="grid gap-2 border-t border-[#e1e5de] p-2">
                        {children.map((child) => (
                          <div className="border-l-2 border-[var(--brand-accent)] pl-3" key={child.id}>
                            <CategoryRow compact category={child} saving={saving} onArchive={archive} onEdit={edit} onToggle={toggle} />
                          </div>
                        ))}
                      </div>
                    </details>
                  ) : null}
                </article>
              );
            })}

            {orphanedCategories.length > 0 ? (
              <details className="rounded-xl border border-[#e5cfaa] bg-[#fffaf1] p-3">
                <summary className="cursor-pointer text-sm font-semibold text-[#79531e]">
                  {orphanedCategories.length} sin categoría principal visible
                </summary>
                <div className="mt-3 grid gap-2">
                  {orphanedCategories.map((category) => (
                    <CategoryRow compact category={category} key={category.id} saving={saving} onArchive={archive} onEdit={edit} onToggle={toggle} />
                  ))}
                </div>
              </details>
            ) : null}
          </div>
        </div>
      </section>
    </section>
  );
}

function CategoryRow({
  category,
  compact = false,
  onArchive,
  onEdit,
  onToggle,
  saving,
}: {
  category: CatalogCategory;
  compact?: boolean;
  onArchive: (category: CatalogCategory) => Promise<void>;
  onEdit: (category: CatalogCategory) => void;
  onToggle: (category: CatalogCategory) => Promise<void>;
  saving: boolean;
}) {
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <strong className={compact ? "text-sm" : "text-base"}>{category.name}</strong>
          <span className={category.status === "ACTIVE"
            ? "rounded-full bg-[#eaf6ed] px-2 py-1 text-[10px] font-semibold text-[#28733e]"
            : "rounded-full bg-[#fff3df] px-2 py-1 text-[10px] font-semibold text-[#8a5a22]"}>
            {category.status === "ACTIVE" ? "Activa" : "Inactiva"}
          </span>
        </div>
        {category.description ? <p className="mt-1 truncate text-xs text-[#6f7a73]">{category.description}</p> : null}
      </div>
      <div className="flex shrink-0 flex-wrap gap-1.5">
        <SmallAction disabled={saving} label="Editar" onClick={() => onEdit(category)} />
        <SmallAction disabled={saving} label={category.status === "ACTIVE" ? "Desactivar" : "Activar"} onClick={() => void onToggle(category)} />
        <SmallAction danger disabled={saving} label="Archivar" onClick={() => void onArchive(category)} />
      </div>
    </div>
  );
}

function SmallAction({
  danger = false,
  disabled,
  label,
  onClick,
}: {
  danger?: boolean;
  disabled: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={danger
        ? "rounded-md border border-[#e2b6b6] px-2 py-1 text-xs font-semibold text-[#9b2c2c]"
        : "rounded-md border border-[#cdd5ca] px-2 py-1 text-xs font-semibold text-[var(--brand-primary)]"}
      disabled={disabled}
      onClick={onClick}
      type="button"
    >
      {label}
    </button>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="grid gap-1.5 text-sm">
      <span className="font-semibold text-[#28332d]">{label}</span>
      {children}
    </label>
  );
}

function compareCategories(left: CatalogCategory, right: CatalogCategory) {
  return left.displayOrder - right.displayOrder || left.name.localeCompare(right.name, "es");
}
