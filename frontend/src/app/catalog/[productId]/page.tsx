"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import { getStoredSession, type StoredSession } from "@/lib/api";
import { getProductDetail } from "@/lib/api/catalog";
import type { ProductDetail } from "@/lib/api/catalog-types";

export default function CatalogProductDetailPage() {
  const params = useParams<{ productId: string }>();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const stored = getStoredSession();
    setSession(stored);
    if (!stored) { setLoading(false); return; }
    getProductDetail(stored.accessToken, params.productId)
      .then(setDetail)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el producto."))
      .finally(() => setLoading(false));
  }, [params.productId]);

  if (loading) return <LoadingScreen text="Cargando detalle del producto..." />;
  if (!session) return <AuthRequired message="Necesitas iniciar sesion para consultar el producto." title="Inicia sesion" />;
  if (!detail) return <AppShell eyebrow="Catalogo" session={session} title="Producto">{error ? <InlineError message={error} /> : null}</AppShell>;

  const { product, dependencies, history } = detail;
  return (
    <AppShell eyebrow="Catalogo" session={session} title={product.name}
      subtitle={`${product.sku} · ${product.categoryName ?? "Sin categoria"}`}>
      {error ? <InlineError message={error} /> : null}
      <div className="flex flex-wrap gap-3">
        <Link className="app-button-secondary" href="/catalog">Volver al catalogo</Link>
        {product.inventoryTracked ? <Link className="app-button" href="/inventory">Ver inventario</Link> : null}
      </div>
      <section className="grid gap-6 lg:grid-cols-2">
        <article className="app-card p-5">
          <p className="app-eyebrow">Informacion comercial</p>
          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
            <Detail label="Estado" value={product.archived ? "Archivado" : product.status} />
            <Detail label="Tipo" value={product.productType} />
            <Detail label="Codigo de barras" value={product.barcode ?? "Sin codigo"} />
            <Detail label="Marca" value={product.brand ?? "Sin marca"} />
            <Detail label="Presentacion" value={product.presentation ?? "Sin presentacion"} />
            <Detail label="Unidad" value={product.unitOfMeasure ?? "Sin unidad"} />
            <Detail label="Inventario" value={product.inventoryTracked ? "Controlado" : "No controlado"} />
            <Detail label="Etiquetas" value={product.tags.join(", ") || "Sin etiquetas"} />
          </dl>
        </article>
        <article className="app-card p-5">
          <p className="app-eyebrow">Dependencias</p>
          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
            <Detail label="Lineas vendidas" value={String(dependencies.saleItems)} />
            <Detail label="Movimientos" value={String(dependencies.inventoryMovements)} />
            <Detail label="Sucursales con stock" value={String(dependencies.branchesWithStock)} />
            <Detail label="Planes activos" value={String(dependencies.activeMembershipPlans)} />
          </dl>
        </article>
      </section>
      <section className="app-card overflow-hidden">
        <div className="border-b border-[#e3e7df] p-4"><p className="app-eyebrow">Historial</p></div>
        {history.length === 0 ? <p className="p-5 text-sm text-[#5d6b63]">No hay eventos registrados.</p> : (
          <div className="divide-y divide-[#e3e7df]">
            {history.map((entry) => (
              <article className="p-4" key={entry.id}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <strong>{entry.action}</strong>
                  <time className="text-xs text-[#6f7a73]">{new Date(entry.occurredAt).toLocaleString("es-MX")}</time>
                </div>
                {entry.reason ? <pre className="mt-2 overflow-x-auto whitespace-pre-wrap text-xs text-[#5d6b63]">{entry.reason}</pre> : null}
              </article>
            ))}
          </div>
        )}
      </section>
    </AppShell>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-[#6f7a73]">{label}</dt><dd className="font-medium">{value}</dd></div>;
}
