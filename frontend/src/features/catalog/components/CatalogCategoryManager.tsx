"use client";

import { type FormEvent, useState } from "react";
import {
  archiveCatalogCategory,
  createCatalogCategory,
  updateCatalogCategory,
  type CatalogCategory,
} from "@/lib/api";

export function CatalogCategoryManager({
  accessToken,
  categories,
  onChanged,
}: {
  accessToken: string;
  categories: CatalogCategory[];
  onChanged: () => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function create(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) return;
    await run(async () => {
      await createCatalogCategory(accessToken, {
        name: name.trim(),
        status: "ACTIVE",
        displayOrder: categories.length,
      });
      setName("");
    });
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
    await run(() => archiveCatalogCategory(accessToken, category.id));
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

  return (
    <section className="app-card grid gap-4 p-5">
      <div>
        <p className="app-eyebrow">Clasificacion</p>
        <h2 className="app-card-title">Categorias</h2>
        <p className="app-card-copy">Organiza productos y servicios para agilizar filtros y venta.</p>
      </div>
      {error ? <p className="text-sm text-[#9b2c2c]" role="alert">{error}</p> : null}
      <form className="flex flex-col gap-2 sm:flex-row" onSubmit={create}>
        <input
          className="h-10 flex-1 rounded-md border border-[#cdd5ca] px-3"
          onChange={(event) => setName(event.target.value)}
          placeholder="Nueva categoria"
          value={name}
        />
        <button className="app-button" disabled={saving || !name.trim()} type="submit">Agregar</button>
      </form>
      <div className="flex flex-wrap gap-2">
        {categories.map((category) => (
          <span className="flex items-center gap-2 rounded-full border border-[#d8ddd2] px-3 py-2 text-sm" key={category.id}>
            {category.name}
            <button disabled={saving} onClick={() => toggle(category)} type="button">
              {category.status === "ACTIVE" ? "Desactivar" : "Activar"}
            </button>
            <button className="text-[#9b2c2c]" disabled={saving} onClick={() => archive(category)} type="button">
              Archivar
            </button>
          </span>
        ))}
      </div>
    </section>
  );
}
